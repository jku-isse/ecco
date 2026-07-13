package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drives a REAL {@link VariantsView} (constructed on a real, running JavaFX Application Thread,
 * listening to a real {@link EccoService}) through a real commit, to directly observe whether its
 * {@code variantsDataSelected} list actually gets populated with the auto-created variant --
 * end-to-end, not reasoned about statically.
 */
public class VariantsViewCommitRefreshTest {

    private static volatile boolean fxStarted = false;

    private static void ensureFxStarted() throws InterruptedException {
        if (fxStarted) return;
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        latch.await();
        fxStarted = true;
    }

    @Test
    @Timeout(30)
    public void commitTriggersVariantsViewRefresh() throws Exception {
        ensureFxStarted();

        Path workDir = Files.createTempDirectory("variantsview-commit-refresh");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            CountDownLatch viewCreated = new CountDownLatch(1);
            VariantsView[] viewHolder = new VariantsView[1];
            Platform.runLater(() -> {
                viewHolder[0] = new VariantsView(service);
                viewCreated.countDown();
            });
            viewCreated.await();
            VariantsView view = viewHolder[0];

            Path p = workDir.resolve("core");
            Files.createDirectories(p);
            Files.writeString(p.resolve("core.txt"), "core\n");
            service.setBaseDir(p);
            service.commit("first commit", "Core");

            // wait for the async chain to settle: statusChangedEvent -> Platform.runLater ->
            // refresh() -> background Task -> setOnSucceeded -> variantsDataSelected populated.
            long deadline = System.currentTimeMillis() + 10000;
            int size = -1;
            while (System.currentTimeMillis() < deadline) {
                CountDownLatch checked = new CountDownLatch(1);
                int[] countHolder = new int[1];
                Platform.runLater(() -> {
                    countHolder[0] = view.variantsDataSelected.size();
                    checked.countDown();
                });
                checked.await();
                size = countHolder[0];
                if (size >= 1) break;
                Thread.sleep(200);
            }

            System.out.println("VariantsView.variantsDataSelected.size() after commit = " + size);
            assertEquals(1, size, "VariantsView's table data should show the auto-created variant after commit");
        }
    }

    /**
     * Closer to real usage: repository already has one variant (from an earlier commit) before the
     * table is ever shown, then a SECOND, real GUI-shaped commit (on its own background Thread, like
     * CommitView's Task) with a genuinely different configuration happens while the view is live --
     * does the newly-added second variant also show up alongside the pre-existing one?
     */
    @Test
    @Timeout(30)
    public void secondCommitWithNewConfigAlsoAppearsAlongsideExisting() throws Exception {
        ensureFxStarted();

        Path workDir = Files.createTempDirectory("variantsview-commit-refresh-2");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            Path core = workDir.resolve("core");
            Files.createDirectories(core);
            Files.writeString(core.resolve("core.txt"), "core\n");
            service.setBaseDir(core);
            service.commit("first commit", "Core");

            CountDownLatch viewCreated = new CountDownLatch(1);
            VariantsView[] viewHolder = new VariantsView[1];
            Platform.runLater(() -> {
                viewHolder[0] = new VariantsView(service);
                viewCreated.countDown();
            });
            viewCreated.await();
            VariantsView view = viewHolder[0];

            // wait for the view's initial load (from construction) to settle at 1
            waitForSize(view, 1, 10000);

            Path extra = workDir.resolve("extra");
            Files.createDirectories(extra);
            Files.writeString(extra.resolve("extra.txt"), "extra\n");
            service.setBaseDir(extra);

            // commit on its own background thread, exactly like CommitView's Task<Commit> does --
            // not the test thread directly.
            Thread commitThread = new Thread(() -> service.commit("second commit", "Extra"));
            commitThread.start();
            commitThread.join(10000);

            int size = waitForSize(view, 2, 10000);
            System.out.println("VariantsView.variantsDataSelected.size() after second commit = " + size);
            assertEquals(2, size, "both the pre-existing and the newly-committed variant should be visible");
        }
    }

    private static int waitForSize(VariantsView view, int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        int size = -1;
        while (System.currentTimeMillis() < deadline) {
            CountDownLatch checked = new CountDownLatch(1);
            int[] countHolder = new int[1];
            Platform.runLater(() -> {
                countHolder[0] = view.variantsDataSelected.size();
                checked.countDown();
            });
            checked.await();
            size = countHolder[0];
            if (size >= expected) break;
            Thread.sleep(200);
        }
        return size;
    }
}
