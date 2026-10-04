package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared "Minimize Presence Conditions" run: what it computes and stores, that a second run
 * while one is in progress is ignored, which results it keeps showing after the repository changed,
 * and how a failure or a closed repository ends a run.
 */
public class MinimizationResultsTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void aRunComputesStoresAndOnReopeningShowsTheMinimizedConditions(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Map<String, String> computed;
        try (EccoService service = minedRepository(tmp, new EccoService())) {
            MinimizationResults results = new MinimizationResults(service);
            assertTrue(service.validMinimizedConditions().isEmpty());

            runToTheEnd(results);

            computed = onFx(() -> Map.copyOf(results.getMinimizedByAssociationId()));
            Set<String> associations = service.getRepository().getAssociations().stream().map(Association::getId).collect(Collectors.toSet());
            assertEquals(associations, computed.keySet(), "one result per association");
            assertEquals(1.0, onFx(() -> results.progressProperty().get()));
            assertEquals(computed, service.validMinimizedConditions(), "stored with the repository");
            assertEquals(associations, service.validCheckoutConditions().keySet(), "and the ones checkout can use");
        }

        // a reopened repository shows them right away, without a new run
        try (EccoService service = new EccoService()) {
            MinimizationResults results = new MinimizationResults(service);
            service.setRepositoryDir(repo.resolve(".ecco"));
            service.open();
            waitUntil("seeded from the repository", () -> results.getMinimizedByAssociationId().equals(computed));
            assertFalse(onFx(() -> results.runningProperty().get()));
        }
    }

    @Test
    @Timeout(60)
    public void aCommitDropsTheResultsItInvalidated(@TempDir Path tmp) throws Exception {
        try (EccoService service = minedRepository(tmp, new EccoService())) {
            MinimizationResults results = new MinimizationResults(service);
            runToTheEnd(results);
            assertFalse(onFx(() -> results.getMinimizedByAssociationId().isEmpty()));

            // a new configuration is part of every result's basis
            commit(service, tmp.resolve("new"), "Base, B", Map.of("b.txt", "b\n"));
            assertTrue(service.validMinimizedConditions().isEmpty());
            waitUntil("the stale results are gone", () -> results.getMinimizedByAssociationId().isEmpty());
        }
    }

    @Test
    @Timeout(60)
    public void aSecondRunWhileOneIsInProgressIsIgnored(@TempDir Path tmp) throws Exception {
        BlockingService service = new BlockingService();
        try (EccoService ignored = minedRepository(tmp, service)) {
            MinimizationResults results = new MinimizationResults(service);
            onFx(results::run);
            waitUntil("the first run started", () -> service.started.get() == 1);
            assertTrue(onFx(() -> results.runningProperty().get()));

            onFx(results::run);
            Thread.sleep(200);
            assertEquals(1, service.started.get(), "no second run");

            service.release.countDown();
            waitUntil("the run finished", () -> !results.runningProperty().get());
            onFx(results::run);
            waitUntil("a finished run does not hold up the next", () -> service.started.get() == 2);
            waitUntil("the second run finished", () -> !results.runningProperty().get());
        }
    }

    @Test
    @Timeout(60)
    public void aRunOnAClosedRepositoryDoesNothing(@TempDir Path tmp) throws Exception {
        BlockingService service = new BlockingService();
        MinimizationResults results = new MinimizationResults(service);
        onFx(results::run);
        assertFalse(onFx(() -> results.runningProperty().get()));
        assertEquals(0, service.started.get());
    }

    @Test
    @Timeout(60)
    public void aFailedRunIsReportedAndEnds(@TempDir Path tmp) throws Exception {
        BlockingService service = new BlockingService();
        service.failure = new IllegalStateException("no fingerprints today");
        try (EccoService ignored = minedRepository(tmp, service)) {
            MinimizationResults results = new MinimizationResults(service);
            onFx(results::run);
            waitUntil("the run ended", () -> !results.runningProperty().get());

            List<String> shown = onFx(MinimizationResultsTest::closeErrorDialogs);
            assertEquals(List.of("no fingerprints today"), shown);
            assertTrue(onFx(() -> results.getMinimizedByAssociationId().isEmpty()));
        }
    }

    @Test
    @Timeout(60)
    public void closingTheRepositoryStopsARunQuietlyAndClearsTheResults(@TempDir Path tmp) throws Exception {
        BlockingService service = new BlockingService();
        try (EccoService ignored = minedRepository(tmp, service)) {
            MinimizationResults results = new MinimizationResults(service);
            onFx(() -> results.getMinimizedByAssociationId().put("stale", "A"));
            onFx(results::run);
            waitUntil("the run started", () -> service.started.get() == 1);

            service.close();
            waitUntil("the run ended", () -> !results.runningProperty().get());
            assertTrue(onFx(() -> results.getMinimizedByAssociationId().isEmpty()));
            assertEquals(List.of(), onFx(MinimizationResultsTest::closeErrorDialogs), "a cancelled run is no error");
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /**
     * Counts the runs (each starts by taking the fingerprints), can hold a run there until released,
     * or fail it.
     */
    private static final class BlockingService extends EccoService {
        final AtomicInteger started = new AtomicInteger();
        final CountDownLatch release = new CountDownLatch(1);
        volatile RuntimeException failure;

        @Override
        public Map<String, String> minimizationBases() {
            started.incrementAndGet();
            if (failure != null)
                throw failure;
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return super.minimizationBases();
        }
    }

    /** Five configurations with MANDATORY Base accepted, so the run has a constraint to use. */
    private static <S extends EccoService> S minedRepository(Path tmp, S service) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("repo"));
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        String[] configurations = {"Base, A, B", "Base, A, B", "Base, A, B", "Base, A", "Base, C"};
        for (int i = 0; i < configurations.length; i++)
            commit(service, tmp.resolve("v" + i), configurations[i], Map.of("f" + i + ".txt", i + "\n"));
        service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Base", null);
        return service;
    }

    private static void runToTheEnd(MinimizationResults results) throws Exception {
        onFx(results::run);
        waitUntil("the run finished", () -> !results.runningProperty().get());
    }

    /** Closes the error dialogs shown (non-modal), returning their messages. */
    private static List<String> closeErrorDialogs() {
        List<String> messages = new java.util.ArrayList<>();
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane) {
                messages.add(pane.getContentText());
                window.hide();
            }
        }
        return messages;
    }
}
