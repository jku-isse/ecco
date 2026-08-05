package at.jku.isse.ecco.adapter.file;

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
    public void writeProducesAnEmptyFileWhenThePluginNodeHasNoChildren() throws IOException {
        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(FilePlugin.class.getName(), Path.of("empty.bin"))));

        Path outputDir = Files.createTempDirectory("file-writer-no-children");
        Path[] written = writer.write(outputDir, Set.<Node>of(pluginNode));

        assertEquals(0, Files.size(written[0]));
    }

    /**
     * FileWriter.write() only writes a child's data when the plugin node has EXACTLY one child
     * (FileWriter.java: {@code if (node.getChildren().size() != 1)}) - two children, both carrying
     * real data, still hits the "write empty file" branch, silently discarding both. Characterizing
     * this as-is: it looks like it could only arise from a malformed/hand-built tree in practice
     * (FileReader.read() itself always produces exactly one file child per plugin node), but the
     * behavior itself - real data going in, an empty file coming out, no error - is surprising enough
     * to pin down rather than leave undocumented.
     */
    @Test
    public void writeProducesAnEmptyFileWhenThePluginNodeHasMoreThanOneChild() throws IOException {
        Path baseDir = Files.createTempDirectory("file-writer-multi-children");
        Files.writeString(baseDir.resolve("a.bin"), "hello");
        Files.writeString(baseDir.resolve("b.bin"), "world");

        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(FilePlugin.class.getName(), Path.of("out.bin"))));
        pluginNode.addChild(ef.createNode(ef.createArtifact(new FileArtifactData(baseDir, Path.of("a.bin")))));
        pluginNode.addChild(ef.createNode(ef.createArtifact(new FileArtifactData(baseDir, Path.of("b.bin")))));

        Path outputDir = Files.createTempDirectory("file-writer-multi-children-out");
        Path[] written = writer.write(outputDir, Set.<Node>of(pluginNode));

        assertEquals(0, Files.size(written[0]), "more than one child hits the same 'write empty file' branch as zero children");
    }

    @Test
    public void getPluginIdIsTheFilePluginClassName() {
        assertEquals(FilePlugin.class.getName(), writer.getPluginId());
    }
}
