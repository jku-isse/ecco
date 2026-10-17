package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;

/**
 * A graph's automatic layout stops when its view is hidden, and after a while when it is not: its
 * viewer redraws the graph on every layout step - into a canvas whose drawing commands pile up while
 * it is not rendered. The Artifact Graph's layout ran forever, and with the view hidden the heap ran
 * out (OutOfMemoryError in GraphicsContext.writePaint).
 */
public class GraphLayoutStopsTest {

    @BeforeAll
    public static void start() throws Exception {
        startToolkit();
        onFx(() -> java.awt.Toolkit.getDefaultToolkit());
    }

    @Test
    @Timeout(60)
    public void theLayoutStopsWhenTheViewIsHidden(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ArtifactGraphView view = onFx(() -> new ArtifactGraphView(service));
            onFx(() -> view.setTabVisible(true));
            waitUntil("the layout runs", () -> layoutRunning(view));

            onFx(() -> view.setTabVisible(false));
            waitUntil("the layout stopped", () -> !layoutRunning(view));
        }
    }

    @Test
    @Timeout(60)
    public void theLayoutStopsAfterAWhileAnyway(@TempDir Path tmp) throws Exception {
        long before = LayoutFreeze.timeoutMs;
        LayoutFreeze.timeoutMs = 500;
        try (EccoService service = sample(tmp)) {
            ArtifactGraphView view = onFx(() -> new ArtifactGraphView(service));
            onFx(() -> view.setTabVisible(true));
            waitUntil("the layout runs", () -> layoutRunning(view));
            waitUntil("the layout stopped by itself", () -> !layoutRunning(view));
        } finally {
            LayoutFreeze.timeoutMs = before;
        }
    }

    /**
     * A closed viewer stops listening to its graph: its pipe stayed registered and queued every later
     * change of the graph - the Dependency Graph makes a new viewer for each commit while shown, so
     * an import piled them up, each holding all its rebuilds.
     */
    @Test
    @Timeout(60)
    public void aClosedViewerNoLongerListensToTheGraph(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            DependencyGraphView view = onFx(() -> new DependencyGraphView(service));
            onFx(() -> view.setTabVisible(true));
            for (int i = 0; i < 3; i++) {
                onFx(() -> view.statusChangedEvent(service));
                Thread.sleep(300);
            }
            onFx(() -> { });
            Field graphField = DependencyGraphView.class.getDeclaredField("graph");
            graphField.setAccessible(true);
            org.graphstream.graph.Graph graph = (org.graphstream.graph.Graph) graphField.get(view);
            long sinks = java.util.stream.StreamSupport.stream(((org.graphstream.graph.implementations.AbstractGraph) graph).elementSinks().spliterator(), false).count();
            org.junit.jupiter.api.Assertions.assertTrue(sinks <= 1, "the live viewer's only: " + sinks);
        }
    }

    /** The order graph of a node (in the artifact details) runs no layout: its blocks are placed in layers. */
    @Test
    @Timeout(60)
    public void theOrderGraphRunsNoLayout(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            at.jku.isse.ecco.tree.Node file = service.getRepository().getAssociations().stream()
                    .flatMap(a -> a.getRootNode().getChildren().stream()).flatMap(d -> d.getChildren().stream())
                    .filter(f -> f.getArtifact().getPartialOrderGraph() != null).findFirst().orElseThrow();
            PartialOrderGraphView view = onFx(() -> new PartialOrderGraphView());
            onFx(() -> view.showGraph(file.getArtifact().getPartialOrderGraph(), file, service));
            waitUntil("drawn", () -> view.drawnGraph().getNodeCount() > 0);
            Thread.sleep(300);
            org.junit.jupiter.api.Assertions.assertFalse(orderLayoutRunning(view));
        }
    }

    private static boolean orderLayoutRunning(PartialOrderGraphView view) throws Exception {
        Field viewerField = PartialOrderGraphView.class.getDeclaredField("viewer");
        viewerField.setAccessible(true);
        Object viewer = viewerField.get(view);
        if (viewer == null)
            return false;
        Field layout = org.graphstream.ui.view.Viewer.class.getDeclaredField("optLayout");
        layout.setAccessible(true);
        return layout.get(viewer) != null;
    }

    /**
     * However large the repository, the Artifact Graph draws about {@link ArtifactGraphView#maxNodes}
     * nodes, the rest folded into "not shown" ones: the child-count and depth limits alone made 11,500
     * for tinycc, which the layout moved at over a second a step.
     */
    @Test
    @Timeout(60)
    public void theArtifactGraphDrawsAtMostItsNodeBudget(@TempDir Path tmp) throws Exception {
        int before = ArtifactGraphView.maxNodes;
        ArtifactGraphView.maxNodes = 50;
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Map<String, String> files = new java.util.HashMap<>();
            for (int f = 0; f < 10; f++)
                files.put("f" + f + ".txt", java.util.stream.IntStream.range(0, 30).mapToObj(l -> "line " + l + "\n").collect(java.util.stream.Collectors.joining()));
            commit(service, tmp.resolve("v1"), "A.1", files);
            ArtifactGraphView view = onFx(() -> new ArtifactGraphView(service));
            onFx(() -> view.setTabVisible(true));
            // the toolbar is disabled while the graph is filled, on another thread
            waitUntil("drawn", () -> graph(view).getNodeCount() > 0 && !toolBar(view).isDisable());

            org.graphstream.graph.Graph graph = graph(view);
            // the budget, plus one folded node for each subtree reached after it ran out
            org.junit.jupiter.api.Assertions.assertTrue(graph.getNodeCount() <= 2 * 50, "nodes: " + graph.getNodeCount());
            org.junit.jupiter.api.Assertions.assertTrue(graph.nodes().anyMatch(n -> String.valueOf(n.getAttribute("label")).contains(" not shown, ")),
                    "the rest is folded, not dropped");
        } finally {
            ArtifactGraphView.maxNodes = before;
        }
    }

    private static javafx.scene.control.ToolBar toolBar(ArtifactGraphView view) throws Exception {
        Field toolBarField = ArtifactGraphView.class.getDeclaredField("toolBar");
        toolBarField.setAccessible(true);
        return (javafx.scene.control.ToolBar) toolBarField.get(view);
    }

    private static org.graphstream.graph.Graph graph(ArtifactGraphView view) throws Exception {
        Field graphField = ArtifactGraphView.class.getDeclaredField("graph");
        graphField.setAccessible(true);
        return (org.graphstream.graph.Graph) graphField.get(view);
    }

    private static EccoService sample(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "b.txt", "b\n"));
        return service;
    }

    /** Whether the view's viewer runs an automatic layout - GraphStream keeps it in a protected field. */
    private static boolean layoutRunning(ArtifactGraphView view) throws Exception {
        Field viewerField = ArtifactGraphView.class.getDeclaredField("viewer");
        viewerField.setAccessible(true);
        FxViewer viewer = (FxViewer) viewerField.get(view);
        if (viewer == null)
            return false;
        Field layout = org.graphstream.ui.view.Viewer.class.getDeclaredField("optLayout");
        layout.setAccessible(true);
        return layout.get(viewer) != null;
    }
}
