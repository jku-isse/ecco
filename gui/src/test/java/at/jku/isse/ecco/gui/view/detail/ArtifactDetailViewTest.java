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
            read(view);
            assertEquals(List.of("Code", "Order graph", "Info"), onFx(() -> tabNames(view)));
            assertTrue(onFx(() -> infoText(view)).startsWith("a.txt "));

            onFx(() -> view.showTree(file.getChildren().get(0)));
            read(view);
            assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)));
            assertTrue(onFx(() -> infoText(view)).startsWith("a1\n"), onFx(() -> infoText(view)));

            // a directory has no plugin: its viewer tab goes
            onFx(() -> view.showTree(file.getParent()));
            read(view);
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
            read(view);
            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Order graph")));
            String key = onFx(() -> view.getSelectedTabKey());
            assertEquals("pog", key);

            onFx(() -> {
                view.reset();
                view.showTree(b, key);
            });
            read(view);
            assertEquals("Order graph", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()));

            // a key whose tab no longer exists leaves the default selection: the first tab, the code
            onFx(() -> {
                view.reset();
                view.showTree(b.getChildren().get(0), key);
            });
            read(view);
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
            read(view);
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
            read(view);
            waitUntil("drawn", () -> drawnNodes(view) > 0);
        }
    }

    /**
     * A block chosen in the order graph is shown in the code: its tab selected, at that line - the
     * order graph's tab stays, for the next block.
     */
    @Test
    @Timeout(60)
    public void aBlockChosenInTheOrderGraphIsShownInTheCode(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> {
                ArtifactDetailView v = new ArtifactDetailView(service);
                new javafx.scene.Scene(v, 800, 600); // laid out: the code's cells exist
                return v;
            });
            onFx(() -> view.showTree(file));
            read(view);
            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Order graph")));
            waitUntil("drawn", () -> drawnNodes(view) > 0);

            Object graphView = orderGraphView(view);
            java.lang.reflect.Method choose = graphView.getClass().getDeclaredMethod("choose", String.class);
            choose.setAccessible(true);
            java.lang.reflect.Method drawn = graphView.getClass().getDeclaredMethod("drawnGraph");
            drawn.setAccessible(true);
            org.graphstream.graph.Graph graph = (org.graphstream.graph.Graph) drawn.invoke(graphView);
            String lines = graph.nodes().filter(n -> String.valueOf(n.getAttribute("label")).contains("a1")).findFirst().orElseThrow(() -> new AssertionError(graph.nodes().map(n -> String.valueOf(n.getAttribute("label"))).toList().toString())).getId();
            onFx(() -> choose.invoke(graphView, lines));

            assertEquals("Code", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()), "shown in the code");
            assertEquals(List.of("Code", "Order graph", "Info"), onFx(() -> tabNames(view)), "the order graph stays");
            // the text viewer marks the line it was shown: a1
            assertEquals("a1", onFx(() -> highlightedLine(view)));
        }
    }

    /** The text of the line the code tab's viewer highlights. */
    private static String highlightedLine(ArtifactDetailView view) {
        javafx.scene.Parent code = (javafx.scene.Parent) tab(view, "Code").getContent();
        view.applyCss();
        view.layout();
        for (javafx.scene.Node n : findAll(code, n -> n instanceof javafx.scene.control.ListCell)) {
            javafx.scene.control.ListCell<?> cell = (javafx.scene.control.ListCell<?>) n;
            if (cell.getBackground() != null && javafx.scene.paint.Color.YELLOW.equals(cell.getBackground().getFills().get(0).getFill()))
                return cell.getText();
        }
        return null;
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
            read(view);
            onFx(() -> view.setAssociationInfo(infos));
            onFx(() -> view.setAssociationInfo(null));
            assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)));

            service.close();
            waitUntil("the tabs are cleared", () -> tabPane(view).getTabs().isEmpty());

            onFx(() -> view.showTree(line));
            assertFalse(onFx(view::isReading), "nothing is read without a repository");
            assertEquals(List.of(), onFx(() -> tabNames(view)), "no viewers without a repository - nothing of it is shown");
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

    /**
     * A node is read in the background, holding the service's lock: while a commit holds it, the FX
     * thread goes on (it computed the condition and walked the tree itself, waiting for nothing - read
     * while the commit changed them), the Info tab saying the node is read; released, the node is shown.
     */
    @Test
    @Timeout(60)
    public void aNodeIsReadOffTheFxThreadHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));
            onFx(() -> view.showTree(file.getChildren().get(1)));
            read(view);
            assertTrue(onFx(() -> infoText(view)).startsWith("a2\n"));

            java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            Thread commitLike = new Thread(() -> {
                synchronized (service) {
                    locked.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            commitLike.start();
            try {
                assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
                // onFx fails if the FX thread does not get to it within 10 s
                onFx(() -> view.showTree(file));
                Thread.sleep(300);
                assertTrue(onFx(view::isReading), "not read while the lock is held");
                assertEquals("Reading the node...", onFx(() -> infoText(view)));
                assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)), "the tabs shown stay meanwhile");

                release.countDown();
                read(view);
                assertTrue(onFx(() -> infoText(view)).startsWith("a.txt "), onFx(() -> infoText(view)));
                assertEquals(List.of("Code", "Order graph", "Info"), onFx(() -> tabNames(view)));
            } finally {
                release.countDown();
                commitLike.join(10_000);
            }
        }
    }

    /** The code viewers show a copy of the tree: of the node's own file, the files of other plugins without content. */
    @Test
    @Timeout(60)
    public void theViewersShowACopyOfTheTree(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            Node file = root.getChildren().get(0).getChildren().get(0);
            NodeSnapshot copy = NodeSnapshot.of(root);
            Node fileCopy = copy.copyOf(file);
            assertNotSame(file, fileCopy);
            assertEquals(file.toString(), fileCopy.toString());
            assertEquals(file.getChildren().stream().map(String::valueOf).toList(), fileCopy.getChildren().stream().map(String::valueOf).toList());
            assertSame(file.getArtifact().getData(), fileCopy.getArtifact().getData(), "the data is shared, it is not changed");
            assertSame(file.getArtifact().getContainingNode().getContainingAssociation(), fileCopy.getArtifact().getContainingNode().getContainingAssociation(),
                    "the viewers colour by the association");
            assertSame(copy.root(), fileCopy.getParent().getParent());
            assertNull(fileCopy.getArtifact().getPartialOrderGraph(), "the order graph is read from the original");
            assertEquals(fileCopy, copy.copyOf(file), "copies compare as the originals do");

            NodeSnapshot shells = NodeSnapshot.of(root, n -> n != file);
            assertTrue(shells.copyOf(file).getChildren().isEmpty());
            assertNull(shells.copyOf(file.getChildren().get(0)));
        }
    }

    /**
     * Shown without the Artifacts view's infos (the Commits tab, a commit comparison), the viewers get
     * the hover texts read with the node, holding the service's lock: they computed the conditions
     * themselves when the mouse was over a line - on the FX thread, while a commit changed the counters.
     */
    @Test
    @Timeout(60)
    public void withoutInfosTheViewersGetTheHoverTextsReadWithTheNode(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            Association lines = at.jku.isse.ecco.adapter.view.AssociationColors.associationOf(file.getChildren().get(0));
            ArtifactDetailView view = onFx(() -> new ArtifactDetailView(service));
            onFx(() -> view.showTree(file));
            read(view);

            Object viewer = onFx(() -> tab(view, "Code").getContent());
            java.lang.reflect.Field field = viewer.getClass().getDeclaredField("colors");
            field.setAccessible(true);
            at.jku.isse.ecco.adapter.view.AssociationColors colors = (at.jku.isse.ecco.adapter.view.AssociationColors) field.get(viewer);
            assertEquals(at.jku.isse.ecco.adapter.view.AssociationColors.hoverText(lines.getId(), lines.computeCondition()),
                    onFx(() -> colors.hoverText(lines)));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Waits until the node last asked for is read and shown - in the background, holding the service's lock. */
    static void read(ArtifactDetailView view) throws Exception {
        waitUntil("the node is read", () -> !view.isReading());
    }

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
