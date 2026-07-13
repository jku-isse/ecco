package at.jku.isse.ecco.mining;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConstraintViolationChecker} operates on plain {@code Set<String>}/{@code Suggestion}
 * inputs, same design philosophy as {@link ConstraintMiner} itself -- no ECCO plumbing needed.
 */
public class ConstraintViolationCheckerTest {

    private static ConstraintMiner.Suggestion hard(ConstraintMiner.Kind kind, String a, String b) {
        return new ConstraintMiner.Suggestion(kind, a, b, 1.0, 1.0, 4, List.of());
    }

    private static ConstraintMiner.Suggestion nearMiss(ConstraintMiner.Kind kind, String a, String b) {
        return new ConstraintMiner.Suggestion(kind, a, b, 1.0, 0.9, 4, List.of(0));
    }

    @Test
    public void requires_selectedWithoutConsequent_isViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A"), List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("REQUIRES"));
    }

    @Test
    public void requires_bothSelected_noViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A", "B"), List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")));
        assertTrue(violations.isEmpty());
    }

    @Test
    public void requires_antecedentNotSelected_noViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("C"), List.of(hard(ConstraintMiner.Kind.REQUIRES, "A", "B")));
        assertTrue(violations.isEmpty());
    }

    @Test
    public void excludes_bothSelected_isViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A", "B"), List.of(hard(ConstraintMiner.Kind.EXCLUDES, "A", "B")));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("EXCLUDES"));
    }

    @Test
    public void excludes_onlyOneSelected_noViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A"), List.of(hard(ConstraintMiner.Kind.EXCLUDES, "A", "B")));
        assertTrue(violations.isEmpty());
    }

    @Test
    public void mandatory_notSelected_isViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("B"), List.of(hard(ConstraintMiner.Kind.MANDATORY, "A", null)));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("MANDATORY"));
    }

    @Test
    public void mandatory_selected_noViolation() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A"), List.of(hard(ConstraintMiner.Kind.MANDATORY, "A", null)));
        assertTrue(violations.isEmpty());
    }

    @Test
    public void nearMissSuggestion_isIgnoredEvenIfViolated() {
        List<String> violations = ConstraintViolationChecker.checkViolations(
                Set.of("A"), List.of(nearMiss(ConstraintMiner.Kind.REQUIRES, "A", "B")));
        assertTrue(violations.isEmpty(), "only hard (accepted, exception-free) rules should be checked");
    }

    @Test
    public void noAcceptedSuggestions_noViolations() {
        List<String> violations = ConstraintViolationChecker.checkViolations(Set.of("A", "B"), List.of());
        assertTrue(violations.isEmpty());
    }
}
