# Plan: closing the gaps in the recovered requirements

As of 2026-10-02, with notes on later gaps added up to 2026-10-15. Gap numbers refer to [Known gaps](requirements.md#known-gaps) in `requirements.md`.

Of the 17 gaps, 12 can be closed with small, low-risk changes. Three need a decision before any code changes, and two (#6, #7) touch the core algorithms and each need a session of their own.


## Ground rules

* **Fix the code where the README states a behaviour users rely on; fix the README where the code's behaviour is the deliberate one.** Each item below says which side moves.
* **Characterize first.** Before changing behaviour, add a test that shows the current behaviour, and check that it fails for the reason the gap describes.
* **Never break existing repositories.** `.adapters` is written once, when a repository is first opened, and fixed after that. Changing adapter defaults therefore only affects new repositories; that is the intended way to roll out #1 and #5.
* **Do not delete our own modules** (adapters, extras, storage), even if they are unused. Mark them read-only or unsupported instead.
* **One commit per gap**, each updating `requirements.md` (its requirement and gap row) in the same commit.


## Phase 1 - quick fixes (about one day, low risk)

| Gap | Change | Verification |
| --- | --- | --- |
| #16 NUL in `AdapterPreferences.java` | Replace the raw NUL byte in `UNSET_MARKER` with the escape `"\u0000unset"`. The value is unchanged, so stored preferences stay valid. | `git diff` shows the file as text again; `file` reports Java source; existing preference tests pass. |
| #15 hard-coded LilyPond paths | Empty `lilypond_executable` and `lilypond_search_paths` in the bundled `lilypond-config.properties`. When nothing is set, look up `lilypond` on the `PATH`. Preferences (`LilypondPreferences`) still override. | Rendering works with `lilypond` on the `PATH` and with the path set in Preferences. A test checks that the bundled file has no absolute user path. |
| #12 unknown `[id]` in a configuration | Reject an unknown feature id, as the commented-out check intended: "Feature id does not exist. Use the feature name to create a new feature." Check that fork, pull and `parseConfigurationString` callers never rely on the temporary feature. | New test in `ConfigurationParser` tests; full suite green. |
| #11 non-lazy composition | Delete the unused non-lazy branch in `Repository` (it only throws), so only the lazy path remains. | Full suite green. |
| #14 GUI leftovers | Wire `ForkView` into the Collaborate ribbon group, since CLI and REST both offer fork. Delete `FeatureDetailView` and `PresenceConditionDetailView`, which nothing references. Finish or remove the TODO toggles in `ArtifactsView`. | Fork from the GUI, then reopen the fork and check it out (see the fork-reopen bug lesson). |
| #4 writers that write nothing | Make the `java` (lines) and `challenge` writers throw "checkout is not supported by this adapter - use java-ast" instead of silently writing nothing. Mark both as read-only in the README. | Test: checkout through either adapter fails with that message. |


## Phase 2 - align code and README (two to three days, low to medium risk)

### #2 CLI repository discovery - fix the code

The README is right: `git`-like discovery is what users expect.

1. In `cli/.../Main.java`, use `detectRepository()` for every command except `init` and `fork`, which must create a repository in the current directory.
2. Keep the current directory as the working directory, even when the repository is found in a parent directory, as the README says.
3. Add tests: run from a subdirectory; `init` inside a subdirectory of an existing repository still creates a new one; with no repository anywhere, the error is clear.

### #1 and #5 C/C++ defaults and adapter priority ties - fix both together

These two gaps have one cause: C and C++ were left disabled partly because they claim the same patterns at the same priority.

1. **Make ties deterministic.** When `.adapters` is first written and two enabled adapters claim the same glob at the same priority, order them by an explicit rule (for example plugin id) and log a warning naming both. Today the winner depends on hash-set order.
2. **Separate C and C++.** Give C++ a lower priority than C for `*.c`/`*.h` and keep it first for `*.cpp`/`*.hpp`. That is what the README already describes ("`*.c`, `*.h` after the C adapter").
3. **Enable C and C++ by default** (remove them from `DEFAULT_DISABLED_PLUGIN_IDS`). This only affects new repositories and users who never changed the setting. The C++ adapter's rewrite (d9ce686c) is line-exact, so the original reason for leaving it off no longer applies.
4. Leave `java`, `challenge`, `runtime` and `go` disabled. Their `.java` claims would tie with java-ast; step 1 makes that harmless.

Verify: on a fresh repository, `.adapters` maps `.c`/`.h` to C and `.cpp`/`.hpp` to C++, and a VEVOS example commits with proactive traces. On an existing repository, `.adapters` is unchanged, and so is its routing.

### #9 REST storage directory - make it configurable

Add a Micronaut property (for example `ecco.storage-dir`, environment variable `ECCO_STORAGE_DIR`). Keep the current Docker/Jenkins/`examples` heuristics as the fallback, and log the directory chosen at startup. Document it in `rest/README.md`.

### #3 `.hashes` - measured; the skip is deferred

**Result (2026-10-03).** 30 generated variants of ECCO's own `base` and `service` sources (122-130 files, 12 features each owning 8 files and adding lines to 10 shared files), timing `readFiles()` against the whole commit, first 3 commits left out as warm-up:

| Files read as | Reading | Whole commit | Reading's share |
| --- | --- | --- | --- |
| `.txt` (text adapter) | ~11 ms | ~240 ms | 4.5% |
| `.java` (Java AST adapter) | ~220 ms | ~325 ms | 69% |

TypeScript and Python were not measured; they parse in Node.js and Python, so they are likely closer to the Java figure.

**Done another way (2026-10-13).** A Git import extracts every commit into a fresh directory, which has no `.hashes`, so the skip would not help where reading costs most. Instead the dispatcher keeps the trees of the last read: a file whose adapter, path, content (SHA-256) and context are the same as then is not read again but copied (`Trees#copyRead`). An adapter opts in with `ArtifactReader#readContext`, which names everything else its trees depend on: nothing for TypeScript, Python, Java AST and Markdown; `.config` and `pcs.variant.csv` for C and C++; the repository's settings for LilyPond. Text and plain files are not reused - copying a tree costs as much as reading them. Artifact data is shared between copies, except data that adopts a newer commit's metadata (`adoptMetadataFrom`: file encodings, TypeScript semicolons, Java comments), which is copied. Checked: a copy equals a fresh read field by field for 2,991 files (flipperzero-firmware's C, C++, TypeScript, Python and Markdown, ECCO's Java, the LilyPond corpus); 15 flipperzero-firmware commits replayed with and without reuse store the same associations (every node, property, trace and order graph) and check out the same; 238 s became 205 s. `-Decco.reuseReadTrees=false` turns it off.

**Decision before (2026-10-03).** The README no longer promises the skip. Implementing it is worth it only for syntax-tree adapters, and it needs a design: an unchanged file's tree must still reach the commit, either rebuilt from the repository (the checkout that wrote `.hashes` composed it) or from a cache of parsed trees that is never shared with extraction. A test must show that the committed associations are identical with and without the skip. That is its own session, like #7 and #10.


## Phase 3 - decisions needed before work starts

| Gap | Decision | Options | Recommendation |
| --- | --- | --- | --- |
| #6 minimization not applied to checkout | Should checkout use minimized presence conditions? | (a) keep preview-only and document it; (b) apply behind an opt-in setting; (c) apply by default | (b) was chosen, then **stopped before implementation (2026-10-03)** - see below. |
| #8 security | How much security does ECCO need? | (a) document the limits (today); (b) harden the defaults: REST refuses to start with the default JWT secret unless a dev flag is set, users come from configuration, and sync refuses all-interfaces mode without an explicit acknowledgement; (c) real authentication for sync and REST roles | (b), a few days of work. (c) only if the REST server will run outside trusted networks. Auth was left out by choice on 2026-09-27; (b) keeps that choice but makes the risk impossible to miss. |
| #13 local fork opens its origin read-write | Should fork open its source read-only? | (a) add a read-only open mode to the storage layer; (b) leave it, since fork only reads | (a) if the storage layer supports it cheaply (it already has read-only transactions). Otherwise (b), with the TODO replaced by a test that fork does not modify the origin's files. |


### #6 findings that stopped the opt-in (2026-10-03)

Option (b) assumed that checkout picks associations by their condition, and that the equivalence test (`PresenceConditionMinimizerCheckoutEquivalenceTest`) covers it. Reading `Repository.Op#compose` and `CheckoutComposer` showed otherwise:

1. **The files come from node conditions, not from selection.** A checkout copies the main tree and prunes it node by node (`NodeRemovalVisitor`) using the conditions stored on its nodes when the main tree is built (`SerBoostedAssociationMerger`). The selected associations only feed the diagnostics: unresolved dependencies, SURPLUS warnings and the GUI's list. Using minimized conditions for selection alone would change no file.
2. **Persisted minimized conditions go stale.** `persistMinimizedConditions` stores them on the associations, and nothing clears them when a later commit changes an association. Checkout would then use a condition for an association that no longer exists in that form. The GUI's "Use Simplified Labels" can already show such a stale condition today.

Doing it properly means: (i) invalidate a stored minimized condition when its association's condition changes (record the condition it was computed from); (ii) let the main-tree build use valid minimized conditions as node conditions, behind the opt-in; (iii) extend the equivalence test from selection to the checked-out files. Since (ii) changes how the main tree is built, it is core work for its own session, like #7. Step (i) is small and also fixes the stale GUI labels; it can be done on its own first. **Step (i) done 2026-10-03**: each stored minimized condition carries a fingerprint (`MinimizationBasis`) of the association's condition, the distinct configurations and the accepted constraints, taken when the run starts; `EccoService#validMinimizedConditions` returns only those that still match, and the GUI reads them through it. **Steps (ii) and (iii) done 2026-10-03.** The GUI's stored minimized conditions turned out to be feature-level (one term per module, feature names), so they cannot replace revision-exact node conditions; checkout uses separately computed revision-exact ones (`CheckoutConditions`, minimized under `FeatureModelFormula.compileRevisionAware`) with their own fingerprint over revision-level configurations. With the opt-in (`checkout --minimized`, GUI preference, off by default) a checkout builds a one-off main tree with them. `MinimizedCheckoutEquivalenceTest` compares the written files for every model-consistent configuration, including an older revision, and fails if the conditions are made feature-level. On 30 generated variants the conditions were already minimal, so the opt-in gave identical files, no gain, and ~13 ms more per checkout; it can only pay off where accepted constraints really shorten conditions.

## Phase 4 - high-risk core work (separate sessions)

### #7 partial order graph alignment is factorial

**Characterized 2026-10-03; no change to the algorithm.**

1. **Benchmark.** 30 variants, each adding a unique line at the same place (and, in a second run, at two places): commits stay at 10-53 ms, with one peak of 122 ms just below the cap. The July fallback (`directPoaAlignment` above 100,000 orderings) is what keeps it there: with the exact algorithm alone, the same test runs out of memory.
2. **The fallback's known bug.** It can find fewer matches than the exact algorithm when a branch's order conflicts with the one it assumes (`DirectPoaAlignmentSpikeTest`); the code notes that this leaves an extra unmerged branch rather than corrupting anything. Checked where it would matter, in the files: committed variants with unique lines at one or two places or with random subsets of shared lines (75 checkouts), and, with the fallback confirmed to run, every ordered pair of 10 concurrent lines fixed by a later variant (90 cases, 990 checkouts). Every checkout matched what was committed.
3. **Replacing the exact algorithm** is therefore not needed now. If it ever is: when each artifact occurs at most once in each graph, the best alignment keeps the largest set of shared artifacts with no pair ordered one way in one graph and the other way in the other. Those pairs form a partial order (the intersection of one graph's order with the other's reverse order), so the largest such set is a maximum antichain of that order: polynomial by Dilworth's theorem (bipartite matching), with no enumeration of orderings. Repeated equal artifacts would need more than that.

`ConcurrentBranchScalingTest` pins the first two points: it fails (out of memory) without the fallback, and checks every checkout, including both orders of two concurrent lines.

**2026-10-15, a real history below the cap.** Replaying tinycc showed the cost the benchmark missed: a file gains about one open branch per commit, so below the cap a merge aligned 2^n orderings, each in full - 2,048 orderings of a 2,000-line file cost 40 s per commit, and 32,768 of `tccgen.c` (6,273 lines) did not finish. Measured with a temporary logging build; the cap counts orderings, not their cost. Now the LCS lengths of all orderings are computed in one walk that follows the enumeration's order and reuses its results where different orderings reach the same state with the same LCS row; only the first longest ordering is aligned, by the unchanged `alignPaths`, so the result is the same by construction (`IterativeLcsAlignmentEquivalenceTest` against the old loop; two deliberately wrong variants fail it). tinycc: 12 commits 417 s to 60 s with identical checkouts and warnings, the 13th 10 s (0be3bd63). Not done: aligning by unique lines first (anchors, as `git diff` does) would be faster still for large changed files, but picks other alignments among equally good ones - a decision, not a refactoring.

### #10 Java 21 language support in the Java (AST) adapter

**Done 2026-10-03.** JavaParser 3.27.0, language level JAVA_21; pattern-matching `switch` (guards, `case null`) and record patterns round-trip. The tree shape did not change, so `TREE_FORMAT` stays; reading all 771 Java files of this repository gave identical artifact identities (67,097 artifacts) under 3.25.8 and 3.27.0, so existing repositories are not split by the upgrade. Step 3's check found that comments inside lambdas and expressions were lost: they are now kept as metadata outside the artifact's identity (`JavaASTData#getTextWithComments`), so old repositories are not split and get them on the next commit.

1. Upgrade JavaParser (3.25.8 to a release that parses Java 21) in `libs.versions.toml`.
2. Run the java-ast fidelity tests (`JavaASTStatementFidelityTest`, `JavaASTCommentTest`, `JavaASTTreeFormatTest`). If the tree shape changes, bump `JavaASTData.TREE_FORMAT` so old repositories are refused for commit, as before.
3. Add tests for pattern-matching `switch`, record patterns, and comments inside lambdas and expressions (still unverified).


## Gaps noted 2026-10-13

Found while importing flipperzero-firmware from Git and replaying a 47-commit LilyPond repository. #47, #50 and #51 were fixed on 2026-10-14; #48 and #49 are deferred.

### #47 rollback on `Error` - done 2026-10-14

The commit now rolls back on any `Throwable` (2dc75b70). The other read-write operations - 16 of them in the services - catch only `Exception`. Same change in each: roll back in a `catch (Error e)` (or `finally` when the transaction is still open) and rethrow. A test per service is cheap with a listener or adapter that throws an `Error`; the CLI test for the commit (`CommitCommandTest`) shows the pattern.

### #48 associations rewritten every commit - deferred

Measured on flipperzero-firmware after the storage changes of 2026-10-13: writing the association files is about 14% of the time spent storing. They are all written again because their counters change with every commit. The tree of an association changes far less often than its counters. Option: store the counters in the association file and the tree in a file of its own (or in the packs, like artifacts), each written only when its bytes change. This changes the storage format, so it needs the same treatment as FR-P4: old files read the old way, converted on the next write, older builds refuse.

### #49 serializing unchanged artifacts - deferred

Measured 2026-10-14: of 629,000 artifacts checked in a commit, at most 23 had changed (partial order graphs of ordered parents), and the check took 1.6 of 5.6 s spent storing. Deferred by choice.

To skip an unchanged artifact, a commit serializes it and compares the digest with the stored one (since 2026-10-13 keyed by artifact id). That serialization is about half of the remaining time spent storing. Options: a dirty flag set where artifact data actually changes (risky: data adopts metadata in several places, `adoptMetadataFrom`), or a cheaper digest over the fields that are written. Characterize first: count how many touched artifacts change per commit on a Git import.

### #50 Java files the Java (AST) adapter cannot read - done 2026-10-14

7 of ECCO's own Java files fail to read. Since AD-11 they are committed by the text adapter with a warning, so nothing is lost, but they are traced by lines. Next step: collect the 7 errors and see whether they are JavaParser limits or ECCO's transformation.

### #51 plain-token LilyPond NPE - done 2026-10-14

4 corpus files throw a `NullPointerException` in `LilyEccoTransformer` when read without musical tokens - #40's failure, reproduced in that mode only. Like #50, AD-11 now commits them by the text adapter. Start from the vocal score #40 describes and the stack trace in plain mode.


## Gaps noted 2026-10-14 and 2026-10-15

Found by round-trip and equality tests of the adapters, by headless GUI view tests and by replaying tinycc and flipperzero-firmware. The fixed ones (#57-#62) are listed in `requirements.md`; open:

### #52 Java comments in parts of statements

Call arguments, try resources, catch and constructor parameters and case labels are stored as text printed without comments; module directives keep their comments on read but the writer does not attach them. Statements already keep their inner comments as metadata outside their identity (`JavaASTData#getTextWithComments`, #10); the same can be done for these parts, so old repositories are not split. The disabled cases in `JavaASTRoundTripTest` are the acceptance tests. Low risk, comments only.

### #53 mixed line separators

Keeping each line's own separator would need a separator per line artifact (part of its identity or metadata). Rare outside test files; leave documented unless a user needs it.

### #54 whole-file conflicts in a combined checkout

Decide what a checkout should do when combined variants hold different versions of a file stored whole: write neither and report a conflict warning, or write the newest. Either way the other files should be written. Needs a decision.

### #55 image adapter limits

Refusing a truncated JPEG is small (check the decoder's warnings). 16-bit channels would need another pixel format in the artifacts (a format change). The others are a JDK bug or invisible. Do the JPEG check when the adapter is next touched.

### #56 first C++ format, `case` blocks

Checkout-only data from before d9ce686c, and the old build loses the same code. Fixing it would change how old repositories check out; leave unless such a repository is in use.

### #63 leftover error dialog in GUI tests

Find which GUI test leaves an error dialog ("Service is not initialized") behind: run the GUI tests one class at a time and list open windows after each. If a view runs background work after its repository closed, guard it like `DependencyGraphView` (cbb09e62).


## Ongoing - #17 documentation

* Restart `CHANGELOG.md` with one entry summarizing the work since 0.1.9 (the 2026 fixes), then one line per change.
* Each gap commit updates `requirements.md`. Rebuild `requirements.pdf` when the markdown changes: `python3 doc/requirements-pdf/build.py` (needs pandoc and Chrome).


## Order and effort

| Order | Gaps | Effort | Risk |
| --- | --- | --- | --- |
| 1 | #16, #15, #12, #11, #4, #14 | done | - |
| 2 | #2, #9 | done | - |
| 3 | #1 + #5 | done | - |
| 4 | #3 measured; done another way 2026-10-13 (read-tree reuse) | done | - |
| 5 | #6, #8, #13 after decisions | done | - |
| 6 | #10 | done | - |
| 7 | #7 characterized 2026-10-03; exponential cost below the cap removed 2026-10-15 | done | - |
| 8 | #47 rollback on `Error` in the other services | done | - |
| 9 | #48, #49 storing cost | deferred | medium (storage format) |
| 10 | #50, #51 adapter read failures | done | - |
| 11 | #52 Java comments in parts of statements | under a day | low |
| 12 | #54 whole-file conflicts in a combined checkout | after a decision | low |
| 13 | #63 leftover dialog in GUI tests | under a day | low |
| later | #53, #55, #56 | when needed | low to medium |
| ongoing | #17 | with every commit | none |
