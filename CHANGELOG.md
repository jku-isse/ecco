
# ECCO CHANGELOG


## Unreleased

  * **LilyPond:** line breaks compare without their indentation, so re-indenting a variant no longer traces its line breaks to a feature (examples/lilypond_respelled: 86 tokens traced to a feature where 24 belong to it, now 24).
  * **LilyPond, musical tokens in new repositories:** each note, rest and chord end is one token compared by its absolute pitch and duration, from lymodel (lilypond-idea-plugin's python/, found through `LYPYTHON` or installed), and lyric syllables stay tokens of their own. Implicit durations and another `\relative` anchor no longer count as content. The writer spells each note again against the note actually written before it, so a variant that leaps (`gis8 cis8 fis,8`) is not checked out an octave off when another variant spelled the same `fis` after a `gis`. A repository records the choice when it is created (`.ecco/.settings`, `lilypond.musicalTokens`); one created before has none and keeps plain tokens. `-Decco.lilypond.musicalTokens=false` creates plain repositories. Reading a musical repository without lymodel fails with how to install it, instead of falling back to plain tokens that would match nothing committed before. A local fork takes its origin's settings; a fork over the network gets the defaults.
  * **LilyPond tests** check checkouts by their music with lymusic (lilypond-idea-plugin), skipped without lymodel; `examples/lilypond_edits` adds notes inserted mid-bar, which must compose with a feature committed on the unedited notes and, with musical tokens, be traced to exactly the 10 tokens they change.
  * **Python and LilyPond adapters find their Python** instead of running `python` from the PATH: the one set in Preferences (Plugins for the Python adapter, Lilypond for LilyPond, with a lymodel directory), else the first of `python`, `python3`, MacPorts, Homebrew, python.org, `/usr/local/bin` and `/usr/bin` that imports the modules needed (libcst and py4j; parce and py4j, plus lymodel for musical tokens). A GUI started from the Finder, whose PATH has none of these, now reads `.py` and `.ly` files too. When none will do, the error lists each Python tried and what it lacks; one installed afterwards is found without restarting. A checkout of `.py` files without a usable Python now fails instead of silently writing nothing.
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

