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
 * SAT solving needed. Only hard (exception-free) suggestions are considered: near-miss
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
            switch (suggestion.kind) {
                case REQUIRES:
                    if (selectedFeatures.contains(suggestion.a) && !selectedFeatures.contains(suggestion.b)) {
                        violations.add(String.format("REQUIRES %s -> %s violated: %s is selected without %s",
                                suggestion.a, suggestion.b, suggestion.a, suggestion.b));
                    }
                    break;
                case EXCLUDES:
                    if (selectedFeatures.contains(suggestion.a) && selectedFeatures.contains(suggestion.b)) {
                        violations.add(String.format("EXCLUDES %s / %s violated: both are selected",
                                suggestion.a, suggestion.b));
                    }
                    break;
                case MANDATORY:
                    if (!selectedFeatures.contains(suggestion.a)) {
                        violations.add(String.format("MANDATORY %s violated: not selected", suggestion.a));
                    }
                    break;
            }
        }
        return violations;
    }
}
