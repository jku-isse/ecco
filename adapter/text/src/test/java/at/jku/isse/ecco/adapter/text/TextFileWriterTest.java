package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TextFileWriterTest {

    private final TextReader reader = new TextReader(new SerEntityFactory());
    private final TextFileWriter writer = new TextFileWriter();

    @Test
    public void writeRoundTripsReadContentBackToDisk() throws IOException {
        Path baseDir = Files.createTempDirectory("text-writer-roundtrip");
        Files.writeString(baseDir.resolve("a.txt"), "first\nsecond\nthird\n");
        Set<Node> read = Set.copyOf(reader.read(baseDir, new Path[]{Path.of("a.txt")}));

        Path outputDir = Files.createTempDirectory("text-writer-roundtrip-out");
        Path[] written = writer.write(outputDir, read);

        assertEquals(1, written.length);
        assertEquals("first\nsecond\nthird\n", Files.readString(written[0]));
    }

    @Test
    public void writeThrowsForANodeWithoutPluginArtifactData() {
        Node.Op notAFileNode = new SerEntityFactory().createNode(new LineArtifactData("not a file"));

        assertThrows(EccoException.class, () -> writer.write(Path.of("."), Set.of(notAFileNode)));
    }

    @Test
    public void getPluginIdIsTheTextPluginClassName() {
        assertEquals(TextPlugin.class.getName(), writer.getPluginId());
    }
}
