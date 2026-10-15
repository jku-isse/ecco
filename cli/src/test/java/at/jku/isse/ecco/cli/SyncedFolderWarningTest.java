package at.jku.isse.ecco.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** init, fork and status warn, on stderr, about a repository in a folder a cloud service synchronizes. */
public class SyncedFolderWarningTest {

    private static String errors(Path dir, String... args) {
        PrintStream err = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));
        try {
            assertEquals(0, Main.run(args, dir));
        } finally {
            System.setErr(err);
        }
        return captured.toString();
    }

    @Test
    @Timeout(60)
    public void initAndStatusWarnInASynchronizedFolderOnly(@TempDir Path tmp) throws Exception {
        String home = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tmp.resolve("home").toString());
            Path synced = Files.createDirectories(tmp.resolve("home/Library/CloudStorage/Dropbox/shop"));
            String init = errors(synced, "init");
            assertTrue(init.contains("WARNING: The repository " + synced.resolve(".ecco")), init);
            assertTrue(init.contains("synchronized by Dropbox"), init);
            assertTrue(errors(synced, "status").contains("synchronized by Dropbox"));

            Path plain = Files.createDirectories(tmp.resolve("work/shop"));
            assertEquals("", errors(plain, "init"));
            assertEquals("", errors(plain, "status"));
        } finally {
            System.setProperty("user.home", home);
        }
    }
}
