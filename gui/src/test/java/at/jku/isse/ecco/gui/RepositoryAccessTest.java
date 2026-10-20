package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read waits for the service's lock - a commit holding it - in the background, not on the FX
 * thread, which keeps running meanwhile; its result and a write's end are applied on the FX thread.
 */
public class RepositoryAccessTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    @Timeout(30)
    public void aReadWaitsForTheLockInTheBackgroundAndAppliesOnTheFxThread(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();

            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread commit = new Thread(() -> {
                synchronized (service) { // what a commit does for its whole run
                    locked.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                    }
                }
            });
            commit.start();
            assertTrue(locked.await(5, TimeUnit.SECONDS));

            AtomicBoolean readUnderLock = new AtomicBoolean();
            AtomicReference<Boolean> appliedOnFx = new AtomicReference<>();
            CountDownLatch applied = new CountDownLatch(1);
            Platform.runLater(() -> RepositoryAccess.read(service, "test read",
                    s -> {
                        readUnderLock.set(Thread.holdsLock(s) && !Platform.isFxApplicationThread());
                        return "result";
                    },
                    result -> {
                        appliedOnFx.set(Platform.isFxApplicationThread());
                        applied.countDown();
                    },
                    failure -> fail(failure)));

            // the FX thread is not waiting for the lock
            CountDownLatch fxRuns = new CountDownLatch(1);
            Platform.runLater(fxRuns::countDown);
            assertTrue(fxRuns.await(5, TimeUnit.SECONDS), "the FX thread kept running while the lock was held");
            assertEquals(1, applied.getCount(), "nothing read while the commit holds the lock");

            release.countDown();
            assertTrue(applied.await(5, TimeUnit.SECONDS));
            assertTrue(readUnderLock.get(), "read in the background, holding the lock");
            assertTrue(appliedOnFx.get(), "applied on the FX thread");
            commit.join(5_000);
        }
    }

    @Test
    @Timeout(30)
    public void aWriteRunsInTheBackgroundAndEndsOnTheFxThread() throws Exception {
        AtomicBoolean wroteOffFx = new AtomicBoolean();
        AtomicReference<Boolean> doneOnFx = new AtomicReference<>();
        AtomicReference<Throwable> failedWith = new AtomicReference<>();
        CountDownLatch ended = new CountDownLatch(2);
        Platform.runLater(() -> {
            RepositoryAccess.write("test write", () -> wroteOffFx.set(!Platform.isFxApplicationThread()),
                    () -> {
                        doneOnFx.set(Platform.isFxApplicationThread());
                        ended.countDown();
                    },
                    failure -> fail(failure));
            RepositoryAccess.write("failing write", () -> {
                throw new IllegalStateException("refused");
            }, () -> fail("not done"), failure -> {
                failedWith.set(failure);
                ended.countDown();
            });
        });
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertTrue(wroteOffFx.get());
        assertTrue(doneOnFx.get());
        assertEquals("refused", failedWith.get().getMessage());
    }
}
