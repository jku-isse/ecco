----------------------------- MODULE SerStorage -----------------------------
(***************************************************************************)
(* The commit protocol of the ser storage (SerTransactionStrategy) and its *)
(* readers, for NFR-R1a (a crash leaves the old state or the new one) and  *)
(* the readers of other processes (NFR-C1a): writers and readers in        *)
(* processes of their own, sharing only the repository's files and the     *)
(* operating system's file locks.                                          *)
(*                                                                         *)
(* A write (endReadWrite) writes a new pack and a new core naming the      *)
(* packs it refers to, then - holding the id file exclusively - writes the *)
(* new core's id into the id file (the commit point) and deletes every     *)
(* other core it can lock exclusively (deleteUnusedCores). Only if it      *)
(* could delete all of them does it delete, after releasing the id file,   *)
(* the packs the new core does not refer to (finishCommitted). A reader    *)
(* (loadDatabase) reads the id, opens that core, locks it shared, checks   *)
(* it still exists, and reads it and its packs before releasing the lock;  *)
(* if the core is gone it reads the id again, up to MaxAttempts times.     *)
(*                                                                         *)
(* A pack or core appears complete or not at all: both are written to a    *)
(* temporary file and renamed into place (StorageFiles.writeStored). Pack  *)
(* and core files are named by the id of the write that made them; a pack  *)
(* here stands for the artifact, association and piece packs of a write.   *)
(*                                                                         *)
(* PowerLoss adds what survives a power failure: the operating system     *)
(* writes each file's latest state back on its own schedule, in any order  *)
(* (no fsync, NFR-R2), and a power failure reverts the disk to what was    *)
(* written back. Fsync models the alternative: before the id is written,   *)
(* the new core and its packs are written back, and the id right after.   *)
(* Neither models a file's content reaching the disk after its name.       *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    Writers,      \* processes that commit
    Readers,      \* processes that open the repository
    CoreIds,      \* ids of cores and their packs; one per write, never reused (UUIDs)
    Initial,      \* the core the repository starts with
    PowerLoss,    \* whether the power can fail
    Fsync,        \* whether a write forces its files to the disk before the id
    MaxAttempts   \* how often a reader reads the id before it gives up (20 in the code)

ASSUME Initial \in CoreIds /\ PowerLoss \in BOOLEAN /\ Fsync \in BOOLEAN /\ MaxAttempts \in Nat \ {0}

NoOne == "none"

VARIABLES
    \* the files as the processes see them
    id,         \* the id file: the current core
    core,       \* core -> whether its file exists
    pack,       \* pack -> whether its file exists
    refs,       \* core -> the packs it refers to (its content, once written)
    \* the files as a power failure leaves them (PowerLoss only)
    did, dcore, dpack,
    used,       \* the ids taken by writes so far
    \* the operating system's locks
    writeLock,  \* the writer holding the write file, or NoOne
    idLock,     \* the writer holding the id file exclusively, or NoOne
    coreLock,   \* core -> the readers holding it shared
    \* writers
    wpc,        \* where each writer is
    mem,        \* the packs of the core it loaded
    newId,      \* the id of its write
    toCheck,    \* the cores deleteUnusedCores has yet to try
    released,   \* whether it could delete every other core so far
    \* readers
    rpc, rid, attempts

disk     == <<id, core, pack, refs>>
durable  == <<did, dcore, dpack>>
locks    == <<writeLock, idLock, coreLock>>
wvars    == <<wpc, mem, newId, toCheck, released>>
rvars    == <<rpc, rid, attempts>>
vars     == <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
              wpc, mem, newId, toCheck, released, rpc, rid, attempts>>

Packs == {p \in CoreIds : pack[p]}

Symmetry == Permutations(Writers) \cup Permutations(Readers)

TypeOK ==
    /\ id \in CoreIds /\ did \in CoreIds
    /\ core \in [CoreIds -> BOOLEAN] /\ dcore \in [CoreIds -> BOOLEAN]
    /\ pack \in [CoreIds -> BOOLEAN] /\ dpack \in [CoreIds -> BOOLEAN]
    /\ refs \in [CoreIds -> SUBSET CoreIds]
    /\ used \subseteq CoreIds
    /\ writeLock \in Writers \cup {NoOne}
    /\ idLock \in Writers \cup {NoOne}
    /\ coreLock \in [CoreIds -> SUBSET Readers]
    /\ wpc \in [Writers -> {"idle", "pack", "core", "swap", "cores", "gc", "done"}]
    /\ rpc \in [Readers -> {"idle", "open", "lock", "recheck", "load", "retry", "failed"}]

Init ==
    /\ id = Initial
    /\ core = [c \in CoreIds |-> c = Initial]
    /\ pack = [c \in CoreIds |-> c = Initial]
    /\ refs = [c \in CoreIds |-> IF c = Initial THEN {Initial} ELSE {}]
    /\ did = Initial
    /\ dcore = [c \in CoreIds |-> c = Initial]
    /\ dpack = [c \in CoreIds |-> c = Initial]
    /\ used = {Initial}
    /\ writeLock = NoOne
    /\ idLock = NoOne
    /\ coreLock = [c \in CoreIds |-> {}]
    /\ wpc = [w \in Writers |-> "idle"]
    /\ mem = [w \in Writers |-> {}]
    /\ newId = [w \in Writers |-> Initial]
    /\ toCheck = [w \in Writers |-> {}]
    /\ released = [w \in Writers |-> TRUE]
    /\ rpc = [r \in Readers |-> "idle"]
    /\ rid = [r \in Readers |-> Initial]
    /\ attempts = [r \in Readers |-> 0]

-----------------------------------------------------------------------------
(* Writers *)

\* beginReadWrite: the write file locked exclusively, the current core and its packs loaded
\* (under the core's shared lock, which no reader's shared lock blocks and no deletion can take
\* from a writer holding the write file)
BeginWrite(w) ==
    /\ wpc[w] = "idle"
    /\ writeLock = NoOne
    /\ idLock = NoOne
    /\ CoreIds \ used # {}
    /\ writeLock' = w
    /\ mem' = [mem EXCEPT ![w] = refs[id]]
    /\ wpc' = [wpc EXCEPT ![w] = "pack"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, idLock, coreLock,
                   newId, toCheck, released, rpc, rid, attempts>>

\* packs.write, associations.stage, pieces.write: the write's packs, named by its new id
WritePack(w) ==
    /\ wpc[w] = "pack"
    /\ LET n == CHOOSE c \in CoreIds \ used : TRUE IN
         /\ newId' = [newId EXCEPT ![w] = n]
         /\ used' = used \cup {n}
         /\ pack' = [pack EXCEPT ![n] = TRUE]
    /\ wpc' = [wpc EXCEPT ![w] = "core"]
    /\ UNCHANGED <<id, core, refs, did, dcore, dpack, writeLock, idLock, coreLock,
                   mem, toCheck, released, rpc, rid, attempts>>

\* the new core: the write's pack and those of the loaded core it still refers to (others are
\* superseded: rewritten into the new pack, or merged)
WriteCore(w) ==
    /\ wpc[w] = "core"
    /\ \E keep \in SUBSET mem[w] :
         refs' = [refs EXCEPT ![newId[w]] = keep \cup {newId[w]}]
    /\ core' = [core EXCEPT ![newId[w]] = TRUE]
    /\ wpc' = [wpc EXCEPT ![w] = "swap"]
    /\ UNCHANGED <<id, pack, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   mem, newId, toCheck, released, rpc, rid, attempts>>

\* the id file locked exclusively and the new id written into it: the commit point. With Fsync,
\* the new core and its packs are on the disk before, and the id right after.
Swap(w) ==
    /\ wpc[w] = "swap"
    /\ idLock = NoOne
    /\ idLock' = w
    /\ id' = newId[w]
    /\ toCheck' = [toCheck EXCEPT ![w] = {c \in CoreIds : core[c] /\ c # newId[w]}]
    /\ released' = [released EXCEPT ![w] = TRUE]
    /\ wpc' = [wpc EXCEPT ![w] = "cores"]
    /\ IF Fsync
         THEN /\ did' = newId[w]
              /\ dcore' = [dcore EXCEPT ![newId[w]] = TRUE]
              /\ dpack' = [p \in CoreIds |-> dpack[p] \/ (p \in refs[newId[w]] /\ pack[p])]
         ELSE UNCHANGED <<did, dcore, dpack>>
    /\ UNCHANGED <<core, pack, refs, used, writeLock, coreLock, mem, newId, rpc, rid, attempts>>

\* deleteUnusedCores, one core at a time: deleted if it can be locked exclusively - no reader
\* holds it - else kept, and then no pack is deleted by this write
DeleteCore(w) ==
    /\ wpc[w] = "cores"
    /\ \E c \in toCheck[w] :
         /\ toCheck' = [toCheck EXCEPT ![w] = @ \ {c}]
         /\ IF core[c] /\ coreLock[c] = {}
              THEN /\ core' = [core EXCEPT ![c] = FALSE]
                   /\ UNCHANGED released
              ELSE /\ released' = [released EXCEPT ![w] = IF core[c] THEN FALSE ELSE @]
                   /\ UNCHANGED core
    /\ UNCHANGED <<id, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, rpc, rid, attempts>>

\* the id file released
EndCores(w) ==
    /\ wpc[w] = "cores"
    /\ toCheck[w] = {}
    /\ idLock' = NoOne
    /\ wpc' = [wpc EXCEPT ![w] = IF released[w] THEN "gc" ELSE "done"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, coreLock,
                   mem, newId, toCheck, released, rpc, rid, attempts>>

\* finishCommitted (deleteSupersededPacks, associations.committed, pieces.deleteUnreferenced):
\* the packs the new core does not refer to - superseded ones and those of crashed writes
DeletePack(w) ==
    /\ wpc[w] = "gc"
    /\ \E p \in CoreIds :
         /\ pack[p]
         /\ p \notin refs[newId[w]]
         /\ pack' = [pack EXCEPT ![p] = FALSE]
    /\ UNCHANGED <<id, core, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, toCheck, released, rpc, rid, attempts>>

EndGc(w) ==
    /\ wpc[w] = "gc"
    /\ \A p \in CoreIds : pack[p] => p \in refs[newId[w]]
    /\ wpc' = [wpc EXCEPT ![w] = "done"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   mem, newId, toCheck, released, rpc, rid, attempts>>

\* the write file released
EndWrite(w) ==
    /\ wpc[w] = "done"
    /\ writeLock' = NoOne
    /\ wpc' = [wpc EXCEPT ![w] = "idle"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, idLock, coreLock,
                   mem, newId, toCheck, released, rpc, rid, attempts>>

\* the process dies: the operating system releases its locks; its files stay as they are
CrashWriter(w) ==
    /\ wpc[w] # "idle"
    /\ wpc' = [wpc EXCEPT ![w] = "idle"]
    /\ writeLock' = IF writeLock = w THEN NoOne ELSE writeLock
    /\ idLock' = IF idLock = w THEN NoOne ELSE idLock
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, coreLock,
                   mem, newId, toCheck, released, rpc, rid, attempts>>

-----------------------------------------------------------------------------
(* Readers *)

RetryOrFail(r) == IF attempts[r] >= MaxAttempts THEN "failed" ELSE "retry"

\* readCurrentId: the id file read under its shared lock
ReadId(r) ==
    /\ rpc[r] \in {"idle", "retry"}
    /\ idLock = NoOne
    /\ rid' = [rid EXCEPT ![r] = id]
    /\ attempts' = [attempts EXCEPT ![r] = IF rpc[r] = "idle" THEN 1 ELSE @ + 1]
    /\ rpc' = [rpc EXCEPT ![r] = "open"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, toCheck, released>>

\* Files.exists and FileChannel.open of the core the id names
OpenCore(r) ==
    /\ rpc[r] = "open"
    /\ rpc' = [rpc EXCEPT ![r] = IF core[rid[r]] THEN "lock" ELSE RetryOrFail(r)]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

\* sharedLock: granted also on a file deleted meanwhile (the open file stays)
LockCore(r) ==
    /\ rpc[r] = "lock"
    /\ coreLock' = [coreLock EXCEPT ![rid[r]] = @ \cup {r}]
    /\ rpc' = [rpc EXCEPT ![r] = "recheck"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

\* "a writer may have replaced and deleted this core between reading the id and locking it"
Recheck(r) ==
    /\ rpc[r] = "recheck"
    /\ IF core[rid[r]]
         THEN /\ rpc' = [rpc EXCEPT ![r] = "load"]
              /\ UNCHANGED coreLock
         ELSE /\ rpc' = [rpc EXCEPT ![r] = RetryOrFail(r)]
              /\ coreLock' = [coreLock EXCEPT ![rid[r]] = @ \ {r}]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

\* readCore and loadRest under the shared lock, which is released after them
Load(r) ==
    /\ rpc[r] = "load"
    /\ coreLock' = [coreLock EXCEPT ![rid[r]] = @ \ {r}]
    /\ rpc' = [rpc EXCEPT ![r] = "idle"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

\* "DB file does not exist": the user opens it again
Restart(r) ==
    /\ rpc[r] = "failed"
    /\ rpc' = [rpc EXCEPT ![r] = "idle"]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

CrashReader(r) ==
    /\ rpc[r] # "idle"
    /\ rpc' = [rpc EXCEPT ![r] = "idle"]
    /\ coreLock' = [c \in CoreIds |-> coreLock[c] \ {r}]
    /\ UNCHANGED <<id, core, pack, refs, did, dcore, dpack, used, writeLock, idLock,
                   wpc, mem, newId, toCheck, released, rid, attempts>>

-----------------------------------------------------------------------------
(* The operating system and the power *)

\* one file's latest state written back - files in any order, as without fsync
Flush ==
    /\ PowerLoss
    /\ \/ /\ did # id
          /\ did' = id
          /\ UNCHANGED <<dcore, dpack>>
       \/ \E c \in CoreIds :
            /\ dcore[c] # core[c]
            /\ dcore' = [dcore EXCEPT ![c] = core[c]]
            /\ UNCHANGED <<did, dpack>>
       \/ \E p \in CoreIds :
            /\ dpack[p] # pack[p]
            /\ dpack' = [dpack EXCEPT ![p] = pack[p]]
            /\ UNCHANGED <<did, dcore>>
    /\ UNCHANGED <<id, core, pack, refs, used, writeLock, idLock, coreLock,
                   wpc, mem, newId, toCheck, released, rpc, rid, attempts>>

\* every process stops, the files are what was written back
PowerFailure ==
    /\ PowerLoss
    /\ id' = did
    /\ core' = dcore
    /\ pack' = dpack
    /\ writeLock' = NoOne
    /\ idLock' = NoOne
    /\ coreLock' = [c \in CoreIds |-> {}]
    /\ wpc' = [w \in Writers |-> "idle"]
    /\ rpc' = [r \in Readers |-> "idle"]
    /\ UNCHANGED <<refs, did, dcore, dpack, used, mem, newId, toCheck, released, rid, attempts>>

-----------------------------------------------------------------------------

Next ==
    \/ \E w \in Writers :
         \/ BeginWrite(w) \/ WritePack(w) \/ WriteCore(w) \/ Swap(w) \/ DeleteCore(w)
         \/ EndCores(w) \/ DeletePack(w) \/ EndGc(w) \/ EndWrite(w) \/ CrashWriter(w)
    \/ \E r \in Readers :
         \/ ReadId(r) \/ OpenCore(r) \/ LockCore(r) \/ Recheck(r) \/ Load(r)
         \/ Restart(r) \/ CrashReader(r)
    \/ Flush
    \/ PowerFailure

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
(* Properties *)

\* NFR-R1a, NFR-R1c: whoever opens the repository now finds the core the id names and every
\* pack it refers to - after a crash at any step too
RepositoryComplete == core[id] /\ refs[id] \subseteq Packs

\* the bug fixed in loadDatabase ("the pack ... is missing" for 37 of 214 opens while another
\* process committed): a reader holding its core finds every pack the core refers to
ReadersReadComplete ==
    \A r \in Readers : rpc[r] = "load" => core[rid[r]] /\ refs[rid[r]] \subseteq Packs

\* a writer builds its core on packs that exist until it commits
WritersBuildOnComplete ==
    \A w \in Writers : wpc[w] \in {"core", "swap"} => mem[w] \subseteq Packs

\* a reader never gives up because writers keep replacing the core it is about to read
ReadersNeverGiveUp == \A r \in Readers : rpc[r] # "failed"

=============================================================================
