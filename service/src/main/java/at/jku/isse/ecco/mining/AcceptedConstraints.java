package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts persisted {@link Constraint}s into the plain {@code kind|a|b} signature strings
 * {@link ConstraintMiner.Suggestion}s are compared against (see
 * {@link ConstraintSuggestionPreferences#signatureOf}, same format). The one shared place this
 * conversion should happen -- {@code EccoService#acceptedSuggestions}, {@code MinimizePreviewCommand},
 * and {@code MinimizationResults} all need exactly this, and previously reimplemented it
 * independently (documented as manually "kept in sync" rather than shared).
 */
public final class AcceptedConstraints {

    private AcceptedConstraints() {
    }

    public static Set<String> acceptedSignatures(Collection<? extends Constraint> constraints) {
        Set<String> signatures = new HashSet<>();
        for (Constraint constraint : constraints) {
            signatures.add(Constraint.buildId(constraint.getKind().name(), constraint.getFeatureA(), constraint.getFeatureB()));
        }
        return signatures;
    }

}
