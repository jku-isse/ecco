package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EccoService#acceptConstraints}/{@link EccoService#unacceptConstraints} exist specifically
 * to replace {@code ConstraintSuggestionsView}'s old one-{@code acceptConstraint}-call-per-suggestion
 * loop, which made accepting N suggestions at once persist N times AND fire N real
 * {@link EccoListener#statusChangedEvent} events - each of which every open GUI tab (the Feature
 * Model graph, this same constraint-suggestions view's own re-mining, ...) reacts to with a full,
 * O(commits x features) re-scan. The property that actually matters for that fix is exercised here:
 * one batch call does the same persistence as N individual calls, but fires the event exactly ONCE
 * - not wall-clock timing, which would be a flaky, environment-dependent thing to assert.
 */
public class ConstraintBatchAcceptTest {

    @Test
    @Timeout(30)
    public void acceptConstraints_persistsAllAndFiresExactlyOneEvent() throws IOException {
        Path repoDir = Files.createTempDirectory("constraint-batch-accept").resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            AtomicInteger eventCount = new AtomicInteger();
            service.addListener(new EccoListener() {
                @Override
                public void statusChangedEvent(EccoService s) {
                    eventCount.incrementAndGet();
                }
            });

            List<ConstraintMiner.Suggestion> suggestions = List.of(
                    new ConstraintMiner.Suggestion(ConstraintMiner.Kind.MANDATORY, "A", null, 1.0, 1.0, 5, List.of()),
                    new ConstraintMiner.Suggestion(ConstraintMiner.Kind.MANDATORY, "B", null, 1.0, 1.0, 5, List.of()),
                    new ConstraintMiner.Suggestion(ConstraintMiner.Kind.EXCLUDES, "C", "D", 1.0, 1.0, 5, List.of()));

            service.acceptConstraints(suggestions);

            assertEquals(1, eventCount.get(), "one batch accept must fire exactly one status-changed event, not one per suggestion");
            assertEquals(3, service.getRepository().getConstraints().size());
            assertTrue(hasConstraint(service, Constraint.Kind.MANDATORY, "A", null));
            assertTrue(hasConstraint(service, Constraint.Kind.MANDATORY, "B", null));
            assertTrue(hasConstraint(service, Constraint.Kind.EXCLUDES, "C", "D"));
        }
    }

    @Test
    @Timeout(30)
    public void acceptConstraints_emptyList_isNoOpNoEvent() throws IOException {
        Path repoDir = Files.createTempDirectory("constraint-batch-accept-empty").resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            AtomicInteger eventCount = new AtomicInteger();
            service.addListener(new EccoListener() {
                @Override
                public void statusChangedEvent(EccoService s) {
                    eventCount.incrementAndGet();
                }
            });

            service.acceptConstraints(List.of());

            assertEquals(0, eventCount.get(), "an empty batch must not fire an event or write a transaction");
            assertEquals(0, service.getRepository().getConstraints().size());
        }
    }

    @Test
    @Timeout(30)
    public void unacceptConstraints_removesAllAndFiresExactlyOneEvent() throws IOException {
        Path repoDir = Files.createTempDirectory("constraint-batch-unaccept").resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "B", null);
            service.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "C", "D");
            assertEquals(3, service.getRepository().getConstraints().size());

            AtomicInteger eventCount = new AtomicInteger();
            service.addListener(new EccoListener() {
                @Override
                public void statusChangedEvent(EccoService s) {
                    eventCount.incrementAndGet();
                }
            });

            List<ConstraintSuggestionPreferences.AcceptedConstraint> toUndo = List.of(
                    ConstraintSuggestionPreferences.parseSignature("MANDATORY|A|"),
                    ConstraintSuggestionPreferences.parseSignature("EXCLUDES|C|D"));

            service.unacceptConstraints(toUndo);

            assertEquals(1, eventCount.get(), "one batch unaccept must fire exactly one status-changed event, not one per constraint");
            assertEquals(1, service.getRepository().getConstraints().size(), "only the constraint NOT in the batch should remain");
            assertTrue(hasConstraint(service, Constraint.Kind.MANDATORY, "B", null));
            assertFalse(hasConstraint(service, Constraint.Kind.MANDATORY, "A", null));
            assertFalse(hasConstraint(service, Constraint.Kind.EXCLUDES, "C", "D"));
        }
    }

    private static boolean hasConstraint(EccoService service, Constraint.Kind kind, String featureA, String featureB) {
        for (Constraint constraint : service.getRepository().getConstraints()) {
            if (constraint.getKind() == kind
                    && constraint.getFeatureA().equals(featureA)
                    && java.util.Objects.equals(constraint.getFeatureB(), featureB)) {
                return true;
            }
        }
        return false;
    }
}
