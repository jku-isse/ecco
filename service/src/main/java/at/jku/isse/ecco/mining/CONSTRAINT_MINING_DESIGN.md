# Feature: Automatic Constraint Suggestion for ECCO

Context handoff for continuing this work inside the IDE. Drop this file in the
repo (or fold it into `CLAUDE.md`) so the assistant has the full picture.

## Goal

Mine **candidate feature-model constraints** (requires / excludes / mandatory)
from the configurations already stored in an ECCO repository, and surface them as
ranked *suggestions* for a human to confirm. Confirmed constraints feed the
feature model, which in turn drives constraint-aware presence-condition
minimization and pruning — reducing module/order complexity that grows as more
variants are committed.

## Non-negotiable epistemic contract

Every suggestion is a **hypothesis induced from an incomplete sample**. "Never
observed together" is NOT "forbidden" — it may just be untested. Therefore:

- Output is **suggestions only**; never auto-apply to the feature model.
- A human accepts/rejects each; rejected pairs go to a "known-allowed" list so
  they are not re-proposed.
- Mined output must **not** silently feed the pruning/minimization path — a wrong
  inferred *exclude* would drop valid configurations.

## Architecture

1. **Mining core — `ConstraintMiner.java`** (done). Zero ECCO dependencies;
   operates on `List<Set<String>>` feature tokens. Fully unit-testable. Lives in
   `service` (or a new `mining` subproject registered in `settings.gradle`).
2. **Bridge — `ConfigurationBridge.java`** (done). The only ECCO-coupled code.
   Produces `List<Set<String>>` from committed variants (see confirmed API
   below).
3. **Review preferences — `ConstraintSuggestionPreferences.java`** (done).
   Persists accept/reject decisions per repository (`Preferences`-backed,
   mirrors `AdapterPreferences`), keyed by a suggestion's
   `signatureOf(kind, a, b)` so a decision survives re-mining and confidence
   tuning.
4. **Exposure — `SuggestConstraintsCommand`** (done, CLI) and
   `ConstraintSuggestionsView` (done, GUI — a panel inside the "Feature Model"
   tab's `FeaturesView`, split-paned next to the existing graph). REST
   endpoint still TO WRITE.
5. **Constraint-aware minimization — `FeatureModelFormula.java` +
   `PresenceConditionMinimizer.java` + `ModuleConditionBridge.java`** (done;
   exposed read-only via `MinimizePreviewCommand` CLI command — see
   "Minimization" below).

## Mining semantics (implemented in the core)

- **REQUIRES A -> B**: among variants containing A, the fraction also containing
  B. `confidence = co(A,B) / count(A)`. Hard rule at confidence 1.0; otherwise a
  near-miss carrying the violating variant indices. Skipped when A or B is
  mandatory (degenerate).
- **EXCLUDES A / B**: A and B both well-attested (`count >= minWitness`) but never
  (or almost never) co-occurring. Hard when `co(A,B) == 0`.
- **MANDATORY f**: present in every variant.
- **witness** = number of chances the rule had to be violated (ranking + trust
  signal). Ranking: hard rules first, then descending witness.
- **minWitness (t)**: don't propose below t witnesses — main defense against the
  incomplete-sample problem.
- **groupKey**: tokens sharing a group are alternatives; pairs are skipped. Use it
  so different revisions of the SAME feature aren't reported as mutually
  exclusive.

## Bridge (confirmed API, implemented in `ConfigurationBridge.java`)

The design doc originally guessed `FeatureInstance`/`getSign()`; the real
model is `FeatureRevision[]`, and there's no sign check needed because a
`Configuration` only ever holds *positively* selected revisions (everything
else is implicitly negative at the repository level — see
`Repository.Op.addConfigurationModules`).

```java
List<Set<String>> readConfigurations(EccoService service) {
    List<Set<String>> configs = new ArrayList<>();
    for (Commit commit : service.getCommits()) {          // EccoService.getCommits(), no repo hop needed
        Configuration cfg = commit.getConfiguration();
        Set<String> tokens = new HashSet<>();
        for (FeatureRevision fr : cfg.getFeatureRevisions())
            tokens.add(fr.getFeature().getName());        // FEATURE-LEVEL token
        configs.add(tokens);
    }
    return configs;
}
```

Design decisions baked in: **feature-level tokens** (collapse revisions to avoid
spurious mutual exclusion; keep revision + pass `groupKey` if revision-level
mining is wanted later) and **positive literals only**. Caller (the CLI
command) is responsible for `EccoService.open()`/`close()` around the read.

## Exposure (first cut: CLI, implemented)

`ecco suggest-constraints --min-witness 4 --confidence 0.9`
(`SuggestConstraintsCommand`, registered in `cli/.../Main.java`) → bridge →
`new ConstraintMiner(minWitness, confidence, null).mine(configs)` → prints
sorted suggestions; each near-miss already lists its violating variant
indices.

GUI (`ConstraintSuggestionsView`, done): lives inside the Feature Model tab
(`FeaturesView`, right side of a `SplitPane`, graph on the left). Toolbar has
min-witness/confidence controls + Refresh; a pending-suggestions `TableView`
with Accept/Reject; and Accepted/Rejected `ListView`s (with a "move back to
pending" undo) backed by `ConstraintSuggestionPreferences`. Important: there
is still no persisted `Constraint` type anywhere in `base`/`service` — "Accept"
here only records that a human reviewed the suggestion and agrees with it. It
does **not** write anything into a feature model, because no such enforced,
persisted concept exists yet. That remains a distinct, larger piece of future
work (a real `Constraint` entity + storage format), separate from what feeds
minimization below.

## Minimization (done — read-only preview, deliberately never persists)

Built on the `org.logicng` dependency already used elsewhere in `base`/`service`
(previously only for parsing, now also for solving/simplifying):

- **`FeatureModelFormula.compile(List<Suggestion>)`** — turns *accepted*
  suggestions into one LogicNG `Formula` (the feature model / "care set").
  `REQUIRES A,B` → `~A | B`, `EXCLUDES A,B` → `~A | ~B`, `MANDATORY f` → `f`.
  Only suggestions with `isHard() == true` are compiled — a near-miss has a
  real counterexample among already-committed variants, so compiling it as a
  hard clause would make the feature model inconsistent with real data. This
  is enforced inside the compiler itself (defense in depth on top of whatever
  accepted it upstream), not left to the caller.
- **`PresenceConditionMinimizer`** — a presence condition here is a DNF:
  `List<Term>` ORed together, each `Term` a set of positive/negative feature
  names ANDed together — mirroring `Module`/`Condition`'s real shape (pos/neg
  `Feature[]` conjunctions, ORed across modules). `minimize(featureModel,
  terms)` greedily drops a literal or a whole term only when a SAT check
  proves the result is equivalent to the original *everywhere the feature
  model holds* (outside that care set the two may disagree — those
  configurations aren't real, by construction of the feature model). One
  pass, not an exhaustive smallest-DNF search.
- **`ModuleConditionBridge.toTerms(Condition)`** — the ECCO-coupled bridge:
  `Module.getPos()/getNeg()` (`Feature[]`, feature-level — matches our
  granularity) → `Term`. The OR-across-modules assumption is grounded in
  `Condition.holds()`'s actual (unconditional) semantics, not
  `Condition.getType()` (which is only a display-string convention). Discards
  `ModuleRevision`'s revision-level detail, same scope limit as the rest of
  this feature.

**Crucial safety rule: always re-derive the feature model fresh, never trust
accept-time hardness.** `ConstraintSuggestionPreferences` only stores
kind/a/b, not confidence — a constraint accepted as hard could have since
picked up a counterexample from a later commit. So the pipeline always
re-mines from the *current* repository (`ConfigurationBridge` +
`ConstraintMiner`), filters that fresh result down to accepted signatures,
*then* compiles — never reconstructs a fake `Suggestion` from a cached
signature. A constraint that no longer re-mines as hard (or doesn't reproduce
at all, e.g. because minWitness/confidence changed) silently drops out here,
which is the safe direction to fail in.

**Exposure — CLI (`MinimizePreviewCommand`, done) and GUI (`AssociationsView`
+ `AssociationDetailView`, done):**
`ecco minimize-preview [id] --min-witness 4 --confidence 0.9` — prints every
association's original vs. minimized condition side by side (own `Term`
rendering on both sides, for a fair comparison), marked `(unchanged)` or
`(simplified)`.

GUI: a "Minimize Presence Conditions" button in the Associations tab's
toolbar. One click re-mines + compiles the feature-model formula once (same
`MINIMIZE_MIN_WITNESS`/`MINIMIZE_CONFIDENCE` = 4/0.9 defaults as the CLI, no
extra UI for them) and minimizes every association's condition in one
background `Task`, caching the results in `AssociationsView`'s
`minimizedByAssociationId` map (id → rendered text) — cheap to look up
per-selection afterward without re-mining. `AssociationDetailView` gained a
`minimizedPC` field (a `TextArea`, matching the `associationPC` field which
was itself switched from `TextField` → `TextArea`, both `setWrapText(true)`
+ `setPrefRowCount(8)`, so long conditions are readable/scrollable instead
of scrolling off a single-line field) and a public `setMinimizedCondition`
setter that the selection listener and the minimize button both call. The
cache is cleared when the repository closes, so a freshly-opened repository
never shows a stale minimized condition from whatever was open before.

**Read-only by design** (both CLI and GUI): never constructs, mutates, or
persists a real `Module`/`Condition`/`Constraint` — the stored condition
stays the sole source of truth for actual checkout, so a wrong accepted
constraint can only mislead a preview, never corrupt real data. This is a
deliberately narrower scope than "wire into commit/checkout" (still not
attempted — that remains the risky, not-yet-justified step).

`PresenceConditionMinimizer.format(List<Term>)` was factored out (previously
a private method duplicated inside `MinimizePreviewCommand`) specifically so
the GUI could reuse the exact same rendering as the CLI without copying it a
second time.

**Verified end-to-end** (`Core`/`BranchA`/`BranchB` toy repo, `Core` accepted
MANDATORY): `Core`'s condition `Core` minimized to `TRUE`; `BranchA`'s
`BranchA & Core & !BranchB  OR  BranchA  OR  BranchA & !BranchB  OR  BranchA &
Core` minimized to `BranchA` — the now-redundant `Core`/`!BranchB` literals
correctly dropped once `Core` is known-mandatory.

**Two real bugs found and fixed along the way** (both pre-existing, not new
in this feature, just never previously exercised by a real cross-process
run):
1. `ConstraintSuggestionPreferences` never called `Preferences.flush()` —
   writes were only guaranteed to reach the backing store asynchronously,
   fine within one long GUI session but a race for a short-lived process
   (e.g. the CLI) started right after a GUI accept/reject.
2. `repoScope()` used `toAbsolutePath().normalize()`, not `toRealPath()` — on
   a symlinked path segment (e.g. macOS's `/tmp` → `/private/tmp`), a
   relative `"."`-based path (CLI's `EccoService`) and an already-absolute
   path (GUI) resolve to *different strings* for the exact same physical
   repository, landing in different preferences buckets. This is why the
   first end-to-end run showed "0 accepted total" despite having just
   accepted one.

## Tests (pure core → easy, this is where correctness is pinned)

`ConstraintMinerTest` (7 tests):
- Clean `A→B` → one hard REQUIRES.
- Add one variant with A and not B → flips to near-miss with that exact index.
- Two well-attested features never co-occurring → hard EXCLUDES.
- Feature in every variant → MANDATORY and produces no REQUIRES.
- Two revisions with a `groupKey` → no EXCLUDES between them.
- `minWitness` above the count → rule suppressed.
- Empty input → no suggestions.

`FeatureModelFormulaTest` (5 tests): each `Kind` compiles to the right clause
(checked via SAT satisfiability probes, not string comparison); near-miss
suggestions are skipped.

`PresenceConditionMinimizerTest` (6 tests): redundant positive/negative
literal dropped, duplicate term dropped, mandatory feature collapses a
disjunction to a tautology, no-op when nothing is redundant, and a
general equivalence-under-feature-model property check (the strongest
regression guard — holds regardless of how the greedy algorithm evolves).
All of the above operate on synthetic `Term` data, not a real repository.

`PresenceConditionMinimizerRealRepoTest` (1 test): the one test that touches
a real `EccoService`/`Module`/`Condition`, exercising `ModuleConditionBridge`
end to end (the one piece the synthetic tests structurally can't reach).
Builds a real `Core`/`BranchA`/`BranchB` repo, accepts `MANDATORY Core`
through the actual `ConstraintSuggestionPreferences` (not a hand-built
`Suggestion`), runs the exact same pipeline `MinimizePreviewCommand`/
`AssociationsView` do, and asserts the minimized condition agrees with the
real `Condition.holds()` on every actually-committed configuration.
Deliberately stops short of checkout/artifact-tree comparison — minimized
conditions are never wired into checkout (see "Minimization" above), so
there's no artifact tree built from one to compare; agreement with `holds()`
on real committed configurations is the strongest claim testable without
that wiring existing.

`PresenceConditionMinimizerRealNachtwacheConfigTest` (1 test): same claim,
larger and more realistic input. 8 real configuration strings (27 features:
SATB choral voices, each with notes/dynamics/markup/lyrics sub-features)
supplied directly by the maintainer from an actual repository ("nachtwache"),
not invented for this test — a mostly-nested growing chain plus one genuine
sibling branch (a config that shares a common base with the main chain but
diverges into a different subset, not comparable either way). File content
is synthetic (one small file per feature; the real `.ly` source lives outside
this repository and isn't checked in), but the feature structure and overlap
pattern are real. Mines and accepts *every* hard suggestion (69 in this
sample) rather than hand-picking one, then checks all 12 associations × 8
configurations (96 pairs) agree with real `Condition.holds()`. This is what
actually exercises real `REQUIRES` chains through the pipeline, not just a
single `MANDATORY` feature.

## Open decisions

1. REST front-end, in addition to the CLI commands and GUI panels already done.
2. New `mining` subproject vs. placing it in `service` (currently in `service`).
3. A real persisted `Constraint` entity in `base`/`service` — "accept" in the
   GUI still doesn't write to an enforced feature model (see "Exposure"
   above). Bigger, separate architectural decision.
4. Wiring minimization into a REAL pipeline (commit/checkout, actually
   rewriting stored conditions) — deliberately still not attempted; the
   preview (CLI + GUI, both read-only) is a stepping stone, not this. Needs
   its own design/risk discussion given it would touch core correctness
   paths.
