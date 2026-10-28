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

    /**
     * A pack is compacted once most of its BYTES are superseded, not only once most of its artifacts
     * are: a file's own artifact holds its order graph, a node per line, and is written again by every
     * commit that changes the file - in tinycc 324 such copies, 0.2% of the artifacts, were 6 MB of
     * 25 MB, every pack still current by count. Reopened, each pack holds at least half its bytes current.
     */
    @Test
    @Timeout(180)
    public void aPackIsCompactedOnceMostOfItsBytesAreSuperseded() throws Exception {
        Path work = Files.createTempDirectory("artifact-packs-bytes");
        Path repoDir = work.resolve(".ecco");
        Path content = Files.createDirectories(work.resolve("content"));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 400; i++)
            text.append(i % 7 == 0 ? "}" : "x").append('\n');
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            for (int c = 1; c <= 12; c++) {
                // one line more each time: the file's artifact and its order graph are written again
                text.append("line ").append(c).append('\n');
                Files.writeString(content.resolve("f.txt"), text.toString());
                service.commit("c" + c, "A." + c);
            }
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            at.jku.isse.ecco.storage.ser.repository.SerRepository repository = (at.jku.isse.ecco.storage.ser.repository.SerRepository) service.getRepository();
            for (String pack : repository.getPackOrder())
                assertTrue(repository.currentBytes(pack) * 2 >= repository.getPackBytes().get(pack),
                        pack + ": " + repository.currentBytes(pack) + " of " + repository.getPackBytes().get(pack) + " bytes current");
        }
        assertEquals(text.toString(), checkout(repoDir, work.resolve("out"), "A.12"));
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

    /** A pack still being written when a write crashed (or failed, e.g. out of disk space) stayed in the artifacts folder for good. */
    @Test
    @Timeout(120)
    public void aPackLeftUnfinishedIsDeleted() throws Exception {
        Path work = Files.createTempDirectory("artifact-packs-unfinished");
        Path repoDir = work.resolve(".ecco");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.commit("one", "A");
            Files.writeString(repoDir.resolve("artifacts").resolve("pack-crashed.zip.0b5c7e1a-4f1e-4a8e-9a43-1f2d3c4b5a69.tmp"), "partial");
            Files.writeString(content.resolve("f.txt"), "a\nb\n");
            service.commit("two", "A.2");
        }
        assertEquals(List.of(), otherArtifactFiles(repoDir));
        assertTrue(packs(repoDir).stream().noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), packs(repoDir).toString());
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

    /**
     * A pack whose digests entry is missing or cut short opens all the same (missing, it threw a
     * NullPointerException): the digests only spare a later write rewriting unchanged artifacts.
     */
    @Test
    @Timeout(120)
    public void aPackWithoutOrWithShortDigestsStillOpens() throws Exception {
        for (boolean missing : new boolean[]{true, false}) {
            Path work = Files.createTempDirectory("artifact-packs-digests");
            Path repoDir = work.resolve(".ecco");
            Path content = Files.createDirectories(work.resolve("content"));
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(repoDir);
                service.init();
                service.setBaseDir(content);
                Files.writeString(content.resolve("f.txt"), "a\nb\n");
                service.commit("one", "A.1");
            }
            for (Path pack : packs(repoDir))
                rewriteDigests(pack, missing);

            assertEquals("a\nb\n", checkout(repoDir, work.resolve("out1"), "A.1"), missing ? "no digests" : "short digests");
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(repoDir);
                service.open();
                service.setBaseDir(content);
                Files.writeString(content.resolve("f.txt"), "a\nx\nb\n");
                service.commit("two", "A.2");
            }
            assertEquals("a\nb\n", checkout(repoDir, work.resolve("out2"), "A.1"));
            assertEquals("a\nx\nb\n", checkout(repoDir, work.resolve("out3"), "A.2"));
        }
    }

    /** Copies [pack] without its digests entry, or with only half of it. */
    private static void rewriteDigests(Path pack, boolean missing) throws Exception {
        Path copy = pack.resolveSibling(pack.getFileName() + ".copy");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(pack.toFile());
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(copy))) {
            for (java.util.zip.ZipEntry entry : java.util.Collections.list(zip.entries())) {
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                if (entry.getName().equals("digests")) {
                    if (missing)
                        continue;
                    bytes = java.util.Arrays.copyOf(bytes, bytes.length / 2);
                }
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                out.write(bytes);
                out.closeEntry();
            }
        }
        Files.move(copy, pack, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
