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
}
