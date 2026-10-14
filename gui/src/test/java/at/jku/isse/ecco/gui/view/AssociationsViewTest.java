package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.view.detail.AssociationDetailView;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.core.SerAssociation;
import at.jku.isse.ecco.storage.ser.tree.SerRootNode;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Associations tab decides: which associations it lists (only those with artifacts unless
 * asked for all), in which order, with which artifact count and minimized condition (seeded from and
 * kept in sync with the shared {@link MinimizationResults}), what the detail pane shows, and when
 * it lists them again.
 */
public class AssociationsViewTest {

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
    public void listsOnlyAssociationsWithArtifactsUnlessAllAreAskedFor(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));
            // a repository prunes associations that lose all their artifacts, so add one by hand
            SerAssociation empty = new SerAssociation();
            empty.setId("empty");
            empty.setRootNode(new SerRootNode());
            List<Association> associations = new ArrayList<>(service.getRepository().getAssociations());
            Set<String> withArtifacts = ids(associations);
            associations.add(empty);

            view.associationsChangedEvent(associations);
            waitUntil("the new list", () -> known(view) == associations.size());

            assertEquals(withArtifacts, onFx(() -> shownIds(view)));
            onFx(() -> showEmpty(view).setSelected(true));
            Set<String> all = new HashSet<>(withArtifacts);
            all.add("empty");
            assertEquals(all, onFx(() -> shownIds(view)));
            onFx(() -> showEmpty(view).setSelected(false));
            assertEquals(withArtifacts, onFx(() -> shownIds(view)));
        }
    }

    @Test
    @Timeout(60)
    public void listsTheMostArtifactsFirstWithTheirCount(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b1\nb2\nb3\nb4\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c1\nc2\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));

            List<AssociationsView.AssociationInfo> shown = onFx(() -> List.copyOf(table(view).getItems()));
            for (AssociationsView.AssociationInfo info : shown)
                assertEquals(info.getAssociation().getRootNode().countArtifacts(), info.getNumArtifacts());
            List<Integer> counts = shown.stream().map(AssociationsView.AssociationInfo::getNumArtifacts).toList();
            assertEquals(counts.stream().sorted(Comparator.reverseOrder()).toList(), counts, "most artifacts first");
            assertTrue(counts.get(0) > counts.get(counts.size() - 1), "the counts differ: " + counts);
        }
    }

    @Test
    @Timeout(60)
    public void minimizedConditionsAreSeededAndFollowTheSharedResults(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            MinimizationResults minimization = new MinimizationResults(service);
            AssociationsView view = loaded(service, minimization);
            List<String> ids = onFx(() -> table(view).getItems().stream().map(i -> i.getAssociation().getId()).toList());
            String first = ids.get(0), second = ids.get(1);
            assertEquals("", onFx(() -> condition(view, first)), "empty until a minimization fills it in");

            onFx(() -> minimization.getMinimizedByAssociationId().put(first, "A"));
            assertEquals("A", onFx(() -> condition(view, first)), "a new result reaches the row at once");
            assertEquals("", onFx(() -> condition(view, second)));

            onFx(() -> minimization.getMinimizedByAssociationId().remove(first));
            assertEquals("", onFx(() -> condition(view, first)), "a dropped result empties the row");

            // a refresh builds new rows: they start from what the shared results hold
            onFx(() -> minimization.getMinimizedByAssociationId().put(second, "B"));
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the refreshed rows", () -> !toolBar(view).isDisable() && "B".equals(condition(view, second)));
            assertEquals("", onFx(() -> condition(view, first)));
        }
    }

    @Test
    @Timeout(60)
    public void refreshListsAssociationsTheViewWasNotToldAbout(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));
            // stop listening, so only the Refresh button can bring the new association in
            service.removeListener(view);
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            int now = service.getRepository().getAssociations().size();
            assertTrue(now > 1);
            assertEquals(1, onFx(() -> table(view).getItems().size()));

            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the new associations", () -> !toolBar(view).isDisable() && table(view).getItems().size() == now);
            assertEquals(ids(service.getRepository().getAssociations()), onFx(() -> shownIds(view)));
        }
    }

    @Test
    @Timeout(60)
    public void aRefreshThatFailsReenablesTheToolbar(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("repo"));
        AtomicBoolean failing = new AtomicBoolean(false);
        try (EccoService service = new EccoService() {
            @Override
            public synchronized Repository getRepository() {
                if (failing.get())
                    throw new EccoException("repository unavailable");
                return super.getRepository();
            }
        }) {
            service.setRepositoryDir(tmp.resolve("repo").resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));

            failing.set(true);
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the failure is shown", () -> Window.getWindows().stream()
                    .anyMatch(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane));
            failing.set(false);

            assertFalse(onFx(() -> toolBar(view).isDisable()), "the toolbar is enabled again");
            assertEquals(1, onFx(() -> table(view).getItems().size()), "the old list stays");
        }
    }

    @Test
    @Timeout(60)
    public void theSelectedRowIsShownInTheAssociationDetails(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));
            AssociationDetailView details = onFx(() -> (AssociationDetailView) find(view, n -> n instanceof AssociationDetailView));
            assertNull(onFx(() -> details.getCenter()), "nothing is selected yet");

            String selected = onFx(() -> {
                table(view).getSelectionModel().select(1);
                return table(view).getItems().get(1).getAssociation().getId();
            });
            assertNotNull(onFx(() -> details.getCenter()));
            TextField idField = onFx(() -> (TextField) find(details, n -> n instanceof TextField));
            assertEquals(selected, onFx(() -> idField.getText()));

            onFx(() -> table(view).getSelectionModel().clearSelection());
            assertNull(onFx(() -> details.getCenter()));
            assertEquals("", onFx(() -> idField.getText()));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesAndDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        try {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            AssociationsView view = loaded(service, new MinimizationResults(service));
            assertFalse(onFx(() -> view.isDisable()));

            service.close();
            waitUntil("the view is emptied", () -> view.isDisable() && table(view).getItems().isEmpty());
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Waits until the repository's associations are all known to the view (shown or filtered out). */
    private static AssociationsView loaded(EccoService service, MinimizationResults minimization) throws Exception {
        int associations = service.getRepository().getAssociations().size();
        AssociationsView view = onFx(() -> new AssociationsView(service, minimization));
        waitUntil(associations + " associations", () -> !view.isDisable() && known(view) == associations);
        return view;
    }

    /** All rows, including those the "without artifacts" filter hides. */
    private static int known(AssociationsView view) {
        CheckBox box = showEmpty(view);
        boolean before = box.isSelected();
        box.setSelected(true);
        int all = table(view).getItems().size();
        box.setSelected(before);
        return all;
    }

    private static Set<String> shownIds(AssociationsView view) {
        return table(view).getItems().stream().map(i -> i.getAssociation().getId()).collect(Collectors.toSet());
    }

    private static Set<String> ids(java.util.Collection<? extends Association> associations) {
        return associations.stream().map(Association::getId).collect(Collectors.toSet());
    }

    private static String condition(AssociationsView view, String associationId) {
        return table(view).getItems().stream().filter(i -> i.getAssociation().getId().equals(associationId))
                .findFirst().orElseThrow().getSimplifiedCondition();
    }

    private static CheckBox showEmpty(AssociationsView view) {
        return (CheckBox) find(view, n -> n instanceof CheckBox c && "Show Associations Without Artifacts".equals(c.getText()));
    }

    private static ToolBar toolBar(AssociationsView view) {
        return (ToolBar) view.getTop();
    }

    @SuppressWarnings("unchecked")
    private static TableView<AssociationsView.AssociationInfo> table(AssociationsView view) {
        return (TableView<AssociationsView.AssociationInfo>) find(view, n -> n instanceof TableView<?> t
                && !t.getColumns().isEmpty() && "Associations".equals(t.getColumns().get(0).getText()));
    }
}
