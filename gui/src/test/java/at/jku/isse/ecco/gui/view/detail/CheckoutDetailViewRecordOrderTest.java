package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reorder... on an ORDER warning used to commit the whole checkout directory under the checkout's
 * configuration (Known gap #23). It now records the order in the repository without a commit, rewrites
 * the checked-out file and drops the resolved row.
 */
public class CheckoutDetailViewRecordOrderTest {

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
    @Timeout(60)
    public void reorderRecordsTheOrderWithoutACommit(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(Files.createDirectories(tmp.resolve("repo")).resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "a\nb\n", "BASE, A");
            commit(service, tmp.resolve("v2"), "a\nc\n", "BASE, B");
            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            Checkout checkout = service.checkout("BASE, A, B");
            assertEquals("a\nc\nb\n", Files.readString(out.resolve("f.txt")));

            CheckoutDetailView view = onFx(() -> {
                CheckoutDetailView v = new CheckoutDetailView(service);
                v.showCheckout(checkout, out);
                return v;
            });
            Node.Op file = onFx(() -> (Node.Op) view.warningsData.stream().filter(i -> "ORDER".equals(i.getType())).findFirst().orElseThrow().getAmbiguousNode());
            List<Node.Op> order = List.of(child(file, "a"), child(file, "b"), child(file, "c"));

            onFx(() -> {
                view.recordOrder(file, order);
                return null;
            });
            long deadline = System.currentTimeMillis() + 30_000;
            while (onFx(() -> view.warningsData.stream().anyMatch(i -> "ORDER".equals(i.getType()))) && System.currentTimeMillis() < deadline)
                Thread.sleep(50);

            assertFalse(onFx(() -> view.warningsData.stream().anyMatch(i -> "ORDER".equals(i.getType()))), "the resolved row is gone");
            assertTrue(onFx(() -> view.warningsData.stream().anyMatch(i -> "MISSING".equals(i.getType()))), "the other warnings stay");
            assertEquals("a\nb\nc\n", Files.readString(out.resolve("f.txt")));
            assertEquals(2, service.getCommits().size(), "no commit");
        }
    }

    private static Node.Op child(Node.Op parent, String text) {
        return parent.getChildren().stream().filter(c -> String.valueOf(c.getArtifact()).equals(text)).findFirst().orElseThrow();
    }

    private static void commit(EccoService service, Path dir, String f, String config) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), f);
        service.setBaseDir(dir);
        service.commit("m", config);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }
}
