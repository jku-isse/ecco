package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DispatchReader.readDirectories() used to catch an IOException from Files.list() (e.g. a directory
 * that can't be listed due to permissions) with only e.printStackTrace(), then silently return null
 * -- treating that directory as if it were empty/absent instead of surfacing an error. A commit
 * would then proceed as if the unreadable content simply didn't exist, unlike every other
 * IOException handler in this codebase, which wraps and rethrows as EccoException. Fixed to do the
 * same here.
 */
public class DispatchReaderUnreadableDirectoryTest {

    @Test
    @Timeout(30)
    public void commitFailsLoudlyInsteadOfSilentlySkippingAnUnreadableDirectory() throws IOException {
        Path workDir = Files.createTempDirectory("dispatch-reader-unreadable-dir");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("normal.txt"), "normal\n");

        Path secretDir = contentDir.resolve("secret");
        Files.createDirectories(secretDir);
        Files.writeString(secretDir.resolve("hidden.txt"), "hidden\n");
        // strip all permissions from the directory itself (not its contents) so Files.list() on it
        // throws AccessDeniedException -- posix-only, matches this dev/CI environment.
        Files.setPosixFilePermissions(secretDir, EnumSet.noneOf(PosixFilePermission.class));

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);

            EccoException exception = assertThrows(EccoException.class, () -> service.commit("commit", ""));
            assertTrue(exception.getMessage() != null || exception.getCause() != null,
                    "the failure must be surfaced as a real error, not silently swallowed");
        } finally {
            // restore permissions so temp-directory cleanup can actually delete it afterward
            Files.setPosixFilePermissions(secretDir, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }
}
