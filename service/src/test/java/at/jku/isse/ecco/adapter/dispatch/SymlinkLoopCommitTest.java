package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DispatchReader follows directory symlinks (Files.isDirectory), so a symlink pointing back at one
 * of its own ancestors made a commit recurse until StackOverflowError. Directories already being
 * walked are now skipped when reached again through a symlink.
 */
public class SymlinkLoopCommitTest {

    @Test
    @Timeout(60)
    public void aSymlinkLoopDoesNotBreakTheCommit() throws Exception {
        Path workDir = Files.createTempDirectory("symlink-loop");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("a.txt"), "a\n");
        Path sub = Files.createDirectories(content.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "b\n");
        Files.createSymbolicLink(sub.resolve("up"), Paths.get(".."));   // sub/up -> content
        Files.createSymbolicLink(content.resolve("self"), Paths.get(".")); // self -> content

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            assertDoesNotThrow(() -> service.commit("commit", "A"));
            assertEquals(1, service.getRepository().getCommits().size());
        }
    }
}
