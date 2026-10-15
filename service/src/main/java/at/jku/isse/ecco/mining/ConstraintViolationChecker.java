package at.jku.isse.ecco.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks a configuration's selected feature names against accepted constraint suggestions.
 *
 * <p>Every entry of a configuration is already a positive selection -- everything else is
 * implicitly negative at the repository level (see {@link ConfigurationBridge}) -- so each
 * accepted {@link ConstraintMiner.Suggestion} can be checked with a direct boolean test, no
 * SAT solving needed - {@link at.jku.isse.ecco.core.Constraint.Kind#isViolatedBy}, the same
 * meaning as its clause in {@link FeatureModelFormula}. Only hard (exception-free) suggestions are considered: near-miss
 * suggestions are hypotheses, not accepted rules.
 */
public final class ConstraintViolationChecker {

    private ConstraintViolationChecker() {
    }

    public static List<String> checkViolations(Set<String> selectedFeatures,
                                                List<ConstraintMiner.Suggestion> acceptedSuggestions) {
        List<String> violations = new ArrayList<>();
        for (ConstraintMiner.Suggestion suggestion : acceptedSuggestions) {
            if (!suggestion.isHard()) continue;
            if (suggestion.kind.isViolatedBy(selectedFeatures, suggestion.a, suggestion.b))
                violations.add(suggestion.kind.violation(suggestion.a, suggestion.b));
        }
        return violations;
    }
}
