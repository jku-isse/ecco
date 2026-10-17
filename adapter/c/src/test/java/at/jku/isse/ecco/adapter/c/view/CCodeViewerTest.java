package at.jku.isse.ecco.adapter.c.view;

import at.jku.isse.ecco.adapter.c.CReader;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The C viewer shows each file of the tree in a tab of its own, the one with the node shown
 * selected. Only that file is rendered right away, the others when their tab is first selected: it
 * rendered every file - for a commit of tinycc hundreds of files and 57,000 lines, on every click.
 */
public class CCodeViewerTest {

    @BeforeAll
    public static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
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
        assertTrue(done.await(10, TimeUnit.SECONDS), "the FX thread did not get to the action within 10 s");
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    /** Three C files read into one tree, under a common root as in a commit's composition. */
    private static List<Node> files(Path dir) throws Exception {
        Files.writeString(dir.resolve("a.c"), "int a(void) {\n\treturn 1;\n}\n");
        Files.writeString(dir.resolve("b.c"), "int b(void) {\n\treturn 2;\n}\n");
        Files.writeString(dir.resolve("c.c"), "int c(void) {\n\treturn 3;\n}\n");
        SerEntityFactory factory = new SerEntityFactory();
        Node.Op root = factory.createRootNode();
        List<Node> files = new ArrayList<>();
        for (Node.Op file : new CReader(factory).read(dir, new Path[]{Path.of("a.c"), Path.of("b.c"), Path.of("c.c")})) {
            root.addChild(file);
            files.add(file);
        }
        return files;
    }

    private static TabPane tabs(CCodeViewer viewer) {
        return (TabPane) ((SplitPane) viewer.getCenter()).getItems().get(0);
    }

    private static Tab tab(TabPane tabs, String name) {
        return tabs.getTabs().stream().filter(t -> t.getText().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> lines(Tab tab) {
        return ((ListView<?>) tab.getContent()).getItems().stream().map(line -> ((CCodeLine) line).getText()).toList();
    }

    @Test
    public void onlyTheFileShownIsRenderedTheOthersWhenSelected(@TempDir Path dir) throws Exception {
        List<Node> files = files(dir);
        Node inB = files.stream().filter(f -> f.getArtifact().getData().toString().contains("b.c")).findFirst().orElseThrow().getChildren().get(0);
        CCodeViewer viewer = onFx(() -> {
            CCodeViewer v = new CCodeViewer();
            new Scene(v, 400, 300);
            v.showTree(inB);
            return v;
        });
        TabPane tabs = tabs(viewer);
        assertEquals(List.of("a.c", "b.c", "c.c"), onFx(() -> tabs.getTabs().stream().map(Tab::getText).sorted().toList()));
        Tab a = tab(tabs, "a.c"), b = tab(tabs, "b.c"), c = tab(tabs, "c.c");
        assertSame(b, onFx(() -> tabs.getSelectionModel().getSelectedItem()), "the file of the node shown");
        assertTrue(onFx(() -> lines(b)).contains("\treturn 2;"), onFx(() -> lines(b)).toString());
        assertNull(onFx(a::getContent), "a.c not rendered yet");
        assertNull(onFx(c::getContent), "c.c not rendered yet");

        onFx(() -> {
            tabs.getSelectionModel().select(c);
            return null;
        });
        assertTrue(onFx(() -> lines(c)).contains("\treturn 3;"), "rendered when selected");
        assertNull(onFx(a::getContent), "a.c still not");
    }
}
