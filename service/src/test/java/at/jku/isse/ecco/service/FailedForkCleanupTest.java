package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A fork creates a new repository where none existed. When it failed part way - e.g. the origin
 * couldn't be opened, or the merge/store failed after begin(READ_WRITE) - it left the half-created
 * .ecco directory behind, the service initialized and (on the write paths) the transaction and
 * write lock open. Retrying then failed with "A repository already exists at the given location".
 * A failed fork must leave nothing behind, so it can simply be retried.
 */
public class FailedForkCleanupTest {

    @Test
    @Timeout(60)
    public void aFailedLocalForkLeavesNoRepositoryBehindAndCanBeRetried() throws Exception {
        Path workDir = Files.createTempDirectory("failed-fork-cleanup");
        Path notARepository = workDir.resolve("does-not-exist").resolve(".ecco");
        Path targetRepoDir = workDir.resolve("target").resolve(".ecco");
        Files.createDirectories(targetRepoDir.getParent());

        try (EccoService target = new EccoService()) {
            target.setRepositoryDir(targetRepoDir);
            assertThrows(Exception.class, () -> target.fork(notARepository));

            assertFalse(Files.exists(targetRepoDir), "the half-created repository must be removed");
            assertFalse(target.isInitialized(), "the service must be back in its uninitialized state");

            // retry against a real origin
            Path originRepoDir = Files.createDirectories(workDir.resolve("origin")).resolve(".ecco");
            Path originContent = Files.createDirectories(workDir.resolve("origin-content"));
            Files.writeString(originContent.resolve("f.txt"), "a\n");
            try (EccoService origin = new EccoService()) {
                origin.setRepositoryDir(originRepoDir);
                origin.init();
                origin.setBaseDir(originContent);
                origin.commit("commit", "A");
            }
            assertDoesNotThrow(() -> target.fork(originRepoDir));
            assertEquals(1, target.getRepository().getFeatures().size());
        }
    }
}
