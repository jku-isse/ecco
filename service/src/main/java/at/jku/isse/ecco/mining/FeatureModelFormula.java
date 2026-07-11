package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Compiles accepted {@link ConstraintMiner.Suggestion}s into a single LogicNG {@link Formula}: the
 * "feature model" constraint that {@link PresenceConditionMinimizer} treats as its care set.
 *
 * <p>Only HARD suggestions ({@link ConstraintMiner.Suggestion#isHard()}, i.e. confidence 1.0, no
 * observed counterexample) are compiled. A near-miss has at least one already-committed variant
 * that violates it, so compiling it as a hard clause would make the resulting formula inconsistent
 * with real, already-accepted data -- it is silently skipped rather than passed through. This is
 * defense in depth on top of whatever review step accepted the suggestion upstream; see
 * CONSTRAINT_MINING_DESIGN.md's epistemic contract.
 */
public final class FeatureModelFormula {

    private FeatureModelFormula() {
    }

    public static Formula compile(List<ConstraintMiner.Suggestion> acceptedSuggestions) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        List<Formula> clauses = new ArrayList<>();
        for (ConstraintMiner.Suggestion suggestion : acceptedSuggestions) {
            if (!suggestion.isHard()) continue;
            switch (suggestion.kind) {
                case REQUIRES:
                    clauses.add(f.or(f.literal(suggestion.a, false), f.literal(suggestion.b, true))); // ~A | B
                    break;
                case EXCLUDES:
                    clauses.add(f.or(f.literal(suggestion.a, false), f.literal(suggestion.b, false))); // ~A | ~B
                    break;
                case MANDATORY:
                    clauses.add(f.literal(suggestion.a, true)); // A
                    break;
            }
        }
        return clauses.isEmpty() ? f.verum() : f.and(clauses);
    }

    /**
     * Like {@link #compile}, plus structural clauses derived purely from {@code allFeatures}' own
     * {@link Feature#getRevisions()} (no mining, always sound) -- required whenever this formula is
     * used together with revision-exact atoms from {@link ModuleConditionBridge#toRevisionTerm}
     * (via {@link FeatureRevision#getLogicLiteralRepresentation()}), since {@link #compile} alone
     * only knows feature-NAME atoms and has no notion of revision atoms at all.
     *
     * <p>Two clause families, both structural/unconditional:
     * <ul>
     *   <li><b>link</b>: for every revision {@code r} of every feature {@code f}, {@code r -> f} --
     *       a selected revision entails its feature is present, letting feature-NAME-level mined
     *       clauses (REQUIRES/EXCLUDES/MANDATORY, from {@link #compile}) correctly constrain
     *       revision atoms too. Without this, revision atoms are logically disconnected from
     *       everything the miner ever learned.</li>
     *   <li><b>at-most-one-revision-per-feature</b>: pairwise {@code ~ri | ~rj} for every pair of
     *       revisions of the same feature -- a {@code Configuration} selects at most one revision
     *       per feature; without this, SAT could treat two revisions of the same feature as
     *       simultaneously selectable and reach unsound conclusions. Pairwise, not a
     *       cardinality-network encoding: real repositories have very few revisions per feature, so
     *       O(k^2) clauses is negligible and simplest to verify by reading.</li>
     * </ul>
     */
    public static Formula compileRevisionAware(List<ConstraintMiner.Suggestion> acceptedSuggestions,
                                                Collection<? extends Feature> allFeatures) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        List<Formula> clauses = new ArrayList<>();
        clauses.add(compile(acceptedSuggestions));
        for (Feature feature : allFeatures) {
            List<? extends FeatureRevision> revisions = new ArrayList<>(feature.getRevisions());
            for (FeatureRevision revision : revisions) {
                // r -> f, i.e. ~r | f
                clauses.add(f.or(f.literal(revision.getLogicLiteralRepresentation(), false),
                        f.literal(feature.getName(), true)));
            }
            for (int i = 0; i < revisions.size(); i++) {
                for (int j = i + 1; j < revisions.size(); j++) {
                    clauses.add(f.or(f.literal(revisions.get(i).getLogicLiteralRepresentation(), false),
                            f.literal(revisions.get(j).getLogicLiteralRepresentation(), false)));
                }
            }
        }
        return f.and(clauses);
    }
}