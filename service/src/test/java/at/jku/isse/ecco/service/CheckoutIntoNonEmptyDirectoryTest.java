package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A checkout writes only into a directory that is empty apart from the repository's own .ecco
 * (FR-K3a), and is refused before it writes anything otherwise - also when all that is there is a
 * .config, .warnings or .hashes left by an earlier checkout, or another repository.
 */
public class CheckoutIntoNonEmptyDirectoryTest {

    @Test
    @Timeout(60)
    public void aDirectoryHoldingAnythingIsRefusedAndLeftAsItWas(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);
            Files.writeString(work.resolve("a.txt"), "a\n");
            service.commit("a", "A");

            for (String leftOver : List.of("keep.txt", ".config", ".warnings", ".hashes")) {
                Path out = Files.createDirectories(tmp.resolve("out" + leftOver));
                Files.writeString(out.resolve(leftOver), "left over\n");
                assertRefused(service, out);
            }

            Path otherRepository = Files.createDirectories(tmp.resolve("other"));
            try (EccoService other = new EccoService()) {
                other.setRepositoryDir(otherRepository.resolve(".ecco"));
                other.init();
            }
            assertRefused(service, otherRepository);

            // the repository's own .ecco does not count
            Files.delete(work.resolve("a.txt"));
            service.setBaseDir(work);
            service.checkout("A");
            assertEquals("a\n", Files.readString(work.resolve("a.txt")));
        }
    }

    private static void assertRefused(EccoService service, Path out) throws Exception {
        Map<String, String> before = DirectoryFingerprint.of(out);
        service.setBaseDir(out);
        EccoException e = assertThrows(EccoException.class, () -> service.checkout("A"), out.toString());
        assertTrue(messages(e).contains("must be empty"), messages(e));
        assertEquals(before, DirectoryFingerprint.of(out), "nothing is written into " + out);
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause())
            sb.append(t.getMessage()).append(" | ");
        return sb.toString();
    }
}
