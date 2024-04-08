package at.jku.isse.ecco.gui.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class DeleteDirectoryVisitor implements FileVisitor<Path> {
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

        if (files != null && files.length == 0 && !dir.toAbsolutePath().toString().contains(".ecco")) {
            Files.delete(dir);
        }

        return FileVisitResult.CONTINUE;
    }
}
