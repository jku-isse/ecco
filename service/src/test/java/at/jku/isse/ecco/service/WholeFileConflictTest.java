package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Known gap #54: a checkout combining variants that hold different versions of a file stored whole
 * (the file adapter: binaries, files of no other adapter such as a Makefile) failed as a whole -
 * "Expected exactly one child node (FileArtifactData) ... found 2". The other files are written, the
 * conflicting one is not, and a CONFLICT warning names it and the commits of each version.
 */
public class WholeFileConflictTest {

    private static Path folder(Path dir, byte[] binary, String text) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("data.bin"), binary);
        Files.writeString(dir.resolve("a.txt"), text);
        return dir;
    }

    @Test
    @Timeout(120)
    public void theOtherFilesAreWrittenAndTheConflictReported() throws Exception {
        Path work = Files.createTempDirectory("whole-file-conflict");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(Files.createDirectories(work.resolve("repo")).resolve(".ecco"));
            service.init();
            service.setBaseDir(folder(work.resolve("v1"), new byte[]{0, 1, 2, (byte) 0xff}, "base\n"));
            service.commit("first", "BASE, A");
            service.setBaseDir(folder(work.resolve("v2"), new byte[]{0, 1, 3, (byte) 0xfe}, "base\nb\n"));
            service.commit("second", "BASE, B");

            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            Checkout checkout = service.checkout("BASE, A, B");

            assertTrue(Files.exists(out.resolve("a.txt")), "the other files are written");
            assertFalse(Files.exists(out.resolve("data.bin")), "neither version of the conflicting file is");
            assertEquals(1, checkout.getConflicts().size(), checkout.getConflicts().toString());
            String conflict = checkout.getConflicts().get(0);
            assertTrue(conflict.contains("data.bin") && conflict.contains("first") && conflict.contains("second"), conflict);
            String warnings = Files.readString(out.resolve(EccoService.WARNINGS_FILE_NAME));
            assertTrue(warnings.contains("CONFLICT: " + conflict), warnings);

            // a configuration with one version still gets it
            Path one = Files.createDirectories(work.resolve("one"));
            service.setBaseDir(one);
            assertTrue(service.checkout("BASE, B").getConflicts().isEmpty());
            assertArrayEquals(new byte[]{0, 1, 3, (byte) 0xfe}, Files.readAllBytes(one.resolve("data.bin")));
        }
    }
}
