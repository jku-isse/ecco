package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CRoundTripTest {

    @Test
    public void emptyLinesSurviveReadAndWrite() throws IOException {
        String source = """
                #include <stdio.h>

                int main() {
                    int x = 1;

                    printf("%d\\n", x);

                    return 0;
                }

                """;
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), roundTrip(source.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void crlfAndMissingFinalNewlineSurvive() throws IOException {
        byte[] source = "#ifndef H\r\n#define H\r\nint f();\r\n#endif".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, roundTrip(source));
    }

    @Test
    public void latin1FilesCanBeCommittedAndSurvive() throws IOException {
        byte[] source = "/* caf\u00e9 */\nint f() {\n    return 1;\n}\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(source, roundTrip(source));
    }

    private static byte[] roundTrip(byte[] source) throws IOException {
        Path baseDir = Files.createTempDirectory("c-roundtrip");
        Files.write(baseDir.resolve("main.c"), source);
        Set<Node> read = Set.copyOf(new CReader(new SerEntityFactory()).read(baseDir, new Path[]{Path.of("main.c")}));

        Path outputDir = Files.createTempDirectory("c-roundtrip-out");
        Path[] written = new CWriter().write(outputDir, read);

        assertEquals(1, written.length);
        return Files.readAllBytes(written[0]);
    }
}
