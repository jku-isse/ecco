package at.jku.isse.ecco.mining;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FeatureSelectionPropagator} operates on plain {@code Set<String>}/{@code Suggestion}
 * inputs, same design philosophy as {@link ConstraintMiner}/{@link ConstraintViolationChecker}
 * themselves -- no ECCO/JavaFX plumbing needed.
 */
public class FeatureSelectionPropagatorTest {

    private static ConstraintMiner.Suggestion hard(ConstraintMiner.Kind kind, String a, String b) {
        return new ConstraintMiner.Suggestion(kind, a, b, 1.0, 1.0, 4, List.of());
    }

    @Test
    public void requires_selectingAntecedent_selectsConsequent() {
        Set<String> selected = new HashSet<>(Set.of("A"));
        FeatureSelectionPropagator.propagate(
                selected, "A", List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")), Set.of());
        assertTrue(selected.contains("B"));
        assertTrue(ConstraintViolationChecker.checkViolations(selected, List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B"))).isEmpty());
    }

    @Test
    public void requires_deselectingConsequent_deselectsAntecedentInsteadOfOverridingTheClick() {
        Set<String> selected = new HashSet<>(Set.of("A", "B"));
        selected.remove("B"); // the user just unchecked B
        FeatureSelectionPropagator.propagate(
                selected, "B", List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")), Set.of());
        assertFalse(selected.contains("B"), "the just-toggled checkbox is authoritative, never overridden");
        assertFalse(selected.contains("A"), "A requires B, so A must be relaxed instead");
    }

    @Test
    public void excludes_selectingOneDeselectsTheOther() {
        Set<String> selected = new HashSet<>(Set.of("A", "B"));
        FeatureSelectionPropagator.propagate(
                selected, "B", List.of(hard(ConstraintMiner.Kind.EXCLUDES, "A", "B")), Set.of());
        assertTrue(selected.contains("B"), "the just-toggled checkbox is authoritative");
        assertFalse(selected.contains("A"));
    }

    @Test
    public void mandatoryLockedFeature_isNeverAdjustedByPropagation() {
        // A REQUIRES B, but B is locked (e.g. mandatory-locked elsewhere) selected=false is
        // impossible in practice since mandatory features are pre-checked -- but confirm the
        // locked set is still honored defensively if a locked feature is ever left unselected.
        Set<String> selected = new HashSet<>(Set.of("A"));
        FeatureSelectionPropagator.propagate(
                selected, "A", List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")), Set.of("B"));
        assertFalse(selected.contains("B"), "locked features must never be adjusted by propagation");
    }

    @Test
    @Timeout(5)
    public void contradictoryAcceptedPair_stopsAtCapInsteadOfLooping() {
        // A REQUIRES B and A EXCLUDES B is a self-contradictory accepted pair: once both A and B
        // are forced into disagreement, nothing can resolve without touching the just-clicked "A"
        // -- the bounded fixpoint must give up instead of oscillating forever.
        List<ConstraintMiner.Suggestion> contradictory = List.of(
                hard(ConstraintMiner.Kind.REQUIRES, "A", "B"),
                hard(ConstraintMiner.Kind.EXCLUDES, "A", "B"));
        Set<String> selected = new HashSet<>(Set.of("A"));
        FeatureSelectionPropagator.propagate(selected, "A", contradictory, Set.of());
        assertTrue(selected.contains("A"), "the just-toggled checkbox is still never overridden");
    }

    @Test
    public void noAcceptedSuggestions_selectionUnchanged() {
        Set<String> selected = new HashSet<>(Set.of("A"));
        FeatureSelectionPropagator.propagate(selected, "A", List.of(), Set.of());
        assertEquals(Set.of("A"), selected);
    }
}
