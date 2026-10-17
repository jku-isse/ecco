package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.view.artifacts.AssociationInfoImpl;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Which detail tabs the artifact detail view shows for a node: Info always, the order graph for an
 * ordered node, and the viewers of the plugin that read the node - none while no repository is open.
 */
public class ArtifactDetailViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** A node belongs to the plugin of its nearest plugin ancestor; directories and the root to none. */
    @Test
    @Timeout(60)
    public void aNodesPluginIsTheNearestPluginAbove(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            Node directory = root.getChildren().get(0);
            Node file = directory.getChildren().get(0);
            Node line = file.getChildren().get(0);

            assertNull(ArtifactDetailView.getPluginId(null));
            assertNull(ArtifactDetailView.getPluginId(root));
            assertNull(ArtifactDetailView.getPluginId(directory));
            assertEquals("at.jku.isse.ecco.adapter.text.TextPlugin", ArtifactDetailView.getPluginId(file));
            assertEquals(ArtifactDetailView.getPluginId(file), ArtifactDetailView.getPluginId(line));
        }
    }

    /** An ordered node adds the Graph tab; an unordered one removes it again; a viewer's tab stays. */
    @Test
    @Timeout(60)
    public void theGraphTabFollowsWhetherTheNodeIsOrdered(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));

            onFx(() -> view.showTree(file));
            assertEquals(List.of("Code", "Order graph", "Info"), onFx(() -> tabNames(view)));
            assertTrue(onFx(() -> infoText(view)).startsWith("a.txt "));

            onFx(() -> view.showTree(file.getChildren().get(0)));
            assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)));
            assertTrue(onFx(() -> infoText(view)).startsWith("a1\n"), onFx(() -> infoText(view)));

            // a directory has no plugin: its viewer tab goes
            onFx(() -> view.showTree(file.getParent()));
            assertEquals(List.of("Info"), onFx(() -> tabNames(view)));
        }
    }

    /** The key of the selected tab survives a reset and is restored by the next showTree. */
    @Test
    @Timeout(60)
    public void theSelectedTabIsRestoredAfterAReset(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node a = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            Node b = association(service, "b.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));
            assertNull(onFx(() -> view.getSelectedTabKey()), "nothing shown yet");

            onFx(() -> view.showTree(a));
            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Order graph")));
            String key = onFx(() -> view.getSelectedTabKey());
            assertEquals("pog", key);

            onFx(() -> {
                view.reset();
                view.showTree(b, key);
            });
            assertEquals("Order graph", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()));

            // a key whose tab no longer exists leaves the default selection: the first tab, the code
            onFx(() -> {
                view.reset();
                view.showTree(b.getChildren().get(0), key);
            });
            assertEquals("Code", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()));

            onFx(() -> view.showTree(null));
            assertEquals(List.of(), onFx(() -> tabNames(view)));
        }
    }

    /**
     * The order graph is drawn only while its tab is selected, and runs no layout: drawn for every
     * node shown, the graph of a large file - one node per line - was laid out and redrawn without
     * end into a canvas that was not rendered, until the heap ran out.
     */
    @Test
    @Timeout(60)
    public void theOrderGraphIsDrawnOnlyWhileItsTabIsSelected(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));
            onFx(() -> view.showTree(file));
            assertEquals("Code", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()));
            Thread.sleep(500); // it was drawn in the background
            assertEquals(0, drawnNodes(view), "not drawn while the code is shown");

            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Order graph")));
            waitUntil("drawn when selected", () -> drawnNodes(view) > 0);
            Thread.sleep(300);
            assertFalse(layoutRunning(view), "placed in layers, not laid out");

            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Code")));

            // shown again with the graph's tab selected: drawn right away
            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Order graph")));
            onFx(() -> view.showTree(file));
            waitUntil("drawn", () -> drawnNodes(view) > 0);
        }
    }

    private static Object orderGraphView(ArtifactDetailView view) throws Exception {
        java.lang.reflect.Field field = ArtifactDetailView.class.getDeclaredField("partialOrderGraphView");
        field.setAccessible(true);
        return field.get(view);
    }

    private static int drawnNodes(ArtifactDetailView view) throws Exception {
        Object graphView = orderGraphView(view);
        java.lang.reflect.Method drawn = graphView.getClass().getDeclaredMethod("drawnGraph");
        drawn.setAccessible(true);
        return ((org.graphstream.graph.Graph) drawn.invoke(graphView)).getNodeCount();
    }

    private static boolean layoutRunning(ArtifactDetailView view) throws Exception {
        Object graphView = orderGraphView(view);
        java.lang.reflect.Field viewerField = graphView.getClass().getDeclaredField("viewer");
        viewerField.setAccessible(true);
        Object viewer = viewerField.get(graphView);
        if (viewer == null)
            return false;
        java.lang.reflect.Field layout = org.graphstream.ui.view.Viewer.class.getDeclaredField("optLayout");
        layout.setAccessible(true);
        return layout.get(viewer) != null;
    }

    /** Without an open repository there are no viewers; closing the repository clears the tabs. */
    @Test
    @Timeout(60)
    public void aClosedRepositoryShowsNoViewersAndClearsTheTabs(@TempDir Path tmp) throws Exception {
        EccoService service = twoFiles(tmp);
        try {
            Association association = association(service, "a.txt");
            Node line = association.getRootNode().getChildren().get(0).getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));
            // association infos reach the viewers, before and after they are injected
            List<AssociationInfoImpl> infos = List.of(new AssociationInfoImpl(association));
            onFx(() -> view.setAssociationInfo(infos));
            onFx(() -> view.showTree(line));
            onFx(() -> view.setAssociationInfo(infos));
            onFx(() -> view.setAssociationInfo(null));
            assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)));

            service.close();
            waitUntil("the tabs are cleared", () -> tabPane(view).getTabs().isEmpty());

            onFx(() -> view.showTree(line));
            assertEquals(List.of("Info"), onFx(() -> tabNames(view)), "no viewers without a repository");
        } finally {
            service.close();
        }
    }

    /** Looking for a default node, the first node below the root that some viewer can show is found. */
    @Test
    @Timeout(60)
    public void findsTheFirstNodeAViewerCanShow(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));

            Node found = onFx(() -> ArtifactDetailView.findNodeWithArtifactViewerRec(view, root));
            assertSame(root.getChildren().get(0).getChildren().get(0), found, "the file below the directory");
            Node line = found.getChildren().get(0);
            assertNull(onFx(() -> ArtifactDetailView.findNodeWithArtifactViewerRec(view, line)), "a leaf has nothing below it");
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** A repository with a.txt for A and b.txt added for B. */
    static EccoService twoFiles(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "b.txt", "b1\n"));
        return service;
    }

    /** The association whose artifacts include the file {@code name}. */
    static Association association(EccoService service, String name) {
        return service.getRepository().getAssociations().stream().filter(a -> {
            List<String> labels = new ArrayList<>();
            a.getRootNode().traverse(n -> labels.add(String.valueOf(n)));
            return labels.stream().anyMatch(l -> l.startsWith(name + " "));
        }).findFirst().orElseThrow();
    }

    private static TabPane tabPane(ArtifactDetailView view) {
        // the tabs, over the hint shown while there are none
        return (TabPane) ((javafx.scene.Parent) view.getCenter()).getChildrenUnmodifiable().stream()
                .filter(TabPane.class::isInstance).findFirst().orElseThrow();
    }

    private static List<String> tabNames(ArtifactDetailView view) {
        return tabPane(view).getTabs().stream().map(Tab::getText).toList();
    }

    private static Tab tab(ArtifactDetailView view, String name) {
        return tabPane(view).getTabs().stream().filter(t -> name.equals(t.getText())).findFirst().orElseThrow();
    }

    private static String infoText(ArtifactDetailView view) {
        return ((Label) tab(view, "Info").getContent()).getText();
    }
}
