package at.jku.isse.ecco.gui.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Delete contents?" before a checkout keeps a repository (.ecco) inside the directory and deletes
 * everything else. It used to search the whole absolute path for ".ecco", so a directory under, say,
 * ~/.ecco-work kept all its files and the checkout wrote into a directory that was not empty.
 */
public class DeleteDirectoryVisitorTest {

    @Test
    public void deletesTheContentsButKeepsTheDirectoryAndARepositoryInIt(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("out"));
        Files.writeString(Files.createDirectories(root.resolve("src/main")).resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        Files.writeString(Files.createDirectories(root.resolve(".ecco")).resolve("id"), "repository");

        Files.walkFileTree(root, new DeleteDirectoryVisitor(root));

        assertTrue(Files.isDirectory(root));
        assertFalse(Files.exists(root.resolve("src")));
        assertFalse(Files.exists(root.resolve("b.txt")));
        assertTrue(Files.exists(root.resolve(".ecco/id")), "the repository stays");
    }

    @Test
    public void aDirectoryUnderAnEccoLikeNameIsClearedToo(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve(".ecco-work/out"));
        Files.writeString(root.resolve("old.txt"), "old");
        Files.writeString(Files.createDirectories(root.resolve("my.ecco.notes")).resolve("n.txt"), "n");

        Files.walkFileTree(root, new DeleteDirectoryVisitor(root));

        assertTrue(Files.isDirectory(root));
        try (var left = Files.list(root)) {
            assertEquals(0, left.count(), "only a directory named .ecco is kept");
        }
    }
}
