package at.jku.isse.ecco.gui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ToolBar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toolbar tasks re-enabled their toolbar only at the end of call(), so a failing task left it
 * disabled until restart. See TaskFailures.
 */
public class TaskFailuresTest {

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
    public void aFailingTaskReEnablesTheToolbar() throws Exception {
        ToolBar toolBar = new ToolBar();
        toolBar.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                throw new IllegalStateException("boom");
            }
        };
        TaskFailures.reportAndReenable(task, toolBar);
        new Thread(task).start();

        AtomicBoolean disabled = new AtomicBoolean(true);
        long deadline = System.currentTimeMillis() + 10_000;
        while (disabled.get() && System.currentTimeMillis() < deadline) {
            CountDownLatch checked = new CountDownLatch(1);
            Platform.runLater(() -> {
                disabled.set(toolBar.isDisable());
                checked.countDown();
            });
            checked.await(5, TimeUnit.SECONDS);
            Thread.sleep(50);
        }
        assertFalse(disabled.get(), "the toolbar must be re-enabled after the task failed");
    }
}
