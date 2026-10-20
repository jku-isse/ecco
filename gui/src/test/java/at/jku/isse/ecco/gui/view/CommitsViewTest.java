package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeTableView;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Commits tab decides: which commits it lists (and when it lists them again), that "Compare"
 * is enabled with exactly two rows selected and opens the comparison of exactly those two, and what
 * the detail pane and the artifact tree show for the selected row.
 */
public class CommitsViewTest {

    private Stage stage;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @AfterEach
    public void closeWindows() throws Exception {
        onFx(() -> {
            for (Window window : List.copyOf(Window.getWindows()))
                window.hide();
        });
    }

    @Test
    @Timeout(60)
    public void listsEveryCommitOfTheRepositoryUnselected(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            CommitsView view = loaded(service, 2);

            assertEquals(ids(service.getCommits()), onFx(() -> view.commitsData.stream().map(Commit::getId).toList()));
            assertTrue(onFx(() -> view.commitsTable.getSelectionModel().getSelectedItems().isEmpty()));
        }
    }

    @Test
    @Timeout(60)
    public void aNewCommitIsListedThroughTheStatusEvent(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            CommitsView view = loaded(service, 1);

            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            waitUntil("the second commit", () -> view.commitsData.size() == 2);
            assertEquals(ids(service.getCommits()), onFx(() -> view.commitsData.stream().map(Commit::getId).toList()));
        }
    }

    @Test
    @Timeout(60)
    public void compareOpensTheComparisonOfTheTwoSelectedCommits(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            CommitsView view = shown(service, 3);

            onFx(() -> {
                view.commitsTable.getSelectionModel().selectIndices(0, 2);
                button(view, "Compare").fire();
            });

            Stage comparison = onFx(() -> Window.getWindows().stream()
                    .filter(w -> w instanceof Stage s && "Commit Comparison".equals(s.getTitle()))
                    .map(w -> (Stage) w).findFirst().orElse(null));
            assertNotNull(comparison, "a comparison window is open");
            assertTrue(onFx(() -> comparison.getScene().getRoot() instanceof CommitComparisonView));
        }
    }

    @Test
    @Timeout(60)
    public void compareIsEnabledWithExactlyTwoSelected(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            CommitsView view = loaded(service, 2);

            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            waitUntil("3 commits", () -> view.commitsData.size() == 3);
            var selection = onFx(() -> view.commitsTable.getSelectionModel());
            assertTrue(onFx(() -> button(view, "Compare").isDisable()), "none selected");
            onFx(() -> selection.select(0));
            assertTrue(onFx(() -> button(view, "Compare").isDisable()), "one selected");
            onFx(() -> selection.select(1));
            assertFalse(onFx(() -> button(view, "Compare").isDisable()), "two selected");
            onFx(() -> selection.select(2));
            assertTrue(onFx(() -> button(view, "Compare").isDisable()), "three selected");
            onFx(() -> selection.clearSelection(0));
            assertFalse(onFx(() -> button(view, "Compare").isDisable()), "two selected again");
        }
    }

    @Test
    @Timeout(60)
    public void theSelectedRowIsShownInTheCommitDetails(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            CommitsView view = loaded(service, 2);
            CommitDetailView details = onFx(() -> (CommitDetailView) find(view, n -> n instanceof CommitDetailView));
            assertNull(onFx(() -> details.getCenter()), "nothing is selected yet");

            Commit second = onFx(() -> view.commitsData.get(1));
            onFx(() -> table(view).getSelectionModel().select(1));
            // read in the background
            ArtifactTreeTableView tree = onFx(() -> (ArtifactTreeTableView) find(view, n -> n instanceof ArtifactTreeTableView));
            waitUntil("the details and the tree", () -> details.getCenter() != null && tree.getRoot() != null);
            TextField idField = onFx(() -> (TextField) find(details, n -> n instanceof TextField));
            assertEquals(second.getId(), onFx(() -> idField.getText()));
            // its artifacts, as a tree to pick a node from
            assertTrue(onFx(() -> treeLabels(tree.getRoot()).stream().anyMatch(l -> l.startsWith("b.txt"))),
                    onFx(() -> treeLabels(tree.getRoot()).toString()));

            onFx(() -> table(view).getSelectionModel().clearSelection());
            assertEquals("", onFx(() -> idField.getText()));
            assertNull(onFx(() -> details.getCenter()));
            assertNull(onFx(() -> tree.getRoot()));
        }
    }

    /**
     * While a commit holds the service's lock, selecting a commit does not freeze the window: its
     * details are read in the background, and shown once the lock is free. The FX thread waited for
     * the lock to compute the conditions, count the artifacts and compose the tree.
     */
    @Test
    @Timeout(60)
    public void selectingACommitDoesNotWaitForTheServiceLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            CommitsView view = loaded(service, 1);
            CommitDetailView details = onFx(() -> (CommitDetailView) find(view, n -> n instanceof CommitDetailView));
            CommitsView another;
            synchronized (service) { // as a commit running
                // onFx fails after 10 s if the FX thread waits for the lock
                onFx(() -> table(view).getSelectionModel().select(0));
                assertNull(onFx(() -> details.getCenter()), "not read while the lock is held");
                // nor are the commits listed by a view opened meanwhile
                another = onFx(() -> new CommitsView(service));
                assertTrue(onFx(() -> another.commitsData.isEmpty()));
            }
            waitUntil("the details", () -> details.getCenter() != null);
            waitUntil("the commits", () -> another.commitsData.size() == 1);
            assertEquals(service.getCommits().iterator().next().getId(),
                    onFx(() -> ((TextField) find(details, n -> n instanceof TextField)).getText()));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesAndDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        try {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            CommitsView view = loaded(service, 1);
            assertFalse(onFx(view::isDisable));

            service.close();
            waitUntil("the view is emptied", () -> view.isDisable() && view.commitsData.isEmpty());
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    private static CommitsView loaded(EccoService service, int commits) throws Exception {
        CommitsView view = onFx(() -> new CommitsView(service));
        waitUntil(commits + " commits", () -> view.commitsData.size() == commits && !view.isDisable());
        return view;
    }

    /** In an off-screen window, as the comparison window is opened on top of it. */
    private CommitsView shown(EccoService service, int commits) throws Exception {
        CommitsView view = loaded(service, commits);
        onFx(() -> {
            stage = new Stage();
            stage.setScene(new Scene(view, 1000, 700));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            view.applyCss();
            view.layout();
        });
        return view;
    }

    private static List<String> ids(Collection<Commit> commits) {
        return commits.stream().map(Commit::getId).toList();
    }

    private static TableView<Commit> table(CommitsView view) {
        return view.commitsTable;
    }

    private static List<String> treeLabels(javafx.scene.control.TreeItem<ArtifactTreeTableView.NodeWrapper> item) {
        List<String> labels = new java.util.ArrayList<>();
        labels.add(String.valueOf(item.getValue()));
        for (var child : item.getChildren())
            labels.addAll(treeLabels(child));
        return labels;
    }
}
