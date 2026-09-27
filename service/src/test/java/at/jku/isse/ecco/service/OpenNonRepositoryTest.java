package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * open() only checked that the repository directory exists, so any existing directory - e.g. an
 * empty folder picked by mistake - opened as an empty repository (and got .ignores/.adapters files
 * written into it), and forking from one silently produced an empty repository. A repository
 * directory is never empty (init() writes into it right away), so open() now rejects an empty
 * directory (or a plain file) instead.
 */
public class OpenNonRepositoryTest {

    @Test
    @Timeout(30)
    public void openingAnEmptyDirectoryFailsAndWritesNothing() throws Exception {
        Path emptyDir = Files.createDirectories(Files.createTempDirectory("open-non-repository").resolve(".ecco"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(emptyDir);
            assertThrows(EccoException.class, service::open);
            assertFalse(service.isInitialized());
        }
        try (Stream<Path> files = Files.list(emptyDir)) {
            assertEquals(0, files.count(), "nothing may be written into a directory that isn't a repository");
        }
    }

    @Test
    @Timeout(30)
    public void forkingFromAnEmptyDirectoryFailsAndLeavesNoRepository() throws Exception {
        Path workDir = Files.createTempDirectory("fork-from-empty");
        Path emptyOrigin = Files.createDirectories(workDir.resolve("origin").resolve(".ecco"));
        Path target = Files.createDirectories(workDir.resolve("target")).resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(target);
            assertThrows(EccoException.class, () -> service.fork(emptyOrigin));
            assertFalse(Files.exists(target));
        }
    }

    @Test
    @Timeout(30)
    public void initAndReopenStillWork() throws Exception {
        Path repoDir = Files.createTempDirectory("init-reopen").resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            assertDoesNotThrow(service::init);
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            assertDoesNotThrow(service::open);
        }
    }
}
