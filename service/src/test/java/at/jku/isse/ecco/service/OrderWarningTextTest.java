package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.service.OrderResolutionCharacterizationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ORDER line of a checkout's .warnings file names the lines whose order is undecided, with their
 * first and last text - it listed every line of the file, the decided ones too.
 */
public class OrderWarningTextTest {

    @Test
    @Timeout(60)
    public void theOrderWarningNamesTheUndecidedLines(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Path out = Files.createDirectories(tmp.resolve("out"));
            checkout(service, out, "BASE, A, B"); // f.txt: a c b - c and b undecided
            List<String> order = Files.readString(out.resolve(".warnings")).lines().filter(l -> l.startsWith("ORDER")).toList();
            assertEquals(1, order.size(), order.toString());
            assertTrue(order.get(0).contains("undecided: lines 2-3 (c … b)"), order.get(0));
            assertFalse(order.get(0).contains("(line 1)"), "the decided line is not listed: " + order.get(0));
        }
    }
}
