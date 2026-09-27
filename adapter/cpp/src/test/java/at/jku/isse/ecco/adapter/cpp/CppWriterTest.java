package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.cpp.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CppWriter keeps its per-file output buffers (code/includes/fields/defines) in instance fields but
 * processed the input files with a parallelStream(), so concurrently written files appended into
 * the same buffers and ended up with each other's content (and the writer's own reset raced too).
 */
public class CppWriterTest {

    @Test
    public void eachFileGetsExactlyItsOwnContent() throws IOException {
        EntityFactory entityFactory = new SerEntityFactory();
        int fileCount = 64;
        Set<Node> input = new HashSet<>();
        for (int i = 0; i < fileCount; i++) {
            Node.Op fileNode = entityFactory.createOrderedNode(entityFactory.createArtifact(new PluginArtifactData("cpp", Paths.get("file" + i + ".cpp"))));
            for (int line = 0; line < 50; line++) {
                fileNode.addChild(entityFactory.createNode(entityFactory.createArtifact(new LineArtifactData("int f" + i + "_" + line + ";"))));
            }
            input.add(fileNode);
        }

        Path base = Files.createTempDirectory("cpp-writer");
        new CppWriter().write(base, input);

        for (int i = 0; i < fileCount; i++) {
            String content = Files.readString(base.resolve("file" + i + ".cpp"));
            for (int other = 0; other < fileCount; other++) {
                if (other == i) continue;
                assertEquals(-1, content.indexOf("int f" + other + "_"), "file" + i + ".cpp must not contain lines of file" + other + ".cpp");
            }
            for (int line = 0; line < 50; line++) {
                assertEquals(1, content.split("int f" + i + "_" + line + ";", -1).length - 1, "line " + line + " must appear exactly once in file" + i + ".cpp");
            }
        }
    }
}
