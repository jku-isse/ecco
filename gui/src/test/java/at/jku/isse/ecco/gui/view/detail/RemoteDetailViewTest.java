package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the remote detail pane decides: what it shows for a remote (name, address, type, the
 * features last fetched), and that it shows and offers nothing without one. Pins two current
 * defects: the feature tree is always the "origin" remote's, and Save/Fetch have no action.
 */
public class RemoteDetailViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void withoutARemoteNothingIsShownOrOffered(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", "localhost:3770", Remote.Type.REMOTE);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            assertNull(onFx(view::getCenter));
            assertTrue(onFx(() -> toolBar(view).isDisable()));

            Parent grid = onFx(() -> {
                view.showRemote(service.getRemote("origin"));
                return grid(view);
            });
            assertEquals(Remote.Type.REMOTE, onFx(() -> typeBox(grid).getValue()));

            onFx(() -> view.showRemote(null));
            assertNull(onFx(view::getCenter));
            assertTrue(onFx(() -> toolBar(view).isDisable()));
            assertEquals(List.of("", ""), onFx(() -> texts(grid)), "cleared");
            assertEquals(Remote.Type.LOCAL, onFx(() -> typeBox(grid).getValue()));
            assertNull(onFx(() -> tree(grid).getRoot()));
        }
    }

    /** Each fetched feature with its revisions, labelled "id - name" and by revision id. */
    @Test
    @Timeout(60)
    public void showsTheRemoteAndTheFeaturesLastFetchedFromIt(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            service.fetch("origin");
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            Parent grid = onFx(() -> {
                view.showRemote(service.getRemote("origin"));
                return grid(view);
            });

            assertNotNull(grid);
            assertFalse(onFx(() -> toolBar(view).isDisable()));
            assertEquals(List.of("origin", origin.toString()), onFx(() -> texts(grid)));
            assertEquals(Remote.Type.LOCAL, onFx(() -> typeBox(grid).getValue()));

            List<Feature> fetched = List.copyOf(service.getRemote("origin").getFeatures());
            assertEquals(Set.of("A", "B"), fetched.stream().map(Feature::getName).collect(Collectors.toSet()));
            Map<String, List<String>> expected = fetched.stream().collect(Collectors.toMap(
                    f -> f.getId() + " - " + f.getName(), f -> f.getRevisions().stream().map(FeatureRevision::getId).toList()));
            assertEquals(expected, onFx(() -> treeLabels(grid)));
            assertTrue(onFx(() -> tree(grid).getRoot().isExpanded()));
        }
    }

    /**
     * Current behaviour, a defect: whichever remote is shown, the tree lists the features fetched
     * from the remote named "origin" (showRemote reads getRemote(ORIGIN_REMOTE_NAME)).
     */
    @Test
    @Timeout(60)
    public void theFeatureTreeIsAlwaysTheOriginRemotes(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            service.fetch("origin");
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL); // never fetched: no features
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            Parent grid = onFx(() -> {
                view.showRemote(service.getRemote("other"));
                return grid(view);
            });

            assertEquals("other", onFx(() -> texts(grid).get(0)));
            assertTrue(service.getRemote("other").getFeatures().isEmpty());
            assertEquals(2, onFx(() -> tree(grid).getRoot().getChildren().size()), "origin's A and B");
        }
    }

    /** Current behaviour, a defect: with no remote named "origin", showing any remote throws (from the Remotes tab's selection listener). */
    @Test
    @Timeout(60)
    public void showingARemoteFailsWhenThereIsNoOrigin(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            Exception failure = assertThrows(Exception.class, () -> onFx(() -> view.showRemote(service.getRemote("other"))));
            assertInstanceOf(NullPointerException.class, failure.getCause());
        }
    }

    /** Current behaviour, a defect: the toolbar's Save and Fetch buttons are enabled for a remote but do nothing. */
    @Test
    @Timeout(60)
    public void saveAndFetchHaveNoAction(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            for (String text : List.of("Save", "Fetch")) {
                Button b = onFx(() -> (Button) toolBar(view).getItems().stream()
                        .filter(n -> n instanceof Button x && x.getText().equals(text)).findFirst().orElseThrow());
                assertNull(onFx(b::getOnAction), text);
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** A repository with "A" (a.txt) and "A, B" (a.txt, b.txt), closed again. */
    private static Path origin(Path tmp) throws Exception {
        Path dir = tmp.resolve("origin");
        try (EccoService service = repository(dir)) {
            commit(service, tmp.resolve("o1"), "A", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("o2"), "A, B", Map.of("a.txt", "a\n", "b.txt", "b\n"));
        }
        return dir;
    }

    private static ToolBar toolBar(RemoteDetailView view) {
        return (ToolBar) view.getTop();
    }

    /** The details grid: the view's center while a remote is shown. */
    private static Parent grid(RemoteDetailView view) {
        return (Parent) view.getCenter();
    }

    /** Name, then address. */
    private static List<String> texts(Parent grid) {
        return findAll(grid, n -> n instanceof TextField && !(n.getParent() instanceof ComboBox)).stream()
                .map(n -> ((TextField) n).getText()).toList();
    }

    @SuppressWarnings("unchecked")
    private static TreeView<RemoteDetailView.FeatureInfo> tree(Parent grid) {
        return (TreeView<RemoteDetailView.FeatureInfo>) find(grid, n -> n instanceof TreeView);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<Remote.Type> typeBox(Parent grid) {
        return (ComboBox<Remote.Type>) find(grid, n -> n instanceof ComboBox);
    }

    private static Map<String, List<String>> treeLabels(Parent grid) {
        return tree(grid).getRoot().getChildren().stream().collect(Collectors.toMap(
                i -> i.getValue().toString(),
                i -> i.getChildren().stream().map(TreeItem::getValue).map(Object::toString).toList()));
    }
}
