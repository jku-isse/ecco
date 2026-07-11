package at.jku.isse.ecco.mining;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the mining semantics documented in CONSTRAINT_MINING_DESIGN.md: clean
 * hard rules, near-misses on the very next violating variant, mandatory
 * detection, group-scoped exclusion, and the minWitness cutoff.
 */
public class ConstraintMinerTest {

    private static Set<String> cfg(String... features) {
        return Set.of(features);
    }

    @Test
    public void cleanRequires_producesOneHardRule() {
        // every variant with A also has B, but B also shows up without A --
        // so this is a one-directional A -> B, not an A <-> B equivalence.
        List<Set<String>> configs = List.of(
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("B"),
                cfg("C")
        );

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(4, 1.0, null).mine(configs);

        List<ConstraintMiner.Suggestion> requires = out.stream()
                .filter(s -> s.kind == ConstraintMiner.Kind.REQUIRES)
                .toList();
        assertEquals(1, requires.size());
        ConstraintMiner.Suggestion s = requires.get(0);
        assertEquals("A", s.a);
        assertEquals("B", s.b);
        assertTrue(s.isHard());
        assertEquals(1.0, s.confidence);
        assertEquals(4, s.witness);
    }

    @Test
    public void addingOneCounterExample_flipsHardRuleToNearMissWithExactIndex() {
        List<Set<String>> configs = List.of(
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("A"),          // index 4: A without B
                cfg("C")
        );

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(4, 0.5, null).mine(configs);

        ConstraintMiner.Suggestion s = out.stream()
                .filter(x -> x.kind == ConstraintMiner.Kind.REQUIRES && x.a.equals("A") && x.b.equals("B"))
                .findFirst().orElseThrow();

        assertTrue(!s.isHard());
        assertEquals(List.of(4), s.counterExamples);
        assertEquals(0.8, s.confidence, 1e-9); // 4 of 5 A's also have B
    }

    @Test
    public void twoWellAttestedFeaturesNeverCoOccurring_isHardExcludes() {
        List<Set<String>> configs = List.of(
                cfg("A", "X"),
                cfg("A", "X"),
                cfg("A", "X"),
                cfg("A", "X"),
                cfg("B", "X"),
                cfg("B", "X"),
                cfg("B", "X"),
                cfg("B", "X")
        );

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(4, 1.0, null).mine(configs);

        ConstraintMiner.Suggestion s = out.stream()
                .filter(x -> x.kind == ConstraintMiner.Kind.EXCLUDES)
                .findFirst().orElseThrow();
        assertEquals("A", s.a);
        assertEquals("B", s.b);
        assertTrue(s.isHard());
        assertEquals(4, s.witness);
    }

    @Test
    public void featureInEveryConfiguration_isMandatoryAndProducesNoRequires() {
        List<Set<String>> configs = List.of(
                cfg("M", "A"),
                cfg("M", "B"),
                cfg("M"),
                cfg("M", "A", "B")
        );

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(1, 1.0, null).mine(configs);

        boolean mandatory = out.stream()
                .anyMatch(s -> s.kind == ConstraintMiner.Kind.MANDATORY && s.a.equals("M"));
        assertTrue(mandatory);

        boolean requiresInvolvingM = out.stream()
                .anyMatch(s -> s.kind == ConstraintMiner.Kind.REQUIRES && (s.a.equals("M") || s.b.equals("M")));
        assertTrue(!requiresInvolvingM);
    }

    @Test
    public void groupKey_suppressesExcludesBetweenRevisionsOfSameFeature() {
        // Foo.rev1 and Foo.rev2 never co-occur (they're the same feature), but
        // are well-attested individually -- without groupKey this would look
        // like a hard EXCLUDES.
        List<Set<String>> configs = List.of(
                cfg("Foo.rev1", "X"),
                cfg("Foo.rev1", "X"),
                cfg("Foo.rev1", "X"),
                cfg("Foo.rev1", "X"),
                cfg("Foo.rev2", "X"),
                cfg("Foo.rev2", "X"),
                cfg("Foo.rev2", "X"),
                cfg("Foo.rev2", "X")
        );
        Function<String, String> groupKey = token -> token.contains(".") ? token.substring(0, token.indexOf('.')) : token;

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(4, 1.0, groupKey).mine(configs);

        boolean excludesBetweenRevisions = out.stream()
                .anyMatch(s -> s.kind == ConstraintMiner.Kind.EXCLUDES
                        && Set.of(s.a, s.b).equals(Set.of("Foo.rev1", "Foo.rev2")));
        assertTrue(!excludesBetweenRevisions);
    }

    @Test
    public void minWitnessAboveCount_suppressesRule() {
        // A -> B is a clean hard rule but only has 2 witnesses.
        List<Set<String>> configs = List.of(
                cfg("A", "B"),
                cfg("A", "B"),
                cfg("C"),
                cfg("C")
        );

        List<ConstraintMiner.Suggestion> out = new ConstraintMiner(3, 1.0, null).mine(configs);

        boolean requiresAB = out.stream()
                .anyMatch(s -> s.kind == ConstraintMiner.Kind.REQUIRES && s.a.equals("A") && s.b.equals("B"));
        assertTrue(!requiresAB);
    }

    @Test
    public void emptyConfigurations_producesNoSuggestions() {
        assertEquals(List.of(), new ConstraintMiner(1, 1.0, null).mine(List.of()));
    }
}
