package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Neither init nor fork creates a repository where one exists (FR-L1b, FR-D2b), and neither runs on
 * a service that already has a repository open; the existing repository, and a fork's origin, stay
 * as they were.
 */
public class ExistingRepositoryRefusalTest {

    @Test
    @Timeout(60)
    public void initAndForkOntoAnExistingRepositoryAreRefusedAndChangeNothing(@TempDir Path tmp) throws Exception {
        Path origin = repositoryWithACommit(tmp.resolve("origin"));
        Path existing = repositoryWithACommit(tmp.resolve("existing"));
        Map<String, String> originBefore = DirectoryFingerprint.of(origin);
        Map<String, String> existingBefore = DirectoryFingerprint.of(existing);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(existing.resolve(".ecco"));
            EccoException e = assertThrows(EccoException.class, service::init);
            assertEquals("Repository already exists at this location.", e.getMessage());
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(existing.resolve(".ecco"));
            EccoException e = assertThrows(EccoException.class, () -> service.fork(origin));
            assertTrue(e.getMessage().startsWith("A repository already exists at the given location"), e.getMessage());
        }

        assertEquals(existingBefore, DirectoryFingerprint.of(existing));
        assertEquals(originBefore, DirectoryFingerprint.of(origin));
    }

    @Test
    @Timeout(60)
    public void initAndForkAreRefusedWhileARepositoryIsOpen(@TempDir Path tmp) throws Exception {
        Path origin = repositoryWithACommit(tmp.resolve("origin"));
        Path existing = repositoryWithACommit(tmp.resolve("existing"));

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(existing.resolve(".ecco"));
            service.open();
            Map<String, String> before = DirectoryFingerprint.of(existing);

            assertEquals("Service must not be initialized for init operation.",
                    assertThrows(EccoException.class, service::init).getMessage());
            assertEquals("Service must not be initialized for fork operation.",
                    assertThrows(EccoException.class, () -> service.fork(origin)).getMessage());

            assertEquals(before, DirectoryFingerprint.of(existing));
            assertEquals(1, service.getCommits().size(), "the open repository is still usable");
        }
    }

    private static Path repositoryWithACommit(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("a.txt"), "a\n");
            service.commit("a", "A");
        }
        return dir;
    }
}
