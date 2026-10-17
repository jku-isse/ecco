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
