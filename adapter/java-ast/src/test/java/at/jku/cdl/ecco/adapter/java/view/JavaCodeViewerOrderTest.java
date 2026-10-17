package at.jku.cdl.ecco.adapter.java.view;

import at.jku.cdl.ecco.adapter.java.JavaASTReader;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The code view shows a file's parts in the order a checkout writes them: package, imports, types.
 * They are unordered children of the file's node, which a composition of several associations gives
 * in the order it took them - a composed file was shown with its package after its class.
 */
public class JavaCodeViewerOrderTest {

    @Test
    public void packageAndImportsComeFirstWhateverTheOrderOfTheFilesParts() throws Exception {
        Path base = Files.createTempDirectory("java-viewer-order");
        Files.writeString(base.resolve("C.java"), "package p;\n\nimport java.util.List;\nimport java.util.Map;\n\nclass C {\n}\n\nclass D {\n}\n");
        Node file = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("C.java")}).iterator().next();

        List<Node> parts = new ArrayList<>(file.getChildren());
        Collections.reverse(parts); // as a composition may give them: the types first
        List<String> shown = JavaCodeViewer.inWrittenOrder(parts).stream().map(n -> String.valueOf(n.getArtifact().getData()).strip()).toList();

        assertEquals("p", shown.get(0), shown.toString());
        assertEquals(List.of("import java.util.List;", "import java.util.Map;"), List.copyOf(new java.util.TreeSet<>(shown.subList(1, 3))), shown.toString());
        assertEquals(5, shown.size(), shown.toString());
    }

    /**
     * And so it is shown: the file's parts in the order a checkout writes them, through showTree -
     * rendering only the file shown lost it (package and imports came after the class again), and
     * the test of the order alone stayed green.
     */
    @Test
    public void theViewShowsPackageAndImportsFirst() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        try {
            javafx.application.Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await();
        Path base = Files.createTempDirectory("java-viewer-order-shown");
        Files.writeString(base.resolve("C.java"), "package p;\n\nimport java.util.List;\n\nclass C {\n}\n");
        Node.Op file = (Node.Op) new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("C.java")}).iterator().next();
        List<Node.Op> parts = new ArrayList<>(file.getChildren());
        Collections.reverse(parts); // as a composition may give them: the class first
        file.setChildren(parts);

        java.util.concurrent.CompletableFuture<List<String>> shown = new java.util.concurrent.CompletableFuture<>();
        javafx.application.Platform.runLater(() -> {
            try {
                JavaCodeViewer viewer = new JavaCodeViewer();
                viewer.showTree(file);
                javafx.scene.control.ListView<?> list = (javafx.scene.control.ListView<?>) ((javafx.scene.control.SplitPane) viewer.getCenter()).getItems().get(0);
                shown.complete(list.getItems().stream().map(line -> ((JavaCodeLine) line).getText().strip()).filter(t -> !t.isEmpty()).toList());
            } catch (Throwable t) {
                shown.completeExceptionally(t);
            }
        });
        List<String> lines = shown.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(lines.get(0).startsWith("package p"), lines.toString());
        assertTrue(lines.get(1).startsWith("import java.util.List"), lines.toString());
    }
}
