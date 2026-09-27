package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * endReadWrite() writes the dirty artifact files, then the dirty association files, then the new
 * core, and only then flips the id file. Artifacts/associations that already existed used to be
 * rewritten IN PLACE under their stable file names - files the still-current old core points at.
 * A commit failing (or the process dying) after some of those were rewritten but before the id
 * flip left the old core loading a mix of old and new files, e.g. an existing file-level artifact
 * whose PartialOrderGraph now referenced line artifacts the old core had never heard of - every
 * later open then failed with "Could not resolve POG node artifact ...", i.e. an unopenable repo.
 * <p>
 * Reproduced here without killing the process: making associations/ read-only lets the artifact
 * phase succeed (overwriting the existing file artifact) and fails the association phase, before
 * the id flip - exactly the window a crash would hit.
 */
public class SerTransactionStrategyInterruptedCommitTest {

    @Test
    @Timeout(60)
    public void aCommitFailingBeforeTheIdSwapLeavesThePreviousStateLoadable() throws IOException {
        Path workDir = Files.createTempDirectory("ser-transaction-strategy-interrupted-commit");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("content");
        Files.createDirectories(contentDir);
        Path file = contentDir.resolve("file.txt");
        Files.writeString(file, "a\nb\n");
        Path associationsDir = repoDir.resolve("associations");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);
            service.commit("commit 1", "A");

            Files.writeString(file, "a\nx\nb\n");
            Files.setPosixFilePermissions(associationsDir, PosixFilePermissions.fromString("r-xr-xr-x"));
            try {
                assertThrows(Exception.class, () -> service.commit("commit 2", "A.2"),
                        "precondition: the second commit must fail while associations/ is read-only");
            } finally {
                Files.setPosixFilePermissions(associationsDir, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            assertDoesNotThrow(service::open, "the repository must still open after a commit failed before the id swap");
            assertEquals(1, service.getRepository().getCommits().size(), "the failed commit must not be visible");

            Path checkoutDir = workDir.resolve("checkout");
            Files.createDirectories(checkoutDir);
            service.setBaseDir(checkoutDir);
            service.checkout("A");
            assertEquals("a\nb\n", Files.readString(checkoutDir.resolve("file.txt")),
                    "checking out the last successful commit must reproduce its content");

            // and the repository must still accept new commits afterwards
            Files.writeString(file, "a\nx\nb\n");
            service.setBaseDir(contentDir);
            assertDoesNotThrow(() -> service.commit("commit 2 retried", "A.2"));
        }

        try (Stream<Path> leftovers = Files.walk(repoDir)) {
            assertTrue(leftovers.noneMatch(p -> p.getFileName().toString().endsWith(".pending")),
                    "no pending files may survive a successful commit");
        }
    }

    /**
     * Crash after the id-file swap but before the staged files were renamed into place: the stable
     * file is gone/stale and the authoritative version is still "<name>.<currentId>.pending". The
     * next load must roll it forward.
     */
    @Test
    @Timeout(60)
    public void pendingFilesOfTheCurrentTransactionAreRolledForwardOnLoad() throws IOException {
        Path workDir = Files.createTempDirectory("ser-transaction-strategy-roll-forward");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("file.txt"), "a\nb\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);
            service.commit("commit 1", "A");
        }

        String currentId = Files.readString(repoDir.resolve("id")).trim();
        Path associationsDir = repoDir.resolve("associations");
        Path stable;
        try (Stream<Path> files = Files.list(associationsDir)) {
            stable = files.findFirst().orElseThrow();
        }
        Path pending = stable.resolveSibling(stable.getFileName() + "." + currentId + ".pending");
        Files.move(stable, pending);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertEquals(1, service.getRepository().getCommits().size());
        }
        assertTrue(Files.exists(stable), "the pending file must have been rolled forward to its stable name");
        assertTrue(Files.notExists(pending));
    }

    /**
     * Pending files of a transaction that never reached its swap are garbage: readers must ignore
     * them (never load or delete them - they could be a concurrent writer's), and the next writer
     * discards them.
     */
    @Test
    @Timeout(60)
    public void pendingFilesOfAnAbortedTransactionAreIgnoredAndDiscardedByTheNextWriter() throws IOException {
        Path workDir = Files.createTempDirectory("ser-transaction-strategy-aborted-pending");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("file.txt"), "a\nb\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);
            service.commit("commit 1", "A");
        }

        Path associationsDir = repoDir.resolve("associations");
        Path stable;
        try (Stream<Path> files = Files.list(associationsDir)) {
            stable = files.findFirst().orElseThrow();
        }
        Path aborted = stable.resolveSibling(stable.getFileName() + ".00000000-0000-0000-0000-000000000000.pending");
        Files.writeString(aborted, "not a zip - must never be loaded");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertEquals(1, service.getRepository().getCommits().size());
            assertTrue(Files.exists(aborted), "a reader must not delete another transaction's pending files");

            Files.writeString(contentDir.resolve("file.txt"), "a\nx\nb\n");
            service.setBaseDir(contentDir);
            service.commit("commit 2", "A.2");
        }
        assertTrue(Files.notExists(aborted), "the next writer must discard an aborted transaction's pending files");
    }
}
