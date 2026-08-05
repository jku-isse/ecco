package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FileWriterTest {

    private final FileReader reader = new FileReader(new SerEntityFactory());
    private final FileWriter writer = new FileWriter();

    @Test
    public void writeRoundTripsFileContentBackToDisk() throws IOException {
        Path baseDir = Files.createTempDirectory("file-writer-roundtrip");
        Files.writeString(baseDir.resolve("a.bin"), "hello");
        Set<Node> read = Set.copyOf(reader.read(baseDir, new Path[]{Path.of("a.bin")}));

        Path outputDir = Files.createTempDirectory("file-writer-roundtrip-out");
        Path[] written = writer.write(outputDir, read);

        assertEquals(1, written.length);
        assertEquals("hello", Files.readString(written[0]));
    }

    @Test
    public void writeThrowsWhenThePluginNodeHasNoChildren() throws IOException {
        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(FilePlugin.class.getName(), Path.of("empty.bin"))));

        Path outputDir = Files.createTempDirectory("file-writer-no-children");

        assertThrows(EccoException.class, () -> writer.write(outputDir, Set.<Node>of(pluginNode)));
    }

    /**
     * Regression test for a fixed bug: FileWriter.write() used to only write a child's data when the
     * plugin node had EXACTLY one child (FileWriter.java: {@code if (node.getChildren().size() != 1)})
     * - two children, both carrying real data, still hit the "write empty file" branch, silently
     * discarding both. Since adapter-file is the catch-all handler for any file no more specific
     * adapter claims, and checkout/compose can merge content from multiple associations into one tree,
     * this wasn't purely a malformed-hand-built-tree scenario. Fixed to throw instead of silently
     * losing data.
     */
    @Test
    public void writeThrowsWhenThePluginNodeHasMoreThanOneChild() throws IOException {
        Path baseDir = Files.createTempDirectory("file-writer-multi-children");
        Files.writeString(baseDir.resolve("a.bin"), "hello");
        Files.writeString(baseDir.resolve("b.bin"), "world");

        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(FilePlugin.class.getName(), Path.of("out.bin"))));
        pluginNode.addChild(ef.createNode(ef.createArtifact(new FileArtifactData(baseDir, Path.of("a.bin")))));
        pluginNode.addChild(ef.createNode(ef.createArtifact(new FileArtifactData(baseDir, Path.of("b.bin")))));

        Path outputDir = Files.createTempDirectory("file-writer-multi-children-out");

        assertThrows(EccoException.class, () -> writer.write(outputDir, Set.<Node>of(pluginNode)));
    }

    @Test
    public void getPluginIdIsTheFilePluginClassName() {
        assertEquals(FilePlugin.class.getName(), writer.getPluginId());
    }
}
