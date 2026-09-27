package at.jku.isse.ecco.adapter.runtime;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.runtime.data.ClassArtifactData;
import at.jku.isse.ecco.adapter.runtime.data.LineArtifactData;
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
 * Same bug as CppWriterTest: RuntimeWriter keeps its per-file output buffers in instance fields but
 * wrote the input files with a parallelStream(), so concurrently written files got each other's
 * content.
 */
public class RuntimeWriterTest {

    @Test
    public void eachFileGetsExactlyItsOwnContent() throws IOException {
        EntityFactory entityFactory = new SerEntityFactory();
        int fileCount = 64;
        Set<Node> input = new HashSet<>();
        for (int i = 0; i < fileCount; i++) {
            Node.Op fileNode = entityFactory.createOrderedNode(entityFactory.createArtifact(new PluginArtifactData("runtime", Paths.get("C" + i + ".java"))));
            Node.Op classNode = entityFactory.createOrderedNode(entityFactory.createArtifact(new ClassArtifactData("p.C" + i, "public class C" + i + " {")));
            for (int line = 0; line < 50; line++) {
                classNode.addChild(entityFactory.createNode(entityFactory.createArtifact(new LineArtifactData("int f" + i + "_" + line + ";"))));
            }
            fileNode.addChild(classNode);
            input.add(fileNode);
        }

        Path base = Files.createTempDirectory("runtime-writer");
        new RuntimeWriter().write(base, input);

        for (int i = 0; i < fileCount; i++) {
            String content = Files.readString(base.resolve("C" + i + ".java"));
            for (int other = 0; other < fileCount; other++) {
                if (other == i) continue;
                assertEquals(-1, content.indexOf("int f" + other + "_"), "C" + i + ".java must not contain lines of C" + other + ".java");
            }
            for (int line = 0; line < 50; line++) {
                assertEquals(1, content.split("int f" + i + "_" + line + ";", -1).length - 1, "line " + line + " must appear exactly once in C" + i + ".java");
            }
        }
    }
}
