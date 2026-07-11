package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.ArrayList;
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
}