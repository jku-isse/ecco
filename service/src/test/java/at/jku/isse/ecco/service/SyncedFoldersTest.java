package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Which folders a cloud service synchronizes - where reading a repository can time out (the system
 * fetches an evicted file's content) - and that a failed read names the repository, its reason and,
 * in such a folder, what to do.
 */
public class SyncedFoldersTest {

    @Test
    public void synchronizedFoldersAreRecognizedByTheirLocation(@TempDir Path tmp) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path icloud = Files.createDirectories(home.resolve("Library/Mobile Documents/com~apple~CloudDocs"));
        Files.createDirectories(home.resolve("Library/CloudStorage/OneDrive-Personal"));
        Files.createDirectories(home.resolve("Library/CloudStorage/GoogleDrive-me@example.org"));

        assertEquals(Optional.of("iCloud Drive"), SyncedFolders.provider(icloud.resolve("repo/.ecco"), home));
        assertEquals(Optional.of("OneDrive"), SyncedFolders.provider(home.resolve("Library/CloudStorage/OneDrive-Personal/x/.ecco"), home));
        assertEquals(Optional.of("Google Drive"), SyncedFolders.provider(home.resolve("Library/CloudStorage/GoogleDrive-me@example.org/My Drive/r"), home));
        assertEquals(Optional.of("Dropbox"), SyncedFolders.provider(tmp.resolve("Dropbox/work/.ecco"), home));
        assertEquals(Optional.of("OneDrive"), SyncedFolders.provider(tmp.resolve("OneDrive - Company/r"), home));
        assertEquals(Optional.empty(), SyncedFolders.provider(home.resolve("Repositories/r/.ecco"), home));

        // Desktop and Documents only when macOS syncs them (their folder exists in iCloud Drive)
        assertEquals(Optional.empty(), SyncedFolders.provider(home.resolve("Desktop/r/.ecco"), home));
        Files.createDirectories(icloud.resolve("Desktop"));
        assertEquals(Optional.of("iCloud Drive (Desktop is synced)"), SyncedFolders.provider(home.resolve("Desktop/r/.ecco"), home));
        assertEquals(Optional.empty(), SyncedFolders.provider(home.resolve("Documents/r/.ecco"), home));

        // through a symlink: where the files really are
        Path link = Files.createSymbolicLink(home.resolve("work"), Files.createDirectories(icloud.resolve("work")));
        assertEquals(Optional.of("iCloud Drive"), SyncedFolders.provider(link.resolve("r/.ecco"), home));
    }

    /** A repository whose files cannot be read: the error says which and why, and in a synchronized folder what to do. */
    @Test
    public void aFailedReadNamesTheRepositoryAndInASynchronizedFolderWhatToDo(@TempDir Path tmp) throws Exception {
        String home = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tmp.resolve("home").toString());
            for (Path dir : new Path[]{tmp.resolve("plain"), tmp.resolve("home/Library/Mobile Documents/com~apple~CloudDocs/r")}) {
                Path repositoryDir = Files.createDirectories(dir).resolve(".ecco");
                try (EccoService service = new EccoService()) {
                    service.setRepositoryDir(repositoryDir);
                    service.init();
                }
                // the id file unreadable, as a timed-out fetch leaves it
                Path id = repositoryDir.resolve("id");
                assumeTrue(id.toFile().setReadable(false) && !Files.isReadable(id), "file permissions not enforced (root?)");
                try (EccoService service = new EccoService()) {
                    service.setRepositoryDir(repositoryDir);
                    EccoException failure = assertThrows(EccoException.class, () -> {
                        service.open();
                        service.getRepository();
                    });
                    String messages = "";
                    for (Throwable t = failure; t != null; t = t.getCause())
                        messages += t.getMessage() + "\n";
                    assertTrue(messages.contains("Could not read the repository " + repositoryDir.toAbsolutePath().normalize() + ": "), messages);
                    assertEquals(dir.startsWith(tmp.resolve("home")), messages.contains("synchronized by iCloud Drive"), messages);
                } finally {
                    id.toFile().setReadable(true);
                }
            }
        } finally {
            System.setProperty("user.home", home);
        }
    }
}
