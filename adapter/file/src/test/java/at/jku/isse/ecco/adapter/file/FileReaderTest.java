package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FileReaderTest {

    private final FileReader reader = new FileReader(new SerEntityFactory());

    @Test
    public void readProducesOnePluginNodeWithOneFileChild() throws IOException {
        Path baseDir = Files.createTempDirectory("file-reader");
        Files.writeString(baseDir.resolve("a.bin"), "hello");

        Set<Node.Op> result = reader.read(baseDir, new Path[]{Path.of("a.bin")});

        assertEquals(1, result.size());
        Node.Op pluginNode = result.iterator().next();
        assertInstanceOf(PluginArtifactData.class, pluginNode.getArtifact().getData());
        assertEquals(1, pluginNode.getChildren().size());

        FileArtifactData fileData = (FileArtifactData) pluginNode.getChildren().get(0).getArtifact().getData();
        assertArrayEquals("hello".getBytes(), fileData.getData());
        assertEquals(Path.of("a.bin"), fileData.getPath());
    }

    @Test
    public void readComputesASHA1ChecksumMatchingTheFileContent() throws IOException, NoSuchAlgorithmException {
        Path baseDir = Files.createTempDirectory("file-reader-checksum");
        Files.writeString(baseDir.resolve("a.bin"), "hello");

        Node.Op pluginNode = reader.read(baseDir, new Path[]{Path.of("a.bin")}).iterator().next();
        FileArtifactData fileData = (FileArtifactData) pluginNode.getChildren().get(0).getArtifact().getData();

        byte[] expectedChecksum = MessageDigest.getInstance("SHA1").digest("hello".getBytes());
        assertArrayEquals(expectedChecksum, fileData.getChecksum());
    }

    @Test
    public void twoFileArtifactDataWithEqualContentAreEqualAndHaveTheSameHashCode() throws IOException {
        Path baseDir = Files.createTempDirectory("file-reader-equals");
        Files.writeString(baseDir.resolve("a.bin"), "same content");
        Files.writeString(baseDir.resolve("b.bin"), "same content");

        FileArtifactData a = new FileArtifactData(baseDir, Path.of("a.bin"));
        FileArtifactData b = new FileArtifactData(baseDir, Path.of("b.bin"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void twoFileArtifactDataWithDifferentContentAreNotEqual() throws IOException {
        Path baseDir = Files.createTempDirectory("file-reader-not-equals");
        Files.writeString(baseDir.resolve("a.bin"), "content A");
        Files.writeString(baseDir.resolve("b.bin"), "content B");

        FileArtifactData a = new FileArtifactData(baseDir, Path.of("a.bin"));
        FileArtifactData b = new FileArtifactData(baseDir, Path.of("b.bin"));

        assertNotEquals(a, b);
    }

    @Test
    public void getPluginIdIsTheFilePluginClassName() {
        assertEquals(FilePlugin.class.getName(), reader.getPluginId());
    }

    @Test
    public void getPrioritizedPatternsMatchesEverythingAtTheLowestPriority() {
        assertArrayEquals(new String[]{"**"}, reader.getPrioritizedPatterns().get(0));
    }
}
