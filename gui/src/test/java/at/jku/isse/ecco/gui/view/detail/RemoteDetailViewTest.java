package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the remote detail pane decides: what it shows for a remote (name, address, type, the
 * features last fetched from it), that it shows and offers nothing without one, and what Save and
 * Fetch do.
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

    /** The tree lists the features fetched from the shown remote - it used to be always those of "origin". */
    @Test
    @Timeout(60)
    public void theFeatureTreeIsTheShownRemotes(@TempDir Path tmp) throws Exception {
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
            assertEquals(0, onFx(() -> tree(grid).getRoot().getChildren().size()), "other has no features");

            onFx(() -> view.showRemote(service.getRemote("origin")));
            assertEquals(2, onFx(() -> tree(grid).getRoot().getChildren().size()), "origin's A and B");
        }
    }

    /** A remote is shown also when none is named "origin" - that threw a NullPointerException. */
    @Test
    @Timeout(60)
    public void aRemoteIsShownWithoutAnOrigin(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            Parent grid = onFx(() -> {
                view.showRemote(service.getRemote("other"));
                return grid(view);
            });
            assertEquals("other", onFx(() -> texts(grid).get(0)));
        }
    }

    /** Save stores the edited name, address and type, shows the changed remote and reports it. */
    @Test
    @Timeout(60)
    public void saveStoresTheEditedRemote(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", "localhost:3770", Remote.Type.REMOTE);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            List<String> changed = new ArrayList<>();
            Parent grid = onFx(() -> {
                view.setOnRemoteChanged((name, remote) -> changed.add(remote.getName()));
                view.showRemote(service.getRemote("origin"));
                return grid(view);
            });

            onFx(() -> {
                fields(grid).get(0).setText("upstream");
                fields(grid).get(1).setText(tmp.toString());
                typeBox(grid).setValue(Remote.Type.LOCAL);
                button(toolBar(view), "Save").fire();
            });
            waitUntil("the save", () -> "Saved.".equals(status(view)));

            assertNull(service.getRemote("origin"));
            Remote upstream = service.getRemote("upstream");
            assertEquals(tmp.toString(), upstream.getAddress());
            assertEquals(Remote.Type.LOCAL, upstream.getType());
            assertEquals(List.of("upstream"), changed);
            assertEquals("Saved.", onFx(() -> status(view)));
        }
    }

    /** A refused Save shows the reason and changes nothing. */
    @Test
    @Timeout(60)
    public void aRefusedSaveShowsWhyAndChangesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", "localhost:3770", Remote.Type.REMOTE);
            service.addRemote("other", "localhost:3771", Remote.Type.REMOTE);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            List<String> changed = new ArrayList<>();
            Parent grid = onFx(() -> {
                view.setOnRemoteChanged((name, remote) -> changed.add(remote.getName()));
                view.showRemote(service.getRemote("origin"));
                return grid(view);
            });

            onFx(() -> {
                fields(grid).get(0).setText("other");
                button(toolBar(view), "Save").fire();
            });
            waitUntil("the refusal", () -> !toolBar(view).isDisable());

            assertEquals("Remote with this name already exists.", onFx(() -> status(view)));
            assertEquals("localhost:3770", service.getRemote("origin").getAddress());
            assertEquals("localhost:3771", service.getRemote("other").getAddress());
            assertEquals(List.of(), changed);
        }
    }

    /** Fetch fetches the shown remote and shows its features; a remote that cannot be reached shows why. */
    @Test
    @Timeout(60)
    public void fetchShowsTheFetchedFeaturesOrWhyItFailed(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("upstream", origin.toString(), Remote.Type.LOCAL);
            service.addRemote("gone", tmp.resolve("gone").toString(), Remote.Type.LOCAL);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            List<String> changed = new ArrayList<>();
            Parent grid = onFx(() -> {
                view.setOnRemoteChanged((name, remote) -> changed.add(remote.getName()));
                view.showRemote(service.getRemote("upstream"));
                return grid(view);
            });
            assertEquals(0, onFx(() -> tree(grid).getRoot().getChildren().size()), "not fetched yet");

            onFx(() -> button(toolBar(view), "Fetch").fire());
            waitUntil("the fetch", () -> "Fetched.".equals(status(view)));

            assertEquals(Set.of("A", "B"), onFx(() -> tree(grid).getRoot().getChildren().stream()
                    .map(i -> i.getValue().getFeature().getName()).collect(Collectors.toSet())));
            assertFalse(onFx(() -> toolBar(view).isDisable()));
            assertEquals(List.of("upstream"), changed);

            onFx(() -> {
                view.showRemote(service.getRemote("gone"));
                button(toolBar(view), "Fetch").fire();
            });
            waitUntil("the failure", () -> !toolBar(view).isDisable() && !status(view).isEmpty() && !"Fetching...".equals(status(view)));
            assertNotEquals("Fetched.", onFx(() -> status(view)));
            assertEquals(List.of("upstream"), changed);
        }
    }

    /** A fetch after which the remote is gone (removed meanwhile) says so, instead of failing on the FX thread. */
    @Test
    @Timeout(60)
    public void aFetchedRemoteRemovedMeanwhileIsReported(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        Path repo = Files.createDirectories(tmp.resolve("repo"));
        EccoService service = new EccoService() {
            @Override
            public synchronized void fetch(String remoteName) {
                super.fetch(remoteName);
                this.removeRemote(remoteName); // as a Remove waiting for the fetch would
            }
        };
        service.setRepositoryDir(repo.resolve(".ecco"));
        service.init();
        try {
            service.addRemote("upstream", origin.toString(), Remote.Type.LOCAL);
            RemoteDetailView view = onFx(() -> new RemoteDetailView(service));
            List<String> changed = new ArrayList<>();
            onFx(() -> {
                view.setOnRemoteChanged((name, remote) -> changed.add(remote.getName()));
                view.showRemote(service.getRemote("upstream"));
                button(toolBar(view), "Fetch").fire();
            });
            waitUntil("the fetch", () -> "The remote no longer exists.".equals(status(view)));
            assertNull(onFx(view::getCenter));
            assertEquals(List.of(), changed);
        } finally {
            service.close();
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

    private static String status(RemoteDetailView view) {
        return ((Label) toolBar(view).getItems().get(toolBar(view).getItems().size() - 1)).getText();
    }

    /** Name, then address. */
    private static List<TextField> fields(Parent grid) {
        return findAll(grid, n -> n instanceof TextField && !(n.getParent() instanceof ComboBox)).stream()
                .map(n -> (TextField) n).toList();
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
