package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LatestRefresh}: one refresh at a time, asks meanwhile merged into one more; hidden, noted
 * and run when shown; read holding the service's lock; nothing applied after the repository closed.
 */
public class LatestRefreshTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void asksWhileOneRunsAreOneMore(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger computed = new AtomicInteger();
            List<Integer> applied = new CopyOnWriteArrayList<>();
            LatestRefresh<Integer> refresh = new LatestRefresh<>("test", service, s -> {
                int n = computed.incrementAndGet();
                if (n == 1) {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                return n;
            }, applied::add, t -> fail(t));

            refresh.request();
            waitUntil("the first runs", () -> computed.get() == 1);
            for (int i = 0; i < 20; i++)
                refresh.request(); // e.g. twenty commits meanwhile
            release.countDown();
            waitUntil("done", () -> !refresh.busy());
            onFx(() -> { });
            assertEquals(2, computed.get(), "the first, and one more for all asked meanwhile");
            assertEquals(List.of(2), applied, "the first was overtaken: only the latest is shown");
        }
    }

    @Test
    @Timeout(60)
    public void hiddenItIsNotedAndRunWhenShown(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            AtomicInteger computed = new AtomicInteger();
            LatestRefresh<Integer> refresh = new LatestRefresh<>("test", service, s -> computed.incrementAndGet(), n -> { }, t -> fail(t));
            refresh.setVisible(false);
            refresh.request();
            refresh.request();
            Thread.sleep(200);
            assertEquals(0, computed.get(), "nothing while hidden");
            refresh.setVisible(true);
            waitUntil("run when shown", () -> computed.get() == 1);
            Thread.sleep(200);
            assertEquals(1, computed.get(), "once");
        }
    }

    /** It reads holding the service's lock: a commit, which holds it, is not read half done. */
    @Test
    @Timeout(60)
    public void itReadsHoldingTheServicesLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            List<Boolean> held = new CopyOnWriteArrayList<>();
            LatestRefresh<Boolean> refresh = new LatestRefresh<>("test", service, s -> Thread.holdsLock(s), held::add, t -> fail(t));
            refresh.request();
            waitUntil("applied", () -> !held.isEmpty());
            assertEquals(List.of(true), held);
        }
    }

    @Test
    @Timeout(60)
    public void nothingIsAppliedAfterTheRepositoryClosed(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            CountDownLatch release = new CountDownLatch(1);
            List<Integer> applied = new CopyOnWriteArrayList<>();
            LatestRefresh<Integer> refresh = new LatestRefresh<>("test", service, s -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return 1;
            }, applied::add, t -> fail(t));
            refresh.request();
            Thread.sleep(100);
            refresh.clear();
            release.countDown();
            waitUntil("done", () -> !refresh.busy());
            onFx(() -> { });
            assertEquals(List.of(), applied);
        }
    }
}
