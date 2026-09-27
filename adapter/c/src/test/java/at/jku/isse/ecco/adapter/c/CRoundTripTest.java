package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

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
        Path baseDir = Files.createTempDirectory("c-roundtrip");
        Files.writeString(baseDir.resolve("main.c"), source);
        Set<Node> read = Set.copyOf(new CReader(new SerEntityFactory()).read(baseDir, new Path[]{Path.of("main.c")}));

        Path outputDir = Files.createTempDirectory("c-roundtrip-out");
        Path[] written = new CWriter().write(outputDir, read);

        assertEquals(1, written.length);
        assertEquals(source, Files.readString(written[0]).replace(System.lineSeparator(), "\n"));
    }
}
