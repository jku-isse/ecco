package at.jku.isse.ecco.service;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * compose() (behind checkout/getAssociations/compareArtifacts) and checkConstraintViolations() read
 * the repository with repositoryDao.load() outside of any transaction. For the ser backend that just
 * returns whatever database the last transaction left loaded: none directly after open() (a
 * NullPointerException), or a stale one if another process committed meanwhile. They now read
 * inside a READ_ONLY transaction like every other read.
 */
public class ReadWithoutTransactionTest {

    @Test
    @Timeout(60)
    public void composingAndCheckingConstraintsRightAfterOpenWork() throws Exception {
        Path workDir = Files.createTempDirectory("read-without-transaction");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A");
        }

        Configuration empty = new SerEntityFactory().createConfiguration(new FeatureRevision[0]);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertDoesNotThrow(() -> service.getAssociations(empty));
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertDoesNotThrow(() -> service.checkConstraintViolations(empty));
        }
    }
}
