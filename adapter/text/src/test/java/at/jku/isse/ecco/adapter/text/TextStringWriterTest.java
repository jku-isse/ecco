package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.ExportListener;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextStringWriter turns read text files into strings. Nothing in the repository uses it (TextModule
 * binds only TextFileWriter), so it is tested as an ArtifactWriter over what TextReader produces. It
 * predates TextFileFormat: every line, the last included, ends with "\n", whatever the file had.
 */
public class TextStringWriterTest {

    private static Set<Node> read(String... contents) throws IOException {
        Path dir = Files.createTempDirectory("text-string-writer");
        Path[] paths = new Path[contents.length];
        for (int i = 0; i < contents.length; i++) {
            paths[i] = Path.of("f" + i + ".txt");
            Files.write(dir.resolve(paths[i]), contents[i].getBytes(StandardCharsets.UTF_8));
        }
        return new LinkedHashSet<>(new TextReader(new SerEntityFactory()).read(dir, paths));
    }

    @Test
    public void linesAreJoinedWithLineFeedsAndTheFinalNewlineMarkerIsNotALine() throws IOException {
        assertArrayEquals(new String[]{"one\ntwo\n"}, new TextStringWriter().write(read("one\ntwo\n")));
    }

    @Test
    public void emptyFileIsAnEmptyString() throws IOException {
        assertArrayEquals(new String[]{""}, new TextStringWriter().write(read("")));
    }

    @Test
    public void whitespaceInsideLinesIsKept() throws IOException {
        assertArrayEquals(new String[]{"\ta  \n \n"}, new TextStringWriter().write(read("\ta  \n \n")));
    }

    /** The string is not the file: CRLF becomes LF and a missing final newline is added. */
    @Test
    public void lineSeparatorsAndAMissingFinalNewlineAreNormalizedCurrentBehaviour() throws IOException {
        assertArrayEquals(new String[]{"one\ntwo\n"}, new TextStringWriter().write(read("one\r\ntwo")));
    }

    @Test
    public void oneStringPerFile() throws IOException {
        Set<Node> files = read("a\n", "b\nc\n");
        List<String> strings = List.of(new TextStringWriter().write(files));
        assertEquals(2, strings.size());
        assertTrue(strings.containsAll(List.of("a\n", "b\nc\n")));
    }

    @Test
    public void theBaseIsIgnored() throws IOException {
        Set<Node> files = read("a\n");
        assertArrayEquals(new TextStringWriter().write(files), new TextStringWriter().write("ignored", files));
    }

    @Test
    public void fileNodeWithoutLinesBuiltByHand() {
        SerEntityFactory factory = new SerEntityFactory();
        Node.Op file = factory.createOrderedNode(factory.createArtifact(new PluginArtifactData(TextPlugin.class.getName(), Path.of("a.txt"))));
        assertArrayEquals(new String[]{""}, new TextStringWriter().write(Set.of(file)));
        assertArrayEquals(new String[0], new TextStringWriter().write(Set.of()));
    }

    @Test
    public void listenersAndPluginId() {
        TextStringWriter writer = new TextStringWriter();
        WriteListener listener = new WriteListener() {
        };
        writer.addListener(listener);
        writer.removeListener(listener);
        assertEquals(TextPlugin.class.getName(), writer.getPluginId());
    }

    /** TextExporter is bound in TextModule but not implemented: it exports nothing. */
    @Test
    public void textExporterExportsNothing() throws IOException {
        TextExporter exporter = new TextExporter();
        Set<Node> files = read("a\n");
        assertEquals(0, exporter.export(files).length);
        assertEquals(0, exporter.export(Path.of("."), files).length);
        assertEquals(TextPlugin.class.getName(), exporter.getPluginId());
        ExportListener listener = new ExportListener() {
        };
        exporter.addListener(listener);
        exporter.removeListener(listener);
    }
}
