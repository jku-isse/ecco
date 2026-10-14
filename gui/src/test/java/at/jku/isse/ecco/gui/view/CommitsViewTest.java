package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Commits tab decides: which commits it lists (and when it lists them again), that at most
 * two commits can be ticked for a comparison, that "Compare" with two ticked opens the comparison of
 * exactly those two, and what the detail pane shows for the selected row. "Compare" with any other
 * number ticked opens a modal error, so that path is not driven here.
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

            assertEquals(ids(service.getCommits()), onFx(() -> view.commitsData.stream().map(i -> i.getCommit().getId()).toList()));
            assertTrue(onFx(() -> view.commitsData.stream().noneMatch(i -> i.isSelected().get())));
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
            assertEquals(ids(service.getCommits()), onFx(() -> view.commitsData.stream().map(i -> i.getCommit().getId()).toList()));
        }
    }

    @Test
    @Timeout(60)
    public void refreshFetchesTheCommitsAgainAndClearsTheTicks(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            CommitsView view = loaded(service, 2);
            onFx(() -> view.commitsData.get(0).setSelected(true));

            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the refreshed list", () -> !toolBar(view).isDisable() && view.commitsData.size() == 2
                    && view.commitsData.stream().noneMatch(i -> i.isSelected().get()));
            assertEquals(ids(service.getCommits()), onFx(() -> view.commitsData.stream().map(i -> i.getCommit().getId()).toList()));
        }
    }

    @Test
    @Timeout(60)
    public void aRefreshThatFailsLeavesTheListAndReenablesTheToolbar(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("repo"));
        AtomicBoolean failing = new AtomicBoolean(false);
        try (EccoService service = new EccoService() {
            @Override
            public synchronized Collection<Commit> getCommits() {
                if (failing.get())
                    throw new EccoException("listing failed");
                return super.getCommits();
            }
        }) {
            service.setRepositoryDir(tmp.resolve("repo").resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            CommitsView view = loaded(service, 1);
            int windows = onFx(() -> Window.getWindows().size());

            failing.set(true);
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the toolbar is enabled again", () -> !toolBar(view).isDisable());

            assertEquals(1, onFx(() -> view.commitsData.size()), "the old list stays");
            assertTrue(onFx(() -> Window.getWindows().stream().anyMatch(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane)),
                    "the failure is shown (non-modal)");
            assertEquals(windows + 1, onFx(() -> Window.getWindows().size()));
        }
    }

    @Test
    @Timeout(60)
    public void atMostTwoCommitsCanBeTickedForAComparison(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            CommitsView view = shown(service, 3);

            onFx(() -> {
                view.commitsData.get(0).setSelected(true);
                view.commitsData.get(1).setSelected(true);
                view.commitsData.get(2).setSelected(true);
            });

            assertEquals(List.of(true, true, false), onFx(() -> view.commitsData.stream().map(i -> i.isSelected().get()).toList()),
                    "the third tick is taken back");
            onFx(() -> {
                view.commitsData.get(0).setSelected(false);
                view.commitsData.get(2).setSelected(true);
            });
            assertEquals(List.of(false, true, true), onFx(() -> view.commitsData.stream().map(i -> i.isSelected().get()).toList()),
                    "after an untick another commit can be ticked");
        }
    }

    @Test
    @Timeout(60)
    public void compareOpensTheComparisonOfTheTwoTickedCommits(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            CommitsView view = shown(service, 3);

            onFx(() -> {
                view.commitsData.get(0).setSelected(true);
                view.commitsData.get(1).setSelected(true);
                button(view, "Compare").fire();
            });

            Stage comparison = onFx(() -> Window.getWindows().stream()
                    .filter(w -> w instanceof Stage s && "Commit Comparison".equals(s.getTitle()))
                    .map(w -> (Stage) w).findFirst().orElse(null));
            assertNotNull(comparison, "a comparison window is open");
            assertTrue(onFx(() -> comparison.getScene().getRoot() instanceof CommitComparisonView));
            waitUntil("the toolbar is enabled again", () -> !toolBar(view).isDisable());
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

            Commit second = onFx(() -> view.commitsData.get(1).getCommit());
            onFx(() -> table(view).getSelectionModel().select(1));
            assertNotNull(onFx(() -> details.getCenter()), "the details are shown");
            TextField idField = onFx(() -> (TextField) find(details, n -> n instanceof TextField));
            assertEquals(second.getId(), onFx(() -> idField.getText()));

            onFx(() -> table(view).getSelectionModel().clearSelection());
            assertEquals("", onFx(() -> idField.getText()));
            assertNull(onFx(() -> details.getCenter()));
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

    /** In an off-screen window, so the "Selected" cells exist (they hold the at-most-two rule). */
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

    private static ToolBar toolBar(CommitsView view) {
        return (ToolBar) view.getTop();
    }

    @SuppressWarnings("unchecked")
    private static TableView<CommitsView.CommitInfo> table(CommitsView view) {
        return (TableView<CommitsView.CommitInfo>) find(view, n -> n instanceof TableView<?> t && t.getItems() == view.commitsData);
    }
}
