package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Artifacts are stored in pack files, one per write, instead of one file each (see
 * SerTransactionStrategy#writeArtifactPack): a repository must reopen with the same content, packs
 * mostly superseded by later writes are compacted, a pack left by a crashed write is deleted, and a
 * missing pack is reported as damage.
 */
public class ArtifactPackTest {

    private static List<Path> packs(Path repoDir) throws Exception {
        try (Stream<Path> files = Files.list(repoDir.resolve("artifacts"))) {
            return files.filter(p -> p.getFileName().toString().startsWith("pack-")).toList();
        }
    }

    private static List<Path> otherArtifactFiles(Path repoDir) throws Exception {
        try (Stream<Path> files = Files.list(repoDir.resolve("artifacts"))) {
            return files.filter(p -> !p.getFileName().toString().startsWith("pack-")).toList();
        }
    }

    private static String checkout(Path repoDir, Path out, String configuration) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            Files.createDirectories(out);
            service.setBaseDir(out);
            service.checkout(configuration);
        }
        return Files.readString(out.resolve("f.txt"));
    }

    @Test
    @Timeout(180)
    public void manyCommitsReopenWithTheSameContentInFewPacks() throws Exception {
        Path work = Files.createTempDirectory("artifact-packs");
        Path repoDir = work.resolve(".ecco");
        Path content = Files.createDirectories(work.resolve("content"));
        StringBuilder text = new StringBuilder();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            for (int c = 1; c <= 30; c++) {
                // each commit adds a line and changes the first one - older packs keep getting superseded
                text.append("line ").append(c).append('\n');
                Files.writeString(content.resolve("f.txt"), "version " + c + "\n" + text);
                service.commit("c" + c, "A." + c);
            }
        }

        assertTrue(otherArtifactFiles(repoDir).isEmpty(), "artifacts are only stored in packs: " + otherArtifactFiles(repoDir));
        assertTrue(packs(repoDir).size() <= 20, "packs are compacted and merged - one per commit would be 30, found " + packs(repoDir).size());
        assertEquals("version 30\n" + text, checkout(repoDir, work.resolve("out"), "A.30"));
    }

    @Test
    @Timeout(120)
    public void aPackLeftByACrashedWriteIsDeleted() throws Exception {
        Path work = Files.createTempDirectory("artifact-packs-crash");
        Path repoDir = work.resolve(".ecco");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.commit("one", "A");
            // written before the id swap of a write that never got there
            Files.writeString(repoDir.resolve("artifacts").resolve("pack-crashed.zip"), "partial");
            Files.writeString(content.resolve("f.txt"), "a\nb\n");
            service.commit("two", "A.2");
        }
        assertTrue(packs(repoDir).stream().noneMatch(p -> p.getFileName().toString().equals("pack-crashed.zip")));
        assertEquals("a\nb\n", checkout(repoDir, work.resolve("out"), "A.2"));
    }

    @Test
    @Timeout(120)
    public void aMissingPackIsReportedAsDamage() throws Exception {
        Path work = Files.createTempDirectory("artifact-packs-missing");
        Path repoDir = work.resolve(".ecco");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.commit("one", "A");
        }
        for (Path pack : packs(repoDir))
            Files.delete(pack);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            EccoException e = assertThrows(EccoException.class, () -> {
                service.open();
                service.getRepository();
            });
            Throwable root = e;
            StringBuilder messages = new StringBuilder();
            while (root != null) {
                messages.append(root.getMessage()).append(" | ");
                root = root.getCause();
            }
            assertTrue(messages.toString().contains("damaged"), messages.toString());
        }
    }
}
