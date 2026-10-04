package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helpers for headless view tests: views are built and driven on the FX thread without a window, so
 * a test checks the decisions a view makes (what it commits, refuses, lists, enables), not its
 * layout. Never drive a path that opens a modal dialog (Alert.showAndWait and the like): it blocks
 * the FX thread and the test hangs; test the decision behind it instead.
 */
public final class FxTestSupport {

    private FxTestSupport() {
    }

    /** Starts the JavaFX toolkit once per JVM; call from a @BeforeAll. */
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

    /**
     * Runs {@code action} on the FX thread and returns its result. Everything queued on the FX thread
     * before it (e.g. a view's Platform.runLater calls) has run by then.
     */
    public static <T> T onFx(Callable<T> action) throws Exception {
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
        assertTrue(done.await(10, TimeUnit.SECONDS), "the FX thread did not get to the action within 10 s (a modal dialog?)");
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    public static void onFx(ThrowingRunnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Waits until {@code condition}, checked on the FX thread, holds - for work a view does in a background Task. */
    public static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(onFx(condition)))
                return;
            Thread.sleep(50);
        }
        fail("timed out waiting for: " + what);
    }

    /**
     * The first node below {@code root} (itself included) matching {@code match}, also inside the
     * content of TitledPanes, ScrollPanes, Tabs, SplitPanes and ToolBars, which are no children until
     * a skin exists.
     */
    public static Node find(Node root, Predicate<Node> match) {
        List<Node> all = new ArrayList<>();
        collect(root, all);
        return all.stream().filter(match).findFirst().orElse(null);
    }

    public static List<Node> findAll(Node root, Predicate<Node> match) {
        List<Node> all = new ArrayList<>();
        collect(root, all);
        return all.stream().filter(match).toList();
    }

    private static void collect(Node node, List<Node> all) {
        if (node == null)
            return;
        all.add(node);
        if (node instanceof TitledPane pane)
            collect(pane.getContent(), all);
        if (node instanceof ScrollPane pane)
            collect(pane.getContent(), all);
        if (node instanceof TabPane pane)
            for (Tab tab : pane.getTabs())
                collect(tab.getContent(), all);
        if (node instanceof SplitPane pane)
            for (Node item : pane.getItems())
                collect(item, all);
        if (node instanceof ToolBar bar)
            for (Node item : bar.getItems())
                collect(item, all);
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable())
                collect(child, all);
    }

    public static Button button(Node root, String text) {
        Node button = find(root, n -> n instanceof Button b && text.equals(b.getText()));
        if (button == null)
            fail("no button '" + text + "'");
        return (Button) button;
    }

    /** A new repository in {@code dir}/.ecco, open, with no commits. */
    public static EccoService repository(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        return service;
    }

    /** Commits {@code files} (relative path -> content), written to a fresh folder {@code dir}, as {@code configuration}. */
    public static void commit(EccoService service, Path dir, String configuration, Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        service.setBaseDir(dir);
        service.commit("commit " + configuration, configuration);
    }
}
