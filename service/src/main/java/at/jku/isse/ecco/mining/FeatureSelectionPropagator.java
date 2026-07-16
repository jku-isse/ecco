package at.jku.isse.ecco.mining;

import java.util.List;
import java.util.Set;

/**
 * Restores a tentative feature-name selection to consistency with accepted hard REQUIRES/EXCLUDES
 * suggestions after one feature was just toggled, without ever overriding that feature's own new
 * state -- only other features get adjusted to resolve a violation. Free of any GUI dependency
 * (same rationale as {@link ConstraintMiner} itself) so it's directly unit-testable; see
 * {@link at.jku.isse.ecco.gui.io.FeatureTogglePanel} for the JavaFX wiring that calls this against
 * real checkboxes.
 */
public final class FeatureSelectionPropagator {

    private FeatureSelectionPropagator() {
    }

    /**
     * @param selected           mutable set of currently-selected feature names, modified in place to
     *                           resolve violations of {@code acceptedSuggestions} by adjusting
     *                           features other than {@code justToggled}.
     * @param justToggled        the feature name whose state was just deliberately changed by the
     *                           user; never adjusted here.
     * @param acceptedSuggestions accepted suggestions to enforce; only hard ones (see
     *                           {@link ConstraintMiner.Suggestion#isHard()}) are considered, same as
     *                           {@link ConstraintViolationChecker}. MANDATORY suggestions are not
     *                           acted on here -- a MANDATORY feature is expected to already be locked
     *                           selected by the caller (see {@code lockedFeatureNames}) rather than
     *                           fought over during propagation.
     * @param lockedFeatureNames feature names that must never be adjusted (e.g. MANDATORY-locked,
     *                           disabled checkboxes) -- skipped as adjustment targets.
     */
    public static void propagate(Set<String> selected, String justToggled,
                                  List<ConstraintMiner.Suggestion> acceptedSuggestions,
                                  Set<String> lockedFeatureNames) {
        // bounded fixpoint: a self-contradictory accepted set (nothing stops two conflicting
        // suggestions both being accepted) must not be able to loop forever, so cap at one
        // resolution per suggestion at most.
        int cap = acceptedSuggestions.size() + 1;
        for (int i = 0; i < cap; i++) {
            if (ConstraintViolationChecker.checkViolations(selected, acceptedSuggestions).isEmpty()) {
                return;
            }

            boolean changed = false;
            for (ConstraintMiner.Suggestion suggestion : acceptedSuggestions) {
                if (!suggestion.isHard()) continue;

                if (suggestion.kind == ConstraintMiner.Kind.REQUIRES
                        && selected.contains(suggestion.a) && !selected.contains(suggestion.b)) {
                    if (suggestion.b.equals(justToggled)) {
                        // b was just (deliberately) unchecked -- can't re-select it, relax a instead.
                        if (!lockedFeatureNames.contains(suggestion.a) && selected.remove(suggestion.a)) {
                            changed = true;
                            break;
                        }
                    } else if (!lockedFeatureNames.contains(suggestion.b) && selected.add(suggestion.b)) {
                        changed = true;
                        break;
                    }
                } else if (suggestion.kind == ConstraintMiner.Kind.EXCLUDES
                        && selected.contains(suggestion.a) && selected.contains(suggestion.b)) {
                    String toDeselect = suggestion.a.equals(justToggled) ? suggestion.b : suggestion.a;
                    if (!lockedFeatureNames.contains(toDeselect) && selected.remove(toDeselect)) {
                        changed = true;
                        break;
                    }
                }
                // MANDATORY: expected to already be locked-selected by the caller; nothing to do here.
            }
            if (!changed) return;
        }
    }
}
