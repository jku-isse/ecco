# Formal models

TLA+ models of protocols, checked with TLC (see Running it below); the same `tla2tools.jar` runs both.

## `SerStorage.tla`: the storage's commit protocol

A TLA+ model of how the `ser` storage commits and how other processes read meanwhile (`SerTransactionStrategy`), for NFR-R1a (a crash leaves the old state or the new one), NFR-R1c (a missing pack is damage) and readers in other processes (NFR-C1a). The module comment says what it models and what it leaves out.

| Step in the model | Code |
| --- | --- |
| `BeginWrite` | `beginReadWrite`: the write file locked, the current core loaded |
| `WritePack`, `WriteCore` | `endReadWrite`: `packs.write`, `associations.stage`, `pieces.write`, `StorageFiles.writeStored` of the core (temporary file, then an atomic rename) |
| `Swap` | the new id written into the id file under its exclusive lock - the commit point |
| `DeleteCore`, `EndCores` | `deleteUnusedCores`: every other core deleted if it can be locked exclusively, else kept, and then no pack is deleted |
| `DeletePack`, `EndGc` | `finishCommitted`: the packs the new core does not refer to |
| `ReadId`, `OpenCore`, `LockCore`, `Recheck`, `Load` | `loadDatabase`: id, core, shared lock, the check that the core still exists, everything read under the lock |
| `CrashWriter`, `CrashReader` | a process dying at any step: its locks go, its files stay |
| `Flush`, `PowerFailure` | without fsync (NFR-R2) the system writes files back in any order; a power failure keeps only what was written back |

### Configurations

| File | Checks | Expected |
| --- | --- | --- |
| `SerStorage.cfg` | Process crashes, two writers and two readers, three commits: `RepositoryComplete`, `ReadersReadComplete`, `WritersBuildOnComplete` | hold |
| `SerStoragePowerLoss.cfg` | The same with power failures, no fsync (as the code does) | `RepositoryComplete` fails: the id can reach the disk before the core it names |
| `SerStorageFsync.cfg` | Power failures, with the new core and its packs written back before the id; one reader (with two the states exceed 118 million) | hold |
| `SerStorageRetries.cfg` | A reader that reads the id only once | `ReadersNeverGiveUp` fails: a commit between reading the id and opening the core. The code reads it up to 20 times |


### Running it

TLC is in `tla2tools.jar` (no MacPorts or Homebrew formula needed; Java 11 or later):

```
curl -L -o tla2tools.jar https://github.com/tlaplus/tlaplus/releases/download/v1.7.4/tla2tools.jar
java -XX:+UseParallelGC -cp tla2tools.jar tlc2.TLC -workers auto -config SerStorage.cfg SerStorage.tla
```

and the same with each other `.cfg`. A failing invariant is reported with the shortest trace that breaks it.

### Results

Checked 2026-11-15 with TLC 2.19 (tla2tools v1.7.4), Writers = {w1, w2}, CoreIds = {c0, c1, c2, c3} (three commits), on the development Mac:

| File | Result | Distinct states | Time |
| --- | --- | --- | --- |
| `SerStorage.cfg` | All invariants hold | 18,801,688 (depth 46) | 52 s |
| `SerStoragePowerLoss.cfg` | `RepositoryComplete` violated, 7 steps: a write makes pack and core `c1` and swaps the id to `c1`; the system writes the id file back, not yet the core or the pack; the power fails - the id names a core that is not there | 400 when found | <1 s |
| `SerStorageFsync.cfg` | All invariants hold | 18,196,485 (depth 43) | 43 s |
| `SerStorageRetries.cfg` | `ReadersNeverGiveUp` violated: a reader reads the id, a write commits and deletes that core before the reader opens it | 462 when found | <1 s |

So the protocol keeps a repository whole against processes dying at any step and against readers in other processes, for every interleaving of two writers and two readers over three commits: no reader finds a pack missing, and no writer deletes one a core still refers to. Against a power failure it does not: NFR-R1a holds for crashed processes, not for a crashed machine, unless the core and its packs reach the disk before the id does. A reader gives up only if writes replace the core it is about to read 20 times in a row (`MaxAttempts`).

In the requirements: NFR-R1a says it holds after a crashed process, and gap #97 records the power failure. NFR-R1b said a pack an unfinished write left is deleted when the repository is next opened; in the model, as in the code, a reader never deletes anything - the next committed write that finds no reader holding an older core does, and NFR-R1b now says so.

## `SyncProtocol.tla`: pull and push by commit replay

A TLA+ model of pull and push between two clients and a sync server (`RemoteSyncService`, FR-D3b/c): what each repository holds, as a sequence of commit ids, and what the requests carry - contents are `SyncPropertyTest`'s. The server handles one request after the other, each in one transaction under its lock; a pull is one step (the client holds its own lock); a push is two requests - `COMMIT_IDS`, then `PUSH_COMMITS` with the client's commits the server lacked - between which other clients push, the server commits, and the client may give up waiting (a timeout, a lost answer, a crash) and push again while its first payload is still on its way. The merge (FR-D3e) and exclusions (FR-D4a) are not modelled.

| Property | What it says |
| --- | --- |
| `NoDuplicates` | No repository holds a commit twice |
| `CreationOrderKept` | Wherever a commit is, the commits made before it where it was made are too, and before it: sync carries a repository's history whole and in order |
| `AppendOnly` | Sync only appends: what a repository held stays where it was |

| File | Result | Distinct states | Time |
| --- | --- | --- | --- |
| `SyncProtocol.cfg` (two clients, four commits) | All hold | 75,082,083 (depth 51) | 4 min 11 s |
| `SyncProtocolNoSkip.cfg` (replay not skipping the commits the receiver has) | `NoDuplicates` violated, 9 steps: a client pushes `i1`, gives up waiting, asks for the server's ids again - which do not have `i1` yet - the first payload arrives, the second carries `i1` again | 17,033 when found | <1 s |

So the commit ids and replay's skipping what the receiver has (`CommitReplay#replayInto`) keep sync free of duplicates and in order however requests interleave, arrive late or are repeated; `DivergedSyncTest#commitsPushedTwiceAreTakenInOnce` checks one such interleaving by hand.

