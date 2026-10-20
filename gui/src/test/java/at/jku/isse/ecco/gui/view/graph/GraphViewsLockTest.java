package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.service.EccoService;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Artifact Graph, the Knowledge Graph and the minimization results read the repository holding
 * the service's lock (NFR-C1b), and never on the FX thread (NFR-C3): a commit holds the lock for its
 * whole run, and the window froze meanwhile. Here the test thread holds the lock, as a commit would,
 * while the FX thread gets a status change - {@code setBaseDir()} fires it there - or a preview is
 * opened; it goes on (each {@code onFx} would otherwise time out), and the read waits in the
 * background until the lock is free.
 */
public class GraphViewsLockTest {

    @BeforeAll
    public static void start() throws Exception {
        startToolkit();
        onFx(() -> java.awt.Toolkit.getDefaultToolkit());
    }

    @Test
    @Timeout(60)
    public void theArtifactGraphBuildsItsSnapshotInTheBackgroundHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ArtifactGraphView view = onFx(() -> new ArtifactGraphView(service));
            waitUntil("drawn", () -> !threadAlive("Artifact Graph snapshot"));

            synchronized (service) {
                onFx(() -> view.statusChangedEvent(service));
                onFx(() -> { }); // the FX thread is free, also after what the event queued
                waitUntilBlocked("Artifact Graph snapshot");
            }
            waitUntil("the snapshot was built", () -> !threadAlive("Artifact Graph snapshot"));
        }
    }

    @Test
    @Timeout(60)
    public void theKnowledgeGraphBuildsItsSnapshotInTheBackgroundHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            KnowledgeGraphView view = onFx(() -> new KnowledgeGraphView(service));
            waitUntil("drawn", () -> !threadAlive("Knowledge Graph snapshot"));

            synchronized (service) {
                onFx(() -> view.statusChangedEvent(service));
                onFx(() -> { });
                waitUntilBlocked("Knowledge Graph snapshot");
            }
            waitUntil("the snapshot was built", () -> !threadAlive("Knowledge Graph snapshot"));
        }
    }

    /** Clicking an association opens its preview - its tree read in the background, holding the lock. */
    @Test
    @Timeout(60)
    public void theKnowledgeGraphReadsAnAssociationPreviewInTheBackground(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            KnowledgeGraphView view = onFx(() -> new KnowledgeGraphView(service));
            waitUntil("the associations are known", () -> !associationsById(view).isEmpty());
            String associationId = service.getRepository().getAssociations().stream()
                    .filter(a -> a.getRootNode() != null && KnowledgeGraphView.findRepresentativeContentNode(a.getRootNode()) != null)
                    .map(Association::getId).findFirst().orElseThrow();
            Method open = KnowledgeGraphView.class.getDeclaredMethod("openDetachedPreview", String.class);
            open.setAccessible(true);

            synchronized (service) {
                onFx(() -> open.invoke(view, associationId));
                onFx(() -> { });
                waitUntilBlocked("Association preview");
                assertTrue(onFx(GraphViewsLockTest::previewWindows).isEmpty(), "nothing read yet");
            }
            waitUntil("the preview opened", () -> !previewWindows().isEmpty());
            onFx(() -> previewWindows().forEach(Window::hide));
        }
    }

    /** The minimization results' status change reads the stored results - also not on the FX thread. */
    @Test
    @Timeout(60)
    public void theMinimizationResultsReadInTheBackground(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            MinimizationResults results = new MinimizationResults(service);
            synchronized (service) {
                onFx(() -> results.statusChangedEvent(service));
                onFx(() -> { });
                waitUntilBlocked("Minimization results refresh");
            }
            waitUntil("read", () -> !threadAlive("Minimization results refresh"));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    private static EccoService sample(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "b.txt", "b\n"));
        return service;
    }

    private static boolean threadAlive(String name) {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals(name) && t.isAlive());
    }

    /** Waits until a thread of that name waits for a lock - the service's, which the test thread holds. */
    private static void waitUntilBlocked(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals(name) && t.getState() == Thread.State.BLOCKED))
                return;
            Thread.sleep(20);
        }
        fail("no thread " + name + " waits for the lock");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Association> associationsById(KnowledgeGraphView view) throws Exception {
        Field field = KnowledgeGraphView.class.getDeclaredField("associationsById");
        field.setAccessible(true);
        return (Map<String, Association>) field.get(view);
    }

    private static List<Window> previewWindows() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage stage && stage.getTitle() != null && stage.getTitle().startsWith("Association Preview"))
                .toList();
    }
}
