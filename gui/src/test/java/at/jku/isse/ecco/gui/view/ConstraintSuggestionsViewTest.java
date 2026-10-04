package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
            service.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "A", "C");
            service.rejectConstraints(List.of(mined(service, B_EXCLUDES_C)));
            CountingMinimizationResults minimization = new CountingMinimizationResults(service);
            ConstraintSuggestionsView view = show(service, minimization);

            waitUntil("the default thresholds", () -> pending(view).equals(Set.of(MANDATORY_BASE)));
            assertEquals(List.of(A_EXCLUDES_C), onFx(() -> List.copyOf(accepted(view).getItems())));
            assertEquals(List.of(B_EXCLUDES_C), onFx(() -> List.copyOf(rejected(view).getItems())));

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
            waitUntil("both accepted", () -> pending(view).isEmpty() && accepted(view).getItems().size() == 2);

            assertEquals(Set.of(MANDATORY_BASE, B_REQUIRES_A), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get(), "one minimization for the whole selection");
            // only MANDATORY Base clears the production threshold (4 witnesses)
            waitUntil("trust labels", () -> cellTexts(accepted(view)).equals(Set.of(
                    "MANDATORY: Base [trusted]",
                    "REQUIRES: B → A [not yet trusted -- needs 4 witnesses, has 3]")));
        }
    }

    @Test
    @Timeout(60)
    public void anAcceptedSuggestionNoLongerMinedIsNotTrusted(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp)) {
            // A and B occur together three times: never mined as excluding each other
            service.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "A", "B");
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));

            waitUntil("the label", () -> cellTexts(accepted(view)).equals(Set.of(
                    "EXCLUDES: A → B [not yet trusted -- not currently reproducible]")));
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
            waitUntil("rejected", () -> pending(view).isEmpty() && rejected(view).getItems().equals(List.of(MANDATORY_BASE)));
            assertEquals(Set.of(MANDATORY_BASE), ids(service.getRepository().getRejectedConstraints()));
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()));
            assertEquals(1, minimization.runs.get(), "a rejection withdraws an acceptance, so minimization runs again");
            waitUntil("shown without a trust label", () -> cellTexts(rejected(view)).equals(Set.of("MANDATORY: Base")));

            onFx(() -> {
                rejected(view).getSelectionModel().select(MANDATORY_BASE);
                moveBackButtons(view).get(1).fire();
            });
            waitUntil("pending again", () -> pending(view).equals(Set.of(MANDATORY_BASE)) && rejected(view).getItems().isEmpty());
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
            waitUntil("accepted", () -> pending(view).isEmpty() && accepted(view).getItems().equals(List.of(MANDATORY_BASE)));

            onFx(() -> {
                // nothing selected: nothing happens
                moveBackButtons(view).get(0).fire();
                button(view, "Accept").fire();
                button(view, "Reject").fire();
            });
            assertEquals(0, minimization.runs.get());

            onFx(() -> {
                accepted(view).getSelectionModel().select(MANDATORY_BASE);
                moveBackButtons(view).get(0).fire();
            });
            waitUntil("pending again", () -> pending(view).equals(Set.of(MANDATORY_BASE)) && accepted(view).getItems().isEmpty());
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

                waitUntil("the old rejection", () -> rejected(view).getItems().equals(List.of(MANDATORY_BASE)) && pending(view).isEmpty());
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
            waitUntil("refreshed when shown", () -> pending(view).isEmpty() && accepted(view).getItems().equals(List.of(MANDATORY_BASE)));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesTheLists(@TempDir Path tmp) throws Exception {
        EccoService service = minedRepository(tmp);
        try {
            service.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "A", "C");
            ConstraintSuggestionsView view = show(service, new CountingMinimizationResults(service));
            waitUntil("loaded", () -> !pending(view).isEmpty() && !accepted(view).getItems().isEmpty());

            service.close();
            waitUntil("emptied", () -> view.isDisable() && pending(view).isEmpty() && accepted(view).getItems().isEmpty());
        } finally {
            service.close();
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
    private static TableView<ConstraintMiner.Suggestion> pendingTable(ConstraintSuggestionsView view) {
        return (TableView<ConstraintMiner.Suggestion>) find(view, n -> n instanceof TableView);
    }

    private static Set<String> pending(ConstraintSuggestionsView view) {
        return pendingTable(view).getItems().stream().map(ConstraintSuggestionPreferences::signatureOf).collect(Collectors.toSet());
    }

    private static void selectPending(ConstraintSuggestionsView view, String... signatures) {
        TableView<ConstraintMiner.Suggestion> table = pendingTable(view);
        for (ConstraintMiner.Suggestion suggestion : table.getItems())
            if (List.of(signatures).contains(ConstraintSuggestionPreferences.signatureOf(suggestion)))
                table.getSelectionModel().select(suggestion);
    }

    @SuppressWarnings("unchecked")
    private static ListView<String> accepted(ConstraintSuggestionsView view) {
        return (ListView<String>) findAll(view, n -> n instanceof ListView).get(0);
    }

    @SuppressWarnings("unchecked")
    private static ListView<String> rejected(ConstraintSuggestionsView view) {
        return (ListView<String>) findAll(view, n -> n instanceof ListView).get(1);
    }

    /** [accepted list's, rejected list's] */
    private static List<Button> moveBackButtons(ConstraintSuggestionsView view) {
        return findAll(view, n -> n instanceof Button b && "Move back to pending".equals(b.getText())).stream().map(n -> (Button) n).toList();
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> minWitness(ConstraintSuggestionsView view) {
        return (Spinner<Integer>) findAll(view, n -> n instanceof Spinner).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Double> confidence(ConstraintSuggestionsView view) {
        return (Spinner<Double>) findAll(view, n -> n instanceof Spinner).get(1);
    }

    private static Set<String> cellTexts(ListView<String> list) {
        return list.lookupAll(".list-cell").stream().map(n -> ((ListCell<?>) n).getText())
                .filter(t -> t != null && !t.isEmpty()).collect(Collectors.toSet());
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
