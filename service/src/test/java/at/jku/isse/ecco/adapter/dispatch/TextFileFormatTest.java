package at.jku.isse.ecco.adapter.dispatch;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * See TextFileFormat: line adapters normalized CRLF to LF, appended a missing final newline and
 * replaced non-UTF-8 bytes with U+FFFD at commit time.
 */
public class TextFileFormatTest {

    private static final String[] PIECES = {"a", "bc", "é", "€", " ", "\t", "\n", "\r\n", "\r", ""};

    @Test
    public void linesAreSplitExactlyLikeBufferedReaderReadLine() throws IOException {
        // so the line artifacts of files committed before TextFileFormat existed stay the same
        Random random = new Random(1);
        Path file = Files.createTempFile("text-file-format", ".txt");
        for (int round = 0; round < 5_000; round++) {
            String text = randomText(random);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            List<String> expected = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
                for (String line; (line = reader.readLine()) != null; ) expected.add(line);
            }
            assertEquals(expected, TextFileFormat.read(file).lines(), "round " + round + ": " + text.replace("\r", "\\r").replace("\n", "\\n"));
        }
    }

    @Test
    public void roundTripIsByteExact() throws IOException {
        Random random = new Random(2);
        Path in = Files.createTempFile("text-file-format-in", ".txt");
        Path out = Files.createTempFile("text-file-format-out", ".txt");
        for (int round = 0; round < 5_000; round++) {
            // one separator per file, as produced by any normal editor
            String separator = new String[]{"\n", "\r\n", "\r"}[random.nextInt(3)];
            StringBuilder text = new StringBuilder();
            int lines = random.nextInt(6);
            for (int l = 0; l < lines; l++) {
                if (l > 0) text.append(separator);
                for (int k = random.nextInt(4); k > 0; k--) text.append(new String[]{"a", "bc", "é", "€", " ", "\t"}[random.nextInt(6)]);
            }
            if (lines > 0 && random.nextBoolean()) text.append(separator);
            byte[] bytes = random.nextInt(4) == 0
                    ? text.toString().replace("€", "e").getBytes(StandardCharsets.ISO_8859_1) // not valid UTF-8 whenever it has an 'é'
                    : text.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(in, bytes);

            assertArrayEquals(bytes, roundTrip(in, out), "round " + round);
        }
    }

    @Test
    public void nonUtf8BytesAreNotLost() throws IOException {
        Path in = Files.createTempFile("latin1", ".txt");
        Path out = Files.createTempFile("latin1-out", ".txt");
        byte[] latin1 = {'c', 'a', 'f', (byte) 0xE9, '\r', '\n'};
        Files.write(in, latin1);
        assertEquals("ISO-8859-1", TextFileFormat.read(in).charset().name());
        assertArrayEquals(latin1, roundTrip(in, out));
    }

    @Test
    public void filesWithoutARecordedFormatAreWrittenAsBefore() throws IOException {
        Path out = Files.createTempFile("legacy", ".txt");
        PluginArtifactData legacy = new PluginArtifactData("plugin", Paths.get("legacy.txt"));
        try (TextFileFormat.LineWriter writer = TextFileFormat.newLineWriter(out, legacy)) {
            writer.writeLine("a");
            writer.writeLine("b");
        }
        String separator = System.lineSeparator();
        assertEquals("a" + separator + "b" + separator, Files.readString(out));
    }

    private static byte[] roundTrip(Path in, Path out) throws IOException {
        TextFileFormat.Decoded decoded = TextFileFormat.read(in);
        PluginArtifactData data = new PluginArtifactData("plugin", Paths.get("file.txt"));
        decoded.recordOn(data);
        try (TextFileFormat.LineWriter writer = TextFileFormat.newLineWriter(out, data)) {
            for (String line : decoded.lines()) writer.writeLine(line);
        }
        return Files.readAllBytes(out);
    }

    private static String randomText(Random random) {
        StringBuilder text = new StringBuilder();
        for (int k = random.nextInt(12); k > 0; k--) text.append(PIECES[random.nextInt(PIECES.length)]);
        return text.toString();
    }
}
