package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dependency graph is drawn on the AWT thread, queued from the FX thread: a repository closed
 * before the queued drawing ran made it throw "Service is not initialized" there.
 */
public class DependencyGraphViewTest {

    @BeforeAll
    public static void start() throws Exception {
        startToolkit();
        // AWT is started on the FX thread, as the views do: on macOS, started from the test thread
        // while the FX thread (AppKit's main thread) waits for it too, both hang
        onFx(() -> java.awt.Toolkit.getDefaultToolkit());
    }

    /** Shown, then the repository closed at once: the queued drawing does nothing instead of throwing. */
    @Test
    @Timeout(60)
    public void closingTheRepositoryBeforeTheGraphIsDrawnThrowsNothing(@TempDir Path tmp) throws Exception {
        List<Throwable> thrown = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> thrown.add(e));
        try {
            EccoService service = repository(tmp.resolve("repo"));
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            DependencyGraphView view = onFx(() -> new DependencyGraphView(service));

            // hold the AWT thread so the drawing is still queued when the repository closes
            Object gate = new Object();
            synchronized (gate) {
                SwingUtilities.invokeLater(() -> {
                    synchronized (gate) {
                    }
                });
                onFx(() -> view.setTabVisible(true));
                onFx(() -> { }); // the runLater that queues the drawing
                service.close();
            }
            onFx(() -> { });
            CountDownLatch drawn = new CountDownLatch(1);
            SwingUtilities.invokeLater(drawn::countDown);
            assertTrue(drawn.await(20, TimeUnit.SECONDS), "the AWT thread is stuck");

            assertEquals(List.of(), thrown);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    /**
     * "Hide Implied Dependencies" hides a dependency that says nothing new: from (A and B, say a line)
     * needs to (A, its file) - wherever from is, to is. It checked the other way round.
     */
    @Test
    public void aDependencyIsImpliedWhenItsSourceImpliesItsTarget() {
        at.jku.isse.ecco.dao.EntityFactory ef = new at.jku.isse.ecco.storage.ser.dao.SerEntityFactory();
        at.jku.isse.ecco.repository.Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        at.jku.isse.ecco.feature.FeatureRevision a = repository.addFeature("a", "A").addRevision("1");
        at.jku.isse.ecco.feature.FeatureRevision b = repository.addFeature("b", "B").addRevision("1");
        at.jku.isse.ecco.module.Module moduleA = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{a.getFeature()}, new at.jku.isse.ecco.feature.Feature[0]);
        at.jku.isse.ecco.module.Module moduleAB = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{a.getFeature(), b.getFeature()}, new at.jku.isse.ecco.feature.Feature[0]);
        at.jku.isse.ecco.module.Condition onlyA = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        onlyA.addModuleRevision(moduleA.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{a}, new at.jku.isse.ecco.feature.Feature[0]));
        at.jku.isse.ecco.module.Condition aAndB = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        aAndB.addModuleRevision(moduleAB.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{a, b}, new at.jku.isse.ecco.feature.Feature[0]));

        assertTrue(DependencyGraphView.isImplied(aAndB, onlyA), "A and B needs A: wherever A and B is, A is - nothing new");
        org.junit.jupiter.api.Assertions.assertFalse(DependencyGraphView.isImplied(onlyA, aAndB), "A needs A and B: A can be without B - shown");
    }
}
