package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TextReaderTest {

    private final TextReader reader = new TextReader(new SerEntityFactory());

    @Test
    public void readProducesOnePluginNodeWithOneChildPerLine() throws IOException {
        Path baseDir = Files.createTempDirectory("text-reader");
        Path file = baseDir.resolve("a.txt");
        Files.writeString(file, "first\nsecond\nthird\n");

        Set<Node.Op> result = reader.read(baseDir, new Path[]{Path.of("a.txt")});

        assertEquals(1, result.size());
        Node.Op fileNode = result.iterator().next();
        assertInstanceOf(PluginArtifactData.class, fileNode.getArtifact().getData());
        assertEquals(TextPlugin.class.getName(), ((PluginArtifactData) fileNode.getArtifact().getData()).getPluginId());

        List<? extends Node.Op> lines = fileNode.getChildren();
        assertEquals(3, lines.size());
        assertEquals("first", ((LineArtifactData) lines.get(0).getArtifact().getData()).getLine());
        assertEquals("second", ((LineArtifactData) lines.get(1).getArtifact().getData()).getLine());
        assertEquals("third", ((LineArtifactData) lines.get(2).getArtifact().getData()).getLine());
    }

    @Test
    public void readAssignsSequentialOneBasedLineNumberProperties() throws IOException {
        Path baseDir = Files.createTempDirectory("text-reader-line-numbers");
        Path file = baseDir.resolve("a.txt");
        Files.writeString(file, "first\nsecond\n");

        Node.Op fileNode = reader.read(baseDir, new Path[]{Path.of("a.txt")}).iterator().next();

        List<? extends Node.Op> lines = fileNode.getChildren();
        assertEquals(1, lines.get(0).<Integer>getProperty(TextReader.PROPERTY_LINE_START).orElseThrow());
        assertEquals(1, lines.get(0).<Integer>getProperty(TextReader.PROPERTY_LINE_END).orElseThrow());
        assertEquals(2, lines.get(1).<Integer>getProperty(TextReader.PROPERTY_LINE_START).orElseThrow());
    }

    @Test
    public void readOfMultipleFilesProducesOneNodePerFile() throws IOException {
        Path baseDir = Files.createTempDirectory("text-reader-multi");
        Files.writeString(baseDir.resolve("a.txt"), "a1\n");
        Files.writeString(baseDir.resolve("b.txt"), "b1\nb2\n");

        Set<Node.Op> result = reader.read(baseDir, new Path[]{Path.of("a.txt"), Path.of("b.txt")});

        assertEquals(2, result.size());
    }

    @Test
    public void readOfAnEmptyFileProducesAPluginNodeWithNoChildren() throws IOException {
        Path baseDir = Files.createTempDirectory("text-reader-empty");
        Files.writeString(baseDir.resolve("empty.txt"), "");

        Node.Op fileNode = reader.read(baseDir, new Path[]{Path.of("empty.txt")}).iterator().next();

        assertTrue(fileNode.getChildren().isEmpty());
    }

    @Test
    public void getPluginIdIsTheTextPluginClassName() {
        assertEquals(TextPlugin.class.getName(), reader.getPluginId());
    }

    @Test
    public void getPrioritizedPatternsIncludesCommonTextExtensions() {
        assertTrue(reader.getPrioritizedPatterns().get(1).length > 0);
        assertTrue(List.of(reader.getPrioritizedPatterns().get(1)).contains("**.txt"));
    }
}
