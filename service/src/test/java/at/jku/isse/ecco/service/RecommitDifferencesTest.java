package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A configuration committed again with other content (FR-M4d): its checkout keeps only what both
 * commits share - a line changed in the second is in neither checkout's version - so the front ends
 * warn before such a commit. The warning names the files that differ from the configuration's checkout,
 * and stays silent for a new configuration, for the same content, and for lines only reordered.
 */
@Timeout(120)
public class RecommitDifferencesTest {

    @Test
    public void aConfigurationCommittedAgainWithOtherContentLosesTheChangedLines(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            Path dir = Files.createDirectories(tmp.resolve("v"));
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("f.txt"), "head\nold body\ntail\n");
            service.commit("v1", "A");
            Files.writeString(dir.resolve("f.txt"), "head\nnew body\ntail\n");
            service.commit("v2", "A");
            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            assertEquals("head\ntail\n", Files.readString(out.resolve("f.txt")), "why the warning exists: neither body checks out");
        }
    }

    @Test
    public void theFilesThatDifferFromTheCommittedVariantAreNamed(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            Path dir = Files.createDirectories(tmp.resolve("v"));
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("f.txt"), "head\nbody\ntail\n");
            Files.writeString(dir.resolve("g.txt"), "g\n");
            service.commit("v1", "A");

            assertEquals(List.of(), service.recommitDifferences("A"), "the same content");
            assertEquals(List.of(), service.recommitDifferences("A, B"), "a configuration not committed before");

            Files.writeString(dir.resolve("f.txt"), "head\ntail\nbody\n");
            assertEquals(List.of(), service.recommitDifferences("A"), "only reordered: an ORDER warning, no lines lost");

            Files.writeString(dir.resolve("f.txt"), "head\nnew body\ntail\n");
            Files.delete(dir.resolve("g.txt"));
            Files.createDirectories(dir.resolve("sub"));
            Files.writeString(dir.resolve("sub/new.txt"), "new\n");
            List<String> differences = service.recommitDifferences("A");
            assertEquals(List.of("f.txt", "g.txt", Path.of("sub", "new.txt").toString()), differences);

            String warning = EccoService.describeRecommit("A", differences);
            assertTrue(warning.startsWith("A was committed before with other content: 3 files differ (f.txt, g.txt, "), warning);
            assertTrue(warning.contains("new revisions (F')"), warning);
            assertEquals("", EccoService.describeRecommit("A", List.of()));

            // A' is a new revision: committed with it, A's variant stays as it was
            assertEquals(List.of(), service.recommitDifferences("A'"));
        }
    }
}
