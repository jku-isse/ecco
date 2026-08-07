package at.jku.isse.ecco.gui.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Deletes everything under {@code root}, including now-empty subdirectories, but never {@code root}
 * itself -- "delete contents" ({@link DeleteDirectoryContentsDialog}) means the directory stays, only
 * what's in it goes. Without excluding {@code root}, a checkout base directory containing no
 * ".ecco" subfolder (the ordinary case -- that lives in the repository, not the checkout output
 * dir) got deleted along with its last file, so a subsequent re-checkout into it failed with
 * "Base directory does not exist." (DispatchWriter#write requires the target to already exist).
 */
public class DeleteDirectoryVisitor implements FileVisitor<Path> {
    private final Path root;

    public DeleteDirectoryVisitor(Path root) {
        this.root = root;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!file.toAbsolutePath().toString().contains(".ecco")) {
            Files.delete(file);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        File[] files = dir.toFile().listFiles();

        if (files != null && files.length == 0 && !dir.equals(this.root) && !dir.toAbsolutePath().toString().contains(".ecco")) {
            Files.delete(dir);
        }

        return FileVisitResult.CONTINUE;
    }
}
