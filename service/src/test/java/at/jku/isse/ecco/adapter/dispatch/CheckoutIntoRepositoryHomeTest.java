package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A checkout into the folder that holds the repository is allowed: the repository directory is the
 * one entry the base directory may have. It was recognised only by the exact path the repository
 * was opened with, so a repository opened through a symlink (on macOS, /var is /private/var) or by
 * a relative path failed with "Current base directory must be empty for checkout operation."
 */
public class CheckoutIntoRepositoryHomeTest {

    /** Opened through a symlinked folder, checked out into the real one. */
    @Test
    @Timeout(60)
    public void aRepositoryOpenedThroughASymlinkChecksOutIntoItsOwnFolder(@TempDir Path temp) throws Exception {
        Path tmp = temp.toRealPath();
        Path home = Files.createDirectories(tmp.resolve("real"));
        Path link = Files.createSymbolicLink(tmp.resolve("link"), home);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(link.resolve(".ecco"));
            service.init();
            Path variant = Files.createDirectories(tmp.resolve("v1"));
            Files.writeString(variant.resolve("a.txt"), "a\n");
            service.setBaseDir(variant);
            service.commit("A", "A.1");

            service.setBaseDir(service.getRepositoryHomeDir());
            assertEquals(home, service.getBaseDir(), "the home is the real path");
            service.checkout("A.1");

            assertEquals("a\n", Files.readString(home.resolve("a.txt")));
        }
    }

    /** The other way round: opened by the real path, checked out through the symlink. */
    @Test
    @Timeout(60)
    public void aCheckoutThroughASymlinkIntoTheRepositorysFolder(@TempDir Path temp) throws Exception {
        Path tmp = temp.toRealPath();
        Path home = Files.createDirectories(tmp.resolve("real"));
        Path link = Files.createSymbolicLink(tmp.resolve("link"), home);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(home.resolve(".ecco"));
            service.init();
            Path variant = Files.createDirectories(tmp.resolve("v1"));
            Files.writeString(variant.resolve("a.txt"), "a\n");
            service.setBaseDir(variant);
            service.commit("A", "A.1");

            service.setBaseDir(link);
            service.checkout("A.1");

            assertEquals("a\n", Files.readString(home.resolve("a.txt")));
        }
    }

    /** Any other entry still makes the folder non-empty. */
    @Test
    @Timeout(60)
    public void anotherEntryStillRefusesTheCheckout(@TempDir Path temp) throws Exception {
        Path tmp = temp.toRealPath();
        Path home = Files.createDirectories(tmp.resolve("real"));
        Path link = Files.createSymbolicLink(tmp.resolve("link"), home);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(link.resolve(".ecco"));
            service.init();
            Path variant = Files.createDirectories(tmp.resolve("v1"));
            Files.writeString(variant.resolve("a.txt"), "a\n");
            service.setBaseDir(variant);
            service.commit("A", "A.1");

            Files.writeString(home.resolve("stray.txt"), "x\n");
            service.setBaseDir(service.getRepositoryHomeDir());
            Exception refused = assertThrows(Exception.class, () -> service.checkout("A.1"));
            assertTrue(String.valueOf(rootMessage(refused)).contains("must be empty"), String.valueOf(rootMessage(refused)));
            assertFalse(Files.exists(home.resolve("a.txt")));
        }
    }

    @Test
    public void isRepositoryDirComparesFilesNotPaths(@TempDir Path temp) throws Exception {
        Path tmp = temp.toRealPath();
        Path repo = Files.createDirectories(tmp.resolve("real/.ecco"));
        Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("real"));

        assertTrue(DispatchWriter.isRepositoryDir(repo, link.resolve(".ecco")));
        assertTrue(DispatchWriter.isRepositoryDir(link.resolve(".ecco"), repo));
        assertFalse(DispatchWriter.isRepositoryDir(tmp.resolve("real"), repo));
        assertFalse(DispatchWriter.isRepositoryDir(repo, tmp.resolve("missing/.ecco")), "a repository directory that does not exist");
        assertFalse(DispatchWriter.isRepositoryDir(repo, null));
    }

    private static String rootMessage(Throwable t) {
        String message = t.getMessage();
        for (Throwable c = t.getCause(); c != null; c = c.getCause())
            if (c.getMessage() != null)
                message = c.getMessage();
        return message;
    }
}
