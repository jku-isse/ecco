
# ECCO CHANGELOG


## Unreleased

  * **Java enum constants keep their order:** they were unordered children of the enum, like its members, so a constant one variant inserted before others was checked out where the associations put it - `BLUE, RED, GREEN` as `RED, GREEN, BLUE` - and `ordinal()`, `values()` and `compareTo` changed. The constants are now an ordered group of their own; members keep their grouping. An enum with a constant named `FIELDS` and a field can now be read by the Java adapter (it fell back to the text adapter). In an existing repository an enum's constants count as changed once, at its next commit; earlier commits check out as before. Older builds cannot read a repository with such an enum.
  * **Java switch cases, enum constant annotations and failed writes:** the Java (AST) adapter dropped the guard of a pattern case (`case Integer i when i > 0 ->` came back as `case Integer i ->`) and the `default` of `case null, default ->` - in switch statements, which then did something else; switch expressions were not affected. Annotations of enum constants were lost (`@Deprecated OLD` came back as `OLD`). A Java file that could not be written was reported on the console only, and the checkout went on without it; it now fails. Cases and constants without these are stored as before.
  * **Grayscale images keep their gray levels:** Java takes 8- and 16-bit gray to be linear, and the image adapter converted it to sRGB - a grayscale PNG was checked out visibly brighter (gray 40 as 110). Gray pixels are now stored as they are in the file. A grayscale image committed before changes in every pixel at its next commit, once.
  * **Adapter tests:** round trips and equality of the Java (AST) adapter's constructors, try blocks, bodies, switches, enums and modules; image formats, transparency and errors; text line endings, encodings and the text viewer - about 240 tests.
  * **Checkout into the repository's own folder through a symlink:** the folder may hold nothing but the repository, which was recognised by the exact path it was opened with. A repository opened through a symlink (on macOS `/var` is `/private/var`), or checked out into its folder by another path, failed with "Current base directory must be empty for checkout operation." The repository directory is now recognised as the same folder, whatever the path. `CheckoutIntoRepositoryHomeTest`.
  * **GUI fixes found by new view tests:** Checkout Selected (Artifacts) checked out even after Cancel in "delete the contents?" - into the repository's own folder. The remote detail pane showed the features of the remote named `origin` whichever remote was selected, and failed with a `NullPointerException` when there was none; its Save and Fetch buttons did nothing - Save now changes the remote's name, address and type (`EccoService#updateRemote`, keeping what was fetched), Fetch fetches it, both say how it went in the toolbar. Fetch said "Pull operation was successful"; a failed Add of a remote always said the name existed, also for an invalid address. Add Variant ended on the success header also when nothing was added: an empty configuration showed the error and then the success header, an invalid one threw on the UI thread. The variant detail pane left the variant's name empty. The dependency graph, drawn after the repository had been closed, threw on the drawing thread. The GUI's own tests now cover 79.5% of its lines (41.8% before).
  * **Pull, push and fork keep files read the same way:** pull and push merged LilyPond files whatever settings the two repositories read them with - with musical tokens and without, say, which match nothing of each other. They now refuse before merging and name the settings that differ; a repository with neither LilyPond settings nor LilyPond files takes the sender's over. A fork over the network takes its origin's settings, like a local one - and works at all: it always failed with a `NullPointerException`. The sync server answers two new requests for this; an older server is synced with as before.
  * **LilyPond without musical tokens: lyrics for a voice named without quotes:** `\lyricsto Alto { ... }` (where `\lyricsto "Alto"` worked) failed the reader with a `NullPointerException`, so the file was committed by the text adapter, by lines. All 705 corpus files are now read and written back byte-identical, with and without musical tokens.
  * **Java files that import a class twice are read as Java again:** the Java (AST) adapter failed on a repeated import ("An equivalent child is already contained"), so such a file was committed by the text adapter, by lines. A repeated import is read once - it means the same - and ECCO's own `TextFileFormat` no longer has one.
  * **An operation that runs out of memory no longer breaks the repository:** only a commit rolled its transaction back on an `Error` such as `OutOfMemoryError`. Any other operation - checkout, adding a variant, a constraint decision, a remote, a fork, the sync server's requests - left its transaction open, and since transactions nest, everything done afterwards became part of it: its changes were never stored, the write lock stayed held, and closing the repository failed with "Not all transactions have been ended". All of them roll back now and report the `Error` itself; a failed init or fork removes the new repository again.
  * **Commits write less and faster, also as a repository grows:** the core file listed every artifact and its pack - two million entries in a large repository, 26.5 MB written again with every commit, growing with every new artifact. It now lists only the packs, in the order they were written; loading reads them in that order, a later copy of an artifact superseding an earlier one. Checking which artifacts changed no longer builds a file path per artifact or asks the file system once per artifact. A flipperzero-firmware commit: 10.9 s to 8.3 s, the first one 23.5 s to 19.5 s; the core written per commit 26.5 MB to 49 KB. A repository written before is converted by its next write, once (all artifacts into one pack: 121 s for a 1 GB repository); older builds cannot open it afterwards. `PackOrderMigrationTest`.
  * **Each variant of a text file ends as it was committed:** a file's final newline was recorded once per file, the newest commit's, and written into every variant - flipperzero-firmware's README lost its final newline in one commit and with it in the checkout of every earlier one. The text, Markdown, C and C++ adapters now read a final newline as the last child of the file's node, traced like a line. A file stored before keeps its recorded final newline, so existing repositories check out as before. 15 flipperzero-firmware commits replayed into a new repository check out byte for byte; `FinalNewlineVariantsTest`. The reorder dialog leaves the final newline out and keeps it last.
  * **A file removed after it changed is no longer checked out where it is gone:** after a directory was renamed (in a Git history: `target_prod/` to `target_f1/`), every later variant was checked out with the old files, without content, and failed: "Expected exactly one child node (FileArtifactData) for plugin node". Copying a tree for the main tree made every node unique, also those that only lead to an association's own nodes, so the condition such a node had while it was unique (`BASE`, after the first commit) counted again. Copies now keep whether a node is unique, and the main tree takes conditions only from nodes that are. 15 flipperzero-firmware commits now all check out (12 before, the same files); `RemovedFileCheckoutTest`.
  * **Unchanged files are not parsed again:** every commit read and parsed every file, also the ones that had not changed - in a Git import, almost all of them. A file whose content and context are the same as in the previous commit of the session now gets a copy of the tree it was read into then (C, C++, TypeScript, Python, Java, Markdown, LilyPond; the context is what else a tree depends on, such as `.config` and `pcs.variant.csv` for C). 15 commits of flipperzero-firmware: 205 s instead of 238 s, a typical commit 10 s instead of 13 s, with the same associations and checkouts. `-Decco.reuseReadTrees=false` reads every file every time.
  * **Command line: the error of a failed commit is shown again:** a commit that ran out of memory left its transaction open, and closing the repository then failed too - `commit`, `checkout`, `order` and `minimize` reported only "Error closing connection: Not all transactions have been ended." in place of what went wrong. The failure to close is now attached to the command's own failure; a commit rolls its transaction back also on an error such as running out of memory; and running out of memory is reported as such, with the heap size and how to raise it (`JAVA_OPTS=-Xmx16g`).
  * **Documentation, images:** the README says what the image adapter costs - every pixel an artifact: one 4-megapixel PNG took 230 s, 9 GB of memory and 1 GB in the repository to commit, against 0.6 s and 11.6 MB with the file adapter - and gives the `.ecco/.adapters` lines that keep images whole, to add right after `init`.
  * **A file its adapter cannot read no longer fails the commit:** a `.json` with comments (`.vscode/settings.json`, which the Python adapter's JSON reader rejects), a Git LFS pointer named `.png` or `.jpg` (text, no image), a file an adapter does not understand - one such file made every commit of its folder fail, e.g. importing flipperzero-firmware from Git. Such a file is now read by the text adapter if it is text, else stored as a whole by the file adapter; both write it back byte for byte, and a warning names the file and the error. The adapter's other files are read by it as before. An adapter that cannot run here - no suitable Python, a LilyPond repository made with `parce` or `lybar-1` - still fails the commit (`AdapterUnavailableException`), as does Cancel: another adapter would store the files otherwise than their history.
  * **TypeScript declarations and empty bodies:** a function or method without a body (`.d.ts` files, `declare`, overload signatures), a variable without an initializer (`let x;`, `declare const x: T;`) and an empty block or enum (`function f() {}`, `enum E {}`) made the reader throw, and a commit containing such a file failed as a whole - every `.d.ts` file did. A class was closed with `\n}` whatever followed its last member, so an empty class came back with two braces. Of 28 TypeScript files in flipperzero-firmware 1 could be read, of 133 in other repositories 121; now all 161 come back byte for byte.
  * **LilyPond files are checked out as they were written:** the writer spaced every token by its rules, so a file came back reformatted - `#'(baseline-skip . 2)` as `#'( baseline-skip . 2 )`, `{gis4` as `{ gis4`, `c4 |` as `c4|`, `name= ` as `name = `, runs of spaces and tabs as one space, CRLF as LF, a tab before the first token dropped (0 of 724 corpus and example files came back byte for byte after a commit, 8 of the 19 examples). A token now also keeps the whitespace it was read with and the token it was read after; where it follows that token again it is written with that whitespace, elsewhere (a checkout combining commits) by the rules as before, and where a space is music it still decides. Neither counts for what makes two tokens the same, so tracing is unchanged, and a token matched in a later commit keeps the spacing of the commit that stored it. All 724 files now come back byte for byte, with musical tokens and without; a 47-commit LilyPond repository replayed into a new one checks out 47 of 47 as committed (0 before), with 0.3% more stored. Existing `lybar-2` repositories keep working: their tokens have no whitespace and are written by the rules.
  * **LilyPond checkouts that combine commits keep their music:** whether two tokens are written together came from the positions they were read at, and a token matched in a later commit keeps the position of the commit that stored it - so a checkout of a variant committed after another ran tokens together or split them (`\p e` as `\pe`, `e2 f4` as `e2f 4`, `g4` as `g 4`, other music each time). The 719-of-724 result below was measured after one commit only. A token now records whether it was read right after the one before it where a space would change the music (`c4`, not `c 4`), as part of what makes two tokens the same; elsewhere the writer's rules decide, as before. New repositories record `lilypond.parser=lybar-2`; one created with `lybar-1` (earlier today's builds) still checks out, with the position guess, but takes no more commits. With the current lymodel, `q` and drum notes in `\drummode`/`\drums` are musical tokens too and set the running duration.
  * **LilyPond markup, drums and chords read as such:** tree-sitter reads `\markup \bold { ... }` and `\drums { ... }` flat, so markup words were read as notes, slurs and variables - a variable makes `\relative` spell the next note from scratch - and drum notes as variables. Markup is now read by its words (`(etc.)` is one), the notes after `\drums`/`\drummode` are drums, and a chord's or tremolo's `:` keeps what follows (`c4:32`, `c:m7`; `m` no variable). `#:kw` is one word (it was written `#: kw`), `#\x` and `#(` are Scheme; an empty file and a brace tree-sitter supposes missing give no empty token, text it leaves out is read by words, positions count as Java counts them, and the parser's warnings are logged (a file read without musical tokens said nothing). Part of `lybar-2`.
  * **A first LilyPond commit into an older repository:** a repository with no LilyPond settings - created before them, or by ecco-rest without the LilyPond plugin - refused every LilyPond commit, though it held no LilyPond to disagree with. One with none of a plugin's settings and none of its files now takes the plugin's settings for a new repository at the next commit.
  * **Commit can be cancelled:** Cancel while committing stops the commit before it stores anything - between files, before extracting, before storing - and leaves the repository as it was (`EccoService#cancelCommit()`); a commit that is already storing completes. Commit Multiple Versions commits no further folder and says how many were committed. It also puts the working directory back afterwards instead of leaving it at the last folder.
  * **Constraint violations are found again:** Commit, Import from Git and the command line's `commit` checked the accepted constraints after committing, which can never find anything - the committed configuration is the exception to every rule it violates, and only rules without exceptions count. They now check the configuration before committing: Commit asks, like Commit Multiple Versions already did; Import from Git logs each violation (also for commits imported unattended, with their `.config`) and sums them up at the end; the command line prints them after the commit, as before. The modal dialogs from inside the commit are gone.
  * **Pull:** the feature tree shows the remote's features also when the remote was never fetched; it is fetched in the background, keeping what was unticked or typed.
  * **Variants:** a Remove Variant that fails no longer leaves the toolbar disabled.
  * **LilyPond files are written back with the music they were read with:** the writer ran numbers and signs together that were apart in the file (`\shp 0 4` as `\shp 04`, `\tempoChange 1 1.` as `11.`, `a2 2` as `a22`, `^ ^` as a marcato `^^`); tokens now keep together exactly what was written together and are never run into one word otherwise. A `\relative` without a pitch of its own was spelled from the note before it, an octave off, instead of from f. Of 724 corpus and example files, 71 came back with other music; now none do. Repositories read with parce, which still check out, gain too (411 to 565).
  * **LilyPond is read with lymodel's tree-sitter parse, parce is gone:** a new repository records `lilypond.parser=lybar-1` and its `.ly` files are read by the lilypond-idea-plugin's tree-sitter grammar (now with a lyric mode) and LilyPond 2.26's own word lists, through lymodel. The tokens keep parce's action names, so writing, highlighting and tracing work as before; a note, rest or chord end is one token including its scaling (`R1*3`), commands parce did not know (`\section`, `\fine`) are LilyPond's own, and numbers and braces parce skipped are tokens. Measured on 724 corpus and example files: written back after a commit, every one of the 719 lymusic reads holds the music it was read with (parce, before the writer fixes below: 411), committing 22% faster; a real 52-commit repository replays 52/52 with the same music, byte for byte. A repository from before (no `lilypond.parser` in `.ecco/.settings`) still checks out, but a commit is refused with an explanation: re-create it. Reading LilyPond needs Python with `tree-sitter`, `py4j` and lymodel, no longer `parce`; the GraalVM parser option is gone.
  * **LilyPond:** line breaks compare without their indentation, so re-indenting a variant no longer traces its line breaks to a feature (service/src/test/resources/lilypond/lilypond_respelled: 86 tokens traced to a feature where 24 belong to it, now 24).
  * **LilyPond, musical tokens in new repositories:** each note, rest and chord end is one token compared by its absolute pitch and duration, from lymodel (lilypond-idea-plugin's python/, found through `LYPYTHON` or installed), and lyric syllables stay tokens of their own. Implicit durations and another `\relative` anchor no longer count as content. The writer spells each note again against the note actually written before it, so a variant that leaps (`gis8 cis8 fis,8`) is not checked out an octave off when another variant spelled the same `fis` after a `gis`. A repository records the choice when it is created (`.ecco/.settings`, `lilypond.musicalTokens`); one created before has none and keeps plain tokens. `-Decco.lilypond.musicalTokens=false` creates plain repositories. Reading a musical repository without lymodel fails with how to install it, instead of falling back to plain tokens that would match nothing committed before. A local fork takes its origin's settings; a fork over the network gets the defaults.
  * **LilyPond, variable contexts in new repositories:** each top-level assignment (`SopranoVoice = ...`) is a context named after its variable, from lymodel's parse tree, around the parse's own contexts. parce gave every voice's notes the same context, so a version that moved a voice had it matched with another voice by order - ECCO stored `AltoVoice =` with soprano notes, and a feature committed before the move put its accents on other notes. Now the move is that voice taken out and put back (service/src/test/resources/lilypond/lilypond_edits/notes_moved: 73 tokens traced instead of 129), and only the moved voice's own accent is missing. Voices edited in place trace and compose as before; r4 (22 commits) and the 665 silverswan-music scores read and write the same. Recorded per repository (`lilypond.variableContexts`); one created before has none. `-Decco.lilypond.variableContexts=false` creates repositories without them. Needs lymodel, like musical tokens.
  * **LilyPond tests** check checkouts by their music with lymusic (lilypond-idea-plugin), skipped without lymodel; `service/src/test/resources/lilypond/lilypond_edits` adds notes inserted mid-bar, which must compose with a feature committed on the unedited notes and, with musical tokens, be traced to exactly the 10 tokens they change.
  * **Python and LilyPond adapters find their Python** instead of running `python` from the PATH: the one set in Preferences (Plugins for the Python adapter, Lilypond for LilyPond, with a lymodel directory), else the first of `python`, `python3`, MacPorts, Homebrew, python.org, `/usr/local/bin` and `/usr/bin` that imports the modules needed (libcst and py4j; tree-sitter, py4j and lymodel for LilyPond). A GUI started from the Finder, whose PATH has none of these, now reads `.py` and `.ly` files too. When none will do, the error lists each Python tried and what it lacks; one installed afterwards is found without restarting. A checkout of `.py` files without a usable Python now fails instead of silently writing nothing.
  * **Commit Multiple Versions:** a `.config` with one feature per line is shown on one line (`a.1, b.1`), also as the default commit message, instead of as ragged multi-line rows.
  * **Tests** that need Python modules are skipped exactly when the adapter could not run, asked the same way the adapter asks; six LilyPond regression tests that failed without lymodel are now skipped, and a music test no longer expects ties (committed with `slurs.1`) in checkouts without slurs.


## 0.2.0 (2026-10-04)

About 1,080 commits from 2020 to October 2026. [doc/requirements.md](doc/requirements.md) records what the system is expected to do and which gaps were closed.

### Upgrade notes

  * **Repositories from 0.1.9 cannot be opened.** Associations and artifacts are now stored in files of their own, and a repository in the old single-file format is refused with an explanation, without a migration. Check out its variants with the version that created it and commit them again.
  * **Older builds cannot read repositories written by this one.** Association counters are stored compactly, artifacts are kept in compressed pack files, and rejected constraint suggestions are stored in the repository (older builds ignore them). This build reads the earlier files of the new format and rewrites them as commits touch them.
  * **Some repositories are checkout-only**, because their adapter's tree format changed: commits and merges (pull, push, fork) into them are refused with an explanation, checkout still works. This applies to repositories written by the previous Java (AST) adapter, by the TypeScript adapter before switch/enum ordering was kept, and by the first C++ adapter.
  * **Java 21** is required to build and run (it was 13). The bundled Gradle wrapper is all else that is needed.
  * **Adapters on by default** in new repositories: Java (AST), C, C++, TypeScript, Python, LilyPond, Markdown, text, image and file. The line-based Java, challenge, runtime and Go adapters are off and can be enabled in Preferences. An existing repository keeps its routing (`.ecco/.adapters`).
  * **Refused that used to be accepted:**
    * a commit without any feature: give content that every variant has a feature of its own, e.g. `BASE`;
    * `[<id>]` naming an unknown feature;
    * a revision id prefix shorter than 7 characters;
    * opening or forking a directory without repository data;
    * a commit of a file mapped to a disabled or missing adapter (it used to fall back to another adapter);
    * a checkout of `.java` files with the line-based Java or challenge adapter, which cannot write them.
  * **Command line:**
    * The launcher is `ecco`.
    * Commands find the repository in the current directory or the nearest parent, and work on that directory; `init` and `fork` create a repository in the current directory.
    * Exit codes are 0 on success, 1 for a failed command and 2 for invalid arguments. Errors go to stderr, with the stack trace only under `-Decco.debug=true`.
    * `commit` prints the commit id, the configuration and any violated constraints; `checkout` prints how many warnings of each kind it wrote to `.warnings`.
    * The CLI and the REST server run without JavaFX; the file adapter's fat jar is gone.
  * **REST server:**
    * Outside development mode it refuses to start without `JWT_GENERATOR_SIGNATURE_SECRET` (at least 32 characters, not the published default) and `ECCO_REST_USERS_FILE` (PBKDF2 password hashes, printed by `ecco-rest --hash-password`). `ECCO_REST_DEV=true` keeps the demonstration setup for development.
    * The storage directory is set with `ECCO_STORAGE_DIR` (or `-Decco.storage-dir`); the old guess is only the fallback.
    * See [rest/README.md](rest/README.md).
  * **The sync server** (fetch, pull, push between machines) accepts only local connections unless started for all interfaces. It has no authentication, and the GUI asks for confirmation before accepting other machines.
  * **Removed:**
    * the Jersey-based `ecco-web` module (the REST server replaces it);
    * the Perst storage backend; the Jackson and Neo4j backends are no longer built (their sources remain under `extras/` and `storage/`);
    * the `experiment` module (moved to its own repository);
    * the GUI's Presence Table tab.

### New

  * **REST server** (Micronaut) for web clients such as [ecco-client](https://github.com/MatthiasPreuner/ecco-client.git):
    * repositories: list, create, clone, fork (deselecting features), delete;
    * commits as file uploads, variants, features, pull between repositories, checkouts as zip downloads;
    * JWT login, OpenAPI description and Swagger UI.
  * **Adapters:** Java (AST, up to Java 21, comments kept), C, C++, TypeScript, Python (including Jupyter notebooks and JSON), Markdown, LilyPond (with an SVG score viewer) and Go.
  * **Feature traces:** presence conditions given up front, e.g. from VEVOS `pcs.variant.csv` files, are stored with their associations and used at checkout. A trace that contradicts the commit history is not used and is reported as a TRACE warning.
  * **Checkout warnings** in `.warnings` and in the GUI, each with a suggested fix:
    * MISSING feature interactions, trimmed and ranked;
    * SURPLUS artifacts, absorbed and suppressed where the accepted feature model explains them;
    * ambiguous ORDER, UNRESOLVED associations, CONSTRAINT violations and rejected TRACEs.
  * **Recording an order:** an ambiguous order can be resolved without a commit, with `ecco order <file>...` after putting the checked-out file in order, or with Reorder... in the GUI's checkout details. A commit would record the whole checkout as a variant of its configuration.
  * **Feature constraints:**
    * `ecco suggest-constraints` mines requires, excludes and mandatory constraints from the committed configurations.
    * In the GUI's Feature Model view they can be accepted or rejected; both decisions are stored in the repository and travel with fork, pull and push.
    * Commits, checkouts and Git imports warn about configurations that violate accepted constraints.
  * **Presence-condition minimization** under the accepted constraints: `ecco minimize-preview` shows the simplified conditions, `ecco minimize` stores them, and `ecco checkout --minimized` (or the GUI preference) uses them while their inputs are unchanged. The files written are the same as without them.
  * **Import from Git** (GUI): imports a commit range of a local clone, commit by commit, with Import, Skip and Stop, optionally reviewing only every N-th commit. A language model at any OpenAI-compatible endpoint can suggest each commit's features. A commit that fails can be corrected and imported again.
  * **Command line:** `status`, `adapters`, `features`, `traces`, `get`/`set`, `remotes`, `fork`, `fetch`, `pull`, `push`, `dg`, `suggest-constraints`, `minimize-preview`, `minimize` and `order`. A GraalVM native image can be built.
  * **GUI:**
    * a ribbon with a single Preferences dialog, native app images for macOS, Linux and Windows;
    * fork, commit of several variant folders at once, recent repositories;
    * views of commits (with a comparison of two), associations with full and simplified conditions, the artifact tree of any selection of associations, the feature model with its constraint graph, and a knowledge graph;
    * association previews that show the artifacts in their files, coloured by association, for every adapter;
    * adapters can be enabled and disabled per user.
  * **Storage:** commits are atomic (a crash leaves the previous state or the new one), and only what changed is written.

### Performance

Measured when the changes were made:

  * **Commit:**
    * only changed artifacts are written: 15.7 s to 2.6 s for a 10 % edit of 21,855 artifacts;
    * compact association counters: association files from 147 MB to 13 MB, the 60th commit from 2.8 s to 1.5 s;
    * a 4,000-line text file: 17.7 s to under 1 s.
  * **Checkout:** cached condition formulas, 40 commits from 314 s to under 5 s.
  * **Opening** a 445 MB repository: about 65 s to 5.3 s.
  * **Pixel-granular images** are usable at around 1.7 megapixels: a 71,000-pixel image commits in 2 s instead of 67 s and checks out in 3.4 s instead of 157 s, with a repository of 17 MB instead of 576 MB.
  * **Ordering:** wide concurrent branches no longer exhaust memory when partial order graphs are aligned; a fallback keeps such commits at about 50 ms.
  * **Reference numbers** at 60 variants and 40 features: commit 1.8 s, checkout 1.1 s, status 0.2 s.

### Fixed

Notable among many:

  * **Content lost or changed:**
    * unreadable files were committed empty;
    * failed writes were reported as successful;
    * line-based files lost their charset, line endings, final newline or blank lines, and Markdown files a whitespace-only line after a list (with the empty lines before it);
    * JPEG and BMP images were checked out empty;
    * the C++ adapter reordered files and dropped comments, namespaces and `#if` directives;
    * the Java (AST) adapter dropped comments and `synchronized`, mangled arrow-switch cases, and crashed on about one file in nine of a real code base;
    * the TypeScript adapter dropped semicolons and reordered switch cases and enum members;
    * `A.2` could resolve to another revision of `A` whose id starts with 2, so the content went to the wrong revision.
  * **Reopening:** presence conditions, associations and order graphs could be corrupted after a repository was closed and reopened.
  * **Fork, pull and push:**
    * forks checked out empty or could not be reopened;
    * transfers could hang or arrive truncated;
    * `push <remote>` used the wrong remote;
    * a `host:port` with an IP address was taken for a path.
  * **Concurrency:** races between the GUI and background work crashed or lost counter updates, and Python-based adapters in parallel processes collided on their port.
  * **Constraints:** un-accepting failed for most feature names, and more than about 600 rejections could not be stored.
  * **GUI:**
    * New could delete any existing directory typed into its field (or the current directory, for an empty field) as "an existing repository";
    * "Delete contents?" before a checkout kept everything under a path containing ".ecco";
    * checking out several variants put them into one folder and failed;
    * after answering No, checkout went back to an empty form;
    * the commit comparison showed one-sided associations under the wrong commit.
  * **Remote sync:** a failed local pull or push reported a rollback error instead of its cause.
  * **REST:** concurrent requests to one repository interfered, and a malformed configuration gave 500 instead of 400.

### Security

  * **REST tokens:** the server issued unsigned tokens and accepted a forged unsigned token for any user, because the JWT secret was configured under a key Micronaut never read. Tokens are now signed, and the server does not start with the demonstration secret and users outside development mode.
  * **REST paths:** upload and repository names could escape the storage folder; they are refused.
  * **Sync server:** it deserializes only allow-listed classes, listens on the local interface by default, and the GUI confirms before accepting other machines.
  * **Git import:** commit trees can no longer write outside the extraction directory.


## 0.1.9
  * upgrade to Java 13
  * upgrade to Gradle 6
  * added GitHub workflows


## 0.1.8


## 0.1.7
  * major refactoring and cleanup
  * reimplementation of presence condition computation


## 0.1.6
  * removed obsolete and unused code
  * added ci/cd
  * improved build scripts
  * refactoring of project structure
  * ...


## 0.1.5
  * major refactorings
  	* separation of service and repository (types as well as projects)
  	* separations of interfaces into public and private
  * added fetch, fork, push, pull operations
  * added simple server functionality
  * (in progress) unit and integration tests
  * ...


## 0.1.4
  * removed ordered node
  * unified all children and ordered children in a single list
  * change unique children from list in parent to boolean in child
  * moved several concepts from node to artifact to remove redundancies
  * added release history (this file)
  * added dispatch for folders
  * made gui improvements
    * feature detail view: description/edit/search
    * commit/checkout result detail view
    * (in progress) artifact detail view
    * (in progress) dependency graph view
    * (in progress) association detail view: containment table, modules table
    * (in progress) artifact graph view: group children where count above limit separately by association and not in parent
    * ...
  * added option to use references in artifacts for equals
  * added requirement for artifact data to be serializable
  * added transactions to data layer
  * (in progress) added jpa data backend plugin
  * moved memory (base) implementation into its own data plugin
  * added order selector to composition


## 0.1.3
  * first running version

