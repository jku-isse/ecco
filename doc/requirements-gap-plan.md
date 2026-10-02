# Plan: closing the gaps in the recovered requirements

As of 2026-10-02. Gap numbers refer to [Known gaps](requirements.md#known-gaps) in `requirements.md`.

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
3. **Enable C and C++ by default** (remove them from `DEFAULT_DISABLED_PLUGIN_IDS`). This only affects new repositories and users who never changed the setting. The C++ adapter's rewrite (9639e362) is line-exact, so the original reason for leaving it off no longer applies.
4. Leave `java`, `challenge`, `runtime` and `go` disabled. Their `.java` claims would tie with java-ast; step 1 makes that harmless.

Verify: on a fresh repository, `.adapters` maps `.c`/`.h` to C and `.cpp`/`.hpp` to C++, and a VEVOS example commits with proactive traces. On an existing repository, `.adapters` is unchanged, and so is its routing.

### #9 REST storage directory - make it configurable

Add a Micronaut property (for example `ecco.storage-dir`, environment variable `ECCO_STORAGE_DIR`). Keep the current Docker/Jenkins/`examples` heuristics as the fallback, and log the directory chosen at startup. Document it in `rest/README.md`.

### #3 `.hashes` - measure, then decide

Skipping unchanged files is not a one-line fix. A commit must still contain the whole variant, so an unchanged file's tree has to come from somewhere other than re-reading it, and that is risky.

1. Measure how much of a commit's time goes to reading files, on the x8 repository and a 60-variant benchmark.
2. **If reading is a small share** (expected, since extraction and counters dominate per the profile after 2026-09-27): stop promising it. Remove the claim from the README, mark FR-M11 as dropped, and keep writing `.hashes` only if a future change will use it.
3. **If reading is a large share:** design the skip as its own change. Cache trees by file hash and reuse them on commit, with a test that the committed associations are identical with and without the cache.


## Phase 3 - decisions needed before work starts

| Gap | Decision | Options | Recommendation |
| --- | --- | --- | --- |
| #6 minimization not applied to checkout | Should checkout use minimized presence conditions? | (a) keep preview-only and document it; (b) apply behind an opt-in setting; (c) apply by default | (b). An equivalence test already shows selection would be identical. An opt-in setting lets real repositories confirm it before it becomes the default. |
| #8 security | How much security does ECCO need? | (a) document the limits (today); (b) harden the defaults: REST refuses to start with the default JWT secret unless a dev flag is set, users come from configuration, and sync refuses all-interfaces mode without an explicit acknowledgement; (c) real authentication for sync and REST roles | (b), a few days of work. (c) only if the REST server will run outside trusted networks. Auth was left out by choice on 2026-09-27; (b) keeps that choice but makes the risk impossible to miss. |
| #13 local fork opens its origin read-write | Should fork open its source read-only? | (a) add a read-only open mode to the storage layer; (b) leave it, since fork only reads | (a) if the storage layer supports it cheaply (it already has read-only transactions). Otherwise (b), with the TODO replaced by a test that fork does not modify the origin's files. |


## Phase 4 - high-risk core work (separate sessions)

### #7 partial order graph alignment is factorial

This has the biggest unbounded worst case in the system, and the most fragile code: POG fixes have repeatedly exposed other bugs.

1. Build a benchmark that grows concurrent unresolved branches until it slows down, and record the curve.
2. Fix the known correctness bug in the capped fallback (`directPoaAlignment`) first, with an isolated reproduction.
3. Only then consider replacing the exact alignment above a threshold, with a test that checks orderings against the exact algorithm below the threshold.

### #10 Java 21 language support in the Java (AST) adapter

1. Upgrade JavaParser (3.25.8 to a release that parses Java 21) in `libs.versions.toml`.
2. Run the java-ast fidelity tests (`JavaASTStatementFidelityTest`, `JavaASTCommentTest`, `JavaASTTreeFormatTest`). If the tree shape changes, bump `JavaASTData.TREE_FORMAT` so old repositories are refused for commit, as before.
3. Add tests for pattern-matching `switch`, record patterns, and comments inside lambdas and expressions (still unverified).


## Ongoing - #17 documentation

* Restart `CHANGELOG.md` with one entry summarizing the work since 0.1.9 (the 2026 fixes), then one line per change.
* Each gap commit updates `requirements.md`. Rebuild `requirements.pdf` when the markdown changes: `python3 doc/requirements-pdf/build.py` (needs pandoc and Chrome).


## Order and effort

| Order | Gaps | Effort | Risk |
| --- | --- | --- | --- |
| 1 | #16, #15, #12, #11, #4, #14 | ~1 day | low |
| 2 | #2, #9 | ~1 day | low |
| 3 | #1 + #5 | ~1 day | medium (adapter routing) |
| 4 | #3 (measure, then decide) | half a day, plus a design session if needed | low / high |
| 5 | #6, #8, #13 after decisions | 1-3 days each | medium |
| 6 | #10 | 1-2 days | medium |
| 7 | #7 | own session(s) | high |
| ongoing | #17 | with every commit | none |
