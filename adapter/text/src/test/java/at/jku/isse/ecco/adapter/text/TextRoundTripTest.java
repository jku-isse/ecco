package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Read -> write of text files must give back the same bytes (TextFileFormat records charset, line
 * separator and final newline). The one documented exception: a file mixing line separators gets
 * the first one throughout.
 */
public class TextRoundTripTest {

    private static byte[] roundTrip(byte[] content) throws IOException {
        Path in = Files.createTempDirectory("text-round-trip");
        Files.write(in.resolve("a.txt"), content);
        Set<Node.Op> nodes = new TextReader(new SerEntityFactory()).read(in, new Path[]{Path.of("a.txt")});
        Path out = Files.createTempDirectory("text-round-trip-out");
        Path[] written = new TextFileWriter().write(out, Set.copyOf(nodes));
        assertArrayEquals(new Path[]{out.resolve("a.txt")}, written);
        return Files.readAllBytes(written[0]);
    }

    private static void assertRoundTrips(String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(bytes, roundTrip(bytes), "round trip of " + content.replace("\r", "\\r").replace("\n", "\\n"));
    }

    @Test
    public void crlf() throws IOException {
        assertRoundTrips("one\r\ntwo\r\n");
    }

    @Test
    public void crlfWithoutAFinalNewline() throws IOException {
        assertRoundTrips("one\r\ntwo");
    }

    @Test
    public void carriageReturnsOnly() throws IOException {
        assertRoundTrips("one\rtwo\r");
    }

    @Test
    public void noFinalNewline() throws IOException {
        assertRoundTrips("one\ntwo");
    }

    @Test
    public void singleLineWithoutNewline() throws IOException {
        assertRoundTrips("x");
    }

    @Test
    public void emptyFile() throws IOException {
        assertRoundTrips("");
    }

    @Test
    public void onlyNewlines() throws IOException {
        assertRoundTrips("\n");
        assertRoundTrips("\n\n\n");
        assertRoundTrips("\r\n\r\n");
    }

    @Test
    public void emptyLinesInTheMiddleAndAtTheEnd() throws IOException {
        assertRoundTrips("a\n\n\nb\n\n");
    }

    @Test
    public void tabsAndTrailingWhitespace() throws IOException {
        assertRoundTrips("\tindented\n  two spaces  \nend\t\t\n   \n");
    }

    @Test
    public void utf8ByteOrderMark() throws IOException {
        assertRoundTrips("\uFEFFfirst\nsecond\n");
    }

    @Test
    public void multiByteUtf8() throws IOException {
        assertRoundTrips("Grüße\n日本語\n\uD83C\uDFB5\n");
    }

    @Test
    public void nulAndControlCharacters() throws IOException {
        assertRoundTrips("a\u0000b\u000c\u001b[0m\n");
    }

    @Test
    public void iso88591BytesAreKept() throws IOException {
        byte[] latin1 = "café\r\nnaïve\r\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(latin1, roundTrip(latin1));
    }

    @Test
    public void utf16WithByteOrderMarkIsKeptByteForByte() throws IOException {
        // not UTF-8, so read as ISO-8859-1, which maps every byte to one character and back
        byte[] utf16 = "a\nb\n".getBytes(StandardCharsets.UTF_16);
        assertArrayEquals(utf16, roundTrip(utf16));
    }

    @Test
    public void invalidUtf8InOtherwiseUtf8TextIsKept() throws IOException {
        byte[] mixed = {'o', 'k', ' ', (byte) 0xc3, (byte) 0xa9, '\n', 'b', 'a', 'd', ' ', (byte) 0xff, (byte) 0xfe, '\n'};
        assertArrayEquals(mixed, roundTrip(mixed));
    }

    /** Documented limitation of TextFileFormat: a file mixing line separators gets the first one throughout. */
    @Test
    public void mixedLineEndingsGetTheFirstSeparatorCurrentBehaviour() throws IOException {
        assertArrayEquals("a\r\nb\r\nc\r\n".getBytes(StandardCharsets.UTF_8), roundTrip("a\r\nb\nc\r\n".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals("a\nb\nc".getBytes(StandardCharsets.UTF_8), roundTrip("a\nb\r\nc".getBytes(StandardCharsets.UTF_8)));
        // LF followed by CR is two separators, the CR becomes an LF
        assertArrayEquals("a\n\nb".getBytes(StandardCharsets.UTF_8), roundTrip("a\n\rb".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readerRefusesAMissingFile() throws IOException {
        Path in = Files.createTempDirectory("text-missing");
        assertThrows(EccoException.class, () -> new TextReader(new SerEntityFactory()).read(in, new Path[]{Path.of("missing.txt")}));
    }

    @Test
    public void readerAndWriterWithoutABaseUseThePathAsGiven() throws IOException {
        Path dir = Files.createTempDirectory("text-no-base");
        Files.writeString(dir.resolve("a.txt"), "x\r\ny");
        // absolute paths resolve to themselves against "."
        Set<Node.Op> nodes = new TextReader(new SerEntityFactory()).read(new Path[]{dir.resolve("a.txt")});
        Files.delete(dir.resolve("a.txt"));
        Path[] written = new TextFileWriter().write(Set.copyOf(nodes));
        assertArrayEquals(new Path[]{dir.resolve("a.txt")}, written);
        assertEquals("x\r\ny", Files.readString(written[0]));
    }

    @Test
    public void writerReportsAnUnwritableTarget() throws IOException {
        Path dir = Files.createTempDirectory("text-unwritable");
        Files.writeString(dir.resolve("a.txt"), "x\n");
        Set<Node.Op> nodes = new TextReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("a.txt")});
        EccoException e = assertThrows(EccoException.class, () -> new TextFileWriter().write(dir.resolve("missing-dir"), Set.copyOf(nodes)));
        assertInstanceOf(IOException.class, e.getCause());
    }

    /** A file node without a recorded format (committed before TextFileFormat) is written the old way: every line followed by the platform separator. */
    @Test
    public void fileWithoutARecordedFormatIsWrittenTheOldWay() throws IOException {
        SerEntityFactory factory = new SerEntityFactory();
        Node.Op file = factory.createOrderedNode(factory.createArtifact(new PluginArtifactData(TextPlugin.class.getName(), Path.of("a.txt"))));
        file.addChild(factory.createNode(new LineArtifactData("one")));
        file.addChild(factory.createNode(new LineArtifactData("two")));
        Path out = Files.createTempDirectory("text-old-format");
        new TextFileWriter().write(out, Set.of(file));
        String nl = System.lineSeparator();
        assertEquals("one" + nl + "two" + nl, Files.readString(out.resolve("a.txt")));
    }

    @Test
    public void listeners() {
        TextReader reader = new TextReader(new SerEntityFactory());
        ReadListener readListener = new ReadListener() {
        };
        reader.addListener(readListener);
        reader.removeListener(readListener);
        TextFileWriter writer = new TextFileWriter();
        WriteListener writeListener = new WriteListener() {
        };
        writer.addListener(writeListener);
        writer.removeListener(writeListener);
    }
}
