package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * getCommits() (and enable/disableFeatureTraceBoosting()) never begin a transaction themselves -
 * they delegate to getRepository(), which already rolls its own back on failure - yet their catch
 * blocks called transactionStrategy.rollback() again. With no transaction active that throws
 * "Error rolling back transaction: No transaction active.", which replaced the real error: a
 * repository that failed to load was reported as a transaction-bookkeeping problem.
 */
public class ErrorMaskingRollbackTest {

    @Test
    @Timeout(30)
    public void getCommitsReportsTheRealLoadFailure() throws Exception {
        Path workDir = Files.createTempDirectory("error-masking-rollback");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("f.txt"), "a\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            service.commit("commit", "A");
        }
        try (Stream<Path> associations = Files.list(repoDir.resolve("associations"))) {
            Files.delete(associations.findFirst().orElseThrow());
        }

        EccoService service = new EccoService();
        service.setRepositoryDir(repoDir);
        service.open();
        EccoException exception = assertThrows(EccoException.class, service::getCommits);
        boolean hasRealCause = false;
        for (Throwable t = exception; t != null; t = t.getCause()) {
            assertTrue(!String.valueOf(t.getMessage()).contains("No transaction active"),
                    "the real error must not be replaced by a failing second rollback: " + t.getMessage());
            if (t instanceof NoSuchFileException) hasRealCause = true;
        }
        assertTrue(hasRealCause, "the missing association file should be in the cause chain");
    }
}
