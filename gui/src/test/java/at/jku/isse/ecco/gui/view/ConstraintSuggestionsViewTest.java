package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the suggestion review decides: pending = mined minus accepted minus rejected, the actions
 * store their decision in the repository (the lists follow through the status event), a threshold
 * change mines again, and an accepted suggestion says whether it is trusted yet.
 *
 * <p>The five committed configurations mine, at the default min witness 4, only MANDATORY Base;
 * at 3 also REQUIRES B -> A (witness 3); at 1 also EXCLUDES A/C and B/C (witness 1); at confidence
 * 0.7 also the near miss REQUIRES A -> B (3 of 4).
 */
public class ConstraintSuggestionsViewTest {

    private static final String MANDATORY_BASE = Constraint.buildId("MANDATORY", "Base", null);
    private static final String B_REQUIRES_A = Constraint.buildId("REQUIRES", "B", "A");
    private static final String A_REQUIRES_B = Constraint.buildId("REQUIRES", "A", "B");
    private static final String A_EXCLUDES_C = Constraint.buildId("EXCLUDES", "A", "C");
    private static final String B_EXCLUDES_C = Constraint.buildId("EXCLUDES", "B", "C");

    private Stage stage;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @AfterEach
    public void closeStage() throws Exception {
        if (stage != null)
            onFx(() -> stage.close());
    }

    @Test
    @Timeout(60)
    public void pendingFollowsTheThresholdsAndLeavesOutReviewedSuggestions(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            service.acceptConstraint(Constraint.Kind.EXCLUDES, "A", "C");
            service.rejectConstraints(List.of(mined(service, B_EXCLUDES_C)));
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);

            waitUntil("the default thresholds", () -> pending(view).equals(Set.of(MANDATORY_BASE)));
            assertEquals(List.of(A_EXCLUDES_C), onFx(() -> accepted(view)));
            assertEquals(List.of(B_EXCLUDES_C), onFx(() -> rejected(view)));

            onFx(() -> minWitness(view).getValueFactory().setValue(3));
            waitUntil("min witness 3", () -> pending(view).equals(Set.of(MANDATORY_BASE, B_REQUIRES_A)));

            // at 1 the two EXCLUDES are mined too, but one is accepted and the other rejected
            onFx(() -> minWitness(view).getValueFactory().setValue(1));
            waitUntil("min witness 1", () -> pending(view).equals(Set.of(MANDATORY_BASE, B_REQUIRES_A)) && !view.getTop().isDisable());
            onFx(() -> {
                minWitness(view).getValueFactory().setValue(4);
                confidence(view).getValueFactory().setValue(0.7);
            });
            // A occurs four times, three of them with B: a near miss at confidence 0.75
            waitUntil("confidence 0.7", () -> pending(view).equals(Set.of(MANDATORY_BASE, A_REQUIRES_B)) && !view.getTop().isDisable());
            assertEquals(0, minimization.runs.get(), "reviewing nothing minimizes nothing");
        }
    }

    @Test
    @Timeout(60)
    public void theTableShowsOneStatusAtATimeWithItsButtonsAndCounts(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            service.acceptConstraint(Constraint.Kind.EXCLUDES, "A", "C");
            service.rejectConstraints(List.of(mined(service, B_EXCLUDES_C)));
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));
            waitUntil("loaded", () -> pending(view).equals(Set.of(MANDATORY_BASE)) && !view.getTop().isDisable());

            assertEquals(List.of("Pending (1)", "Accepted (1)", "Rejected (1)"), onFx(() -> findAll(view, n -> n instanceof javafx.scene.control.ToggleButton t
                    && t.getToggleGroup() != null).stream().distinct().map(n -> ((javafx.scene.control.ToggleButton) n).getText()).toList()));
            assertEquals(List.of(MANDATORY_BASE), onFx(() -> table(view).getItems().stream().map(ConstraintSuggestionsView.ReviewRow::signature).toList()));
            assertTrue(onFx(() -> button(view, "Accept").isVisible() && !button(view, "Move back to pending").isVisible()));

            onFx(() -> view.showStatus(ConstraintSuggestionsView.Status.ACCEPTED));
            assertEquals(List.of(A_EXCLUDES_C), onFx(() -> table(view).getItems().stream().map(ConstraintSuggestionsView.ReviewRow::signature).toList()));
            assertTrue(onFx(() -> !button(view, "Accept").isVisible() && button(view, "Move back to pending").isVisible()));
        }
    }

    @Test
    @Timeout(60)
    public void theEvidenceSaysInHowManyOfItsConfigurationsASuggestionHolds(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            // A occurs four times, three of them with B
            ConstraintMiner.Suggestion nearMiss = mined(service, A_REQUIRES_B);
            assertEquals(4, nearMiss.witness);
            assertEquals("3 of 4", ConstraintSuggestionsView.evidence(nearMiss));
            assertEquals("", ConstraintSuggestionsView.evidence(null));
            assertEquals("REQUIRES A -> B", ConstraintSuggestionsView.describe(A_REQUIRES_B));
        }
    }

    @Test
    @Timeout(60)
    public void acceptStoresTheSuggestionAndMinimizesAgain(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);
            onFx(() -> minWitness(view).getValueFactory().setValue(3));
            waitUntil("both suggestions", () -> pending(view).equals(Set.of(MANDATORY_BASE, B_REQUIRES_A)));

            onFx(() -> {
                selectPending(view, MANDATORY_BASE, B_REQUIRES_A);
                button(view, "Accept").fire();
            });
            waitUntil("both accepted", () -> !view.getBottom().isDisable() && pending(view).isEmpty() && accepted(view).size() == 2);

            assertEquals(Set.of(MANDATORY_BASE, B_REQUIRES_A), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get(), "one minimization for the whole selection");
            // only MANDATORY Base clears the production threshold (4 witnesses)
            waitUntil("trust labels", () -> trust(view).equals(Map.of(
                    "MANDATORY Base", "trusted",
                    "REQUIRES B -> A", "not yet trusted: needs 4 witnesses, has 3")));
        }
    }

    @Test
    @Timeout(60)
    public void anAcceptedSuggestionNoLongerMinedIsNotTrusted(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            // A and B occur together three times: never mined as excluding each other
            service.acceptConstraint(Constraint.Kind.EXCLUDES, "A", "B");
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));

            waitUntil("the label", () -> trust(view).equals(Map.of(
                    "EXCLUDES A / B", "not yet trusted: not currently reproducible")));
        }
    }

    @Test
    @Timeout(60)
    public void rejectStoresTheRejectionAndMoveBackMakesItPendingAgain(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);
            waitUntil("the suggestion", () -> pending(view).equals(Set.of(MANDATORY_BASE)));

            onFx(() -> {
                selectPending(view, MANDATORY_BASE);
                button(view, "Reject").fire();
            });
            waitUntil("rejected", () -> !view.getBottom().isDisable() && pending(view).isEmpty() && rejected(view).equals(List.of(MANDATORY_BASE)));
            assertEquals(Set.of(MANDATORY_BASE), ids(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get(), "a rejection withdraws an acceptance, so minimization runs again");
            assertTrue(onFx(() -> view.rows.stream().filter(r -> r.status() == ConstraintSuggestionsView.Status.REJECTED)
                    .allMatch(r -> r.trust() == null)), "shown without a trust label");

            onFx(() -> {
                select(view, ConstraintSuggestionsView.Status.REJECTED, MANDATORY_BASE);
                button(view, "Move back to pending").fire();
            });
            waitUntil("pending again", () -> !view.getBottom().isDisable() && pending(view).equals(Set.of(MANDATORY_BASE)) && rejected(view).isEmpty());
            assertEquals(Set.of(), ids(service.getRepository().getRejectedConstraints()));
            assertEquals(1, minimization.runs.get(), "un-rejecting changes no accepted constraint");
        }
    }

    @Test
    @Timeout(60)
    public void moveBackFromAcceptedWithdrawsTheAcceptance(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            service.acceptConstraints(List.of(mined(service, MANDATORY_BASE)));
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);
            waitUntil("accepted", () -> !view.getBottom().isDisable() && pending(view).isEmpty() && accepted(view).equals(List.of(MANDATORY_BASE)));

            onFx(() -> {
                // nothing selected: nothing happens
                view.showStatus(ConstraintSuggestionsView.Status.ACCEPTED);
                button(view, "Move back to pending").fire();
                button(view, "Accept").fire();
                button(view, "Reject").fire();
            });
            assertEquals(0, minimization.runs.get());

            onFx(() -> {
                select(view, ConstraintSuggestionsView.Status.ACCEPTED, MANDATORY_BASE);
                button(view, "Move back to pending").fire();
            });
            waitUntil("pending again", () -> !view.getBottom().isDisable() && pending(view).equals(Set.of(MANDATORY_BASE)) && accepted(view).isEmpty());
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get());
        }
    }

    @Test
    @Timeout(60)
    public void rejectionsKeptOnThisMachineMoveIntoTheRepository(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            Path repositoryDir = service.getRepositoryDir();
            try {
                // what earlier versions stored when a suggestion was rejected
                ConstraintSuggestionPreferences.reject(repositoryDir, MANDATORY_BASE);
                ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));

                waitUntil("the old rejection", () -> rejected(view).equals(List.of(MANDATORY_BASE)) && pending(view).isEmpty());
                assertEquals(Set.of(MANDATORY_BASE), ids(service.getRepository().getRejectedConstraints()));
                assertTrue(ConstraintSuggestionPreferences.getRejected(repositoryDir).isEmpty(), "moved, not copied");
            } finally {
                ConstraintSuggestionPreferences.forget(repositoryDir);
            }
        }
    }

    @Test
    @Timeout(60)
    public void aHiddenTabRefreshesOnlyWhenShownAgain(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));
            waitUntil("the suggestion", () -> pending(view).equals(Set.of(MANDATORY_BASE)));

            view.setTabVisible(false);
            service.acceptConstraints(List.of(mined(service, MANDATORY_BASE)));
            // the status event went to a hidden tab: nothing re-mined
            Thread.sleep(300);
            assertEquals(Set.of(MANDATORY_BASE), onFx(() -> pending(view)));

            onFx(() -> view.setTabVisible(true));
            waitUntil("refreshed when shown", () -> pending(view).isEmpty() && accepted(view).equals(List.of(MANDATORY_BASE)));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesTheLists(@TempDir Path tmp) throws Exception {
        EccoService service = minedRepository(tmp);
        try {
            service.acceptConstraint(Constraint.Kind.EXCLUDES, "A", "C");
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));
            waitUntil("loaded", () -> !pending(view).isEmpty() && !accepted(view).isEmpty());

            service.close();
            waitUntil("emptied", () -> view.isDisable() && pending(view).isEmpty() && accepted(view).isEmpty());
        } finally {
            service.close();
        }
    }

    /**
     * A suggested subfeature is a pending row of its own - instead of the REQUIRES it means - and can be
     * accepted (as the parent, with that REQUIRES), moved back and rejected, stored in the repository;
     * it shaped the tree with no way to say no.
     */
    @Test
    @Timeout(60)
    public void aSuggestedSubfeatureIsReviewedLikeTheConstraints(@TempDir Path tmp) throws Exception {
        String location = System.getProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY);
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        try (EccoService service = minedRepository(tmp)) {
            // the LLM named A as B's parent when B was new
            java.nio.file.Files.writeString(log, "{\"eccoRepository\":\"" + service.getRepositoryDir().toString().replace("\\", "\\\\")
                    + "\",\"gitRepository\":\"/g\",\"before\":\"Base, A\",\"committed\":\"Base, A, B\",\"outcome\":\"auto\",\"parents\":{\"B\":\"A\"},\"positions\":{}}\n");
            String subfeature = ConstraintSuggestionsView.parentSignature("B", "A");
            assertEquals("SUBFEATURE B of A", ConstraintSuggestionsView.describe(subfeature));
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);
            onFx(() -> minWitness(view).getValueFactory().setValue(3));
            waitUntil("the subfeature", () -> pending(view).contains(subfeature));
            assertFalse(onFx(() -> pending(view)).contains(B_REQUIRES_A), "accepting the subfeature accepts it");

            onFx(() -> {
                selectPending(view, subfeature);
                button(view, "Accept").fire();
            });
            waitUntil("accepted", () -> !view.getBottom().isDisable() && accepted(view).contains(subfeature));
            assertEquals(Map.of("B", "A"), service.getRepository().getFeatureParents());
            assertEquals(Set.of(B_REQUIRES_A), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get());
            assertEquals("not yet trusted: needs 4 witnesses, has 3", onFx(() -> trust(view).get("SUBFEATURE B of A")));

            onFx(() -> {
                select(view, ConstraintSuggestionsView.Status.ACCEPTED, subfeature);
                button(view, "Move back to pending").fire();
            });
            waitUntil("pending again", () -> !view.getBottom().isDisable() && pending(view).contains(subfeature) && !accepted(view).contains(subfeature));
            assertEquals(Map.of(), service.getRepository().getFeatureParents());
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()), "its REQUIRES went with it");

            onFx(() -> {
                selectPending(view, subfeature);
                button(view, "Reject").fire();
            });
            waitUntil("rejected", () -> !view.getBottom().isDisable() && rejected(view).equals(List.of(subfeature)) && !pending(view).contains(subfeature));
            assertEquals(Map.of("B", Set.of("A")), service.getRepository().getRejectedFeatureParents());
        } finally {
            if (location == null)
                System.clearProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY);
            else
                System.setProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY, location);
        }
    }

    /**
     * The review actions store their decision in the background (NFR-C3): while a commit holds the
     * service's lock, the FX thread goes on - it waited for the lock, the window frozen - and the
     * decision follows once the lock is free, with the minimization it asks for.
     */
    @Test
    @Timeout(60)
    public void acceptDoesNotBlockTheFxThreadWhileTheServiceIsLocked(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);
            waitUntil("the suggestion", () -> pending(view).equals(Set.of(MANDATORY_BASE)) && !view.getTop().isDisable());

            synchronized (service) {
                long start = System.nanoTime();
                onFx(() -> {
                    selectPending(view, MANDATORY_BASE);
                    button(view, "Accept").fire();
                });
                onFx(() -> {
                });
                assertTrue(System.nanoTime() - start < 2_000_000_000L, "the FX thread did not wait for the lock");
                assertTrue(onFx(() -> view.getBottom().isDisable()), "the actions are disabled while the decision waits");
                assertEquals(Set.of(), ids(service.getRepository().getConstraints()), "not stored while the lock is held");
            }
            waitUntil("accepted", () -> !view.getBottom().isDisable() && pending(view).isEmpty() && accepted(view).equals(List.of(MANDATORY_BASE)));
            assertEquals(Set.of(MANDATORY_BASE), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get());
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Runs nothing, counts the runs the view asks for. */
    private static final class CountingMinimizationResults extends MinimizationResults {
        final AtomicInteger runs = new AtomicInteger();

        CountingMinimizationResults(EccoService service) {
            super(service);
        }

        @Override
        public void run() {
            runs.incrementAndGet();
        }
    }

    private static EccoService minedRepository(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        String[] configurations = {"Base, A, B", "Base, A, B", "Base, A, B", "Base, A", "Base, C"};
        for (int i = 0; i < configurations.length; i++)
            commit(service, tmp.resolve("v" + i), configurations[i], Map.of("f" + i + ".txt", i + "\n"));
        return service;
    }

    /** In an off-screen window, so list cells are rendered. */
    private ConstraintSuggestionsView show(EccoService service, MinimizationResults minimization) throws Exception {
        return onFx(() -> {
            ConstraintSuggestionsView view = new ConstraintSuggestionsView(service, minimization);
            stage = new Stage();
            stage.setScene(new Scene(view, 1000, 700));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            return view;
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<ConstraintSuggestionsView.ReviewRow> table(ConstraintSuggestionsView view) {
        return (TableView<ConstraintSuggestionsView.ReviewRow>) find(view, n -> n instanceof TableView);
    }

    private static List<String> signatures(ConstraintSuggestionsView view, ConstraintSuggestionsView.Status status) {
        return view.rows.stream().filter(r -> r.status() == status).map(ConstraintSuggestionsView.ReviewRow::signature).toList();
    }

    private static Set<String> pending(ConstraintSuggestionsView view) {
        return Set.copyOf(signatures(view, ConstraintSuggestionsView.Status.PENDING));
    }

    private static List<String> accepted(ConstraintSuggestionsView view) {
        return signatures(view, ConstraintSuggestionsView.Status.ACCEPTED);
    }

    private static List<String> rejected(ConstraintSuggestionsView view) {
        return signatures(view, ConstraintSuggestionsView.Status.REJECTED);
    }

    /** The accepted ones, as the Constraint column shows them, with their Trust column. */
    private static Map<String, String> trust(ConstraintSuggestionsView view) {
        return view.rows.stream().filter(r -> r.status() == ConstraintSuggestionsView.Status.ACCEPTED)
                .collect(Collectors.toMap(r -> ConstraintSuggestionsView.describe(r.signature()), ConstraintSuggestionsView.ReviewRow::trust));
    }

    /** Shows the suggestions of [status] and selects those with these signatures in the table, as a click would. */
    private static void select(ConstraintSuggestionsView view, ConstraintSuggestionsView.Status status, String... signatures) {
        view.showStatus(status);
        TableView<ConstraintSuggestionsView.ReviewRow> table = table(view);
        for (ConstraintSuggestionsView.ReviewRow row : table.getItems())
            if (List.of(signatures).contains(row.signature()))
                table.getSelectionModel().select(row);
    }

    private static void selectPending(ConstraintSuggestionsView view, String... signatures) {
        select(view, ConstraintSuggestionsView.Status.PENDING, signatures);
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> minWitness(ConstraintSuggestionsView view) {
        return (Spinner<Integer>) findAll(view, n -> n instanceof Spinner).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Double> confidence(ConstraintSuggestionsView view) {
        return (Spinner<Double>) findAll(view, n -> n instanceof Spinner).get(1);
    }

    private static Set<String> ids(Collection<? extends Constraint> constraints) {
        return constraints.stream().map(Constraint::getId).collect(Collectors.toSet());
    }

    /** The suggestion with this signature, mined from the repository's configurations at the loosest thresholds. */
    private static ConstraintMiner.Suggestion mined(EccoService service, String signature) {
        return new ConstraintMiner(1, 0.0, null).mine(ConfigurationBridge.readConfigurations(service)).stream()
                .filter(s -> ConstraintSuggestionPreferences.signatureOf(s).equals(signature)).findFirst().orElseThrow();
    }
}
