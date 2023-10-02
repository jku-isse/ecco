package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * This FileVisitor walks a directory and accepts files of types defined in
 * {@link LilypondPlugin}. If subdirectory contains a '.config' file it will
 * be copied to output directory.
 */
public class FeatureFileVisitor implements FileVisitor<Path> {

    private final Path outDir;
    private final Consumer<Path> fileConsumer;
    private Path root;

    public FeatureFileVisitor(Path output, Consumer<Path> file) {
        outDir = output;
        fileConsumer = file;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (null == root) {
            root = dir;

        } else {
            Path p  = outDir.resolve(root.relativize(dir));
            Files.createDirectory(p);
            System.out.println("serialize " + p);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = file.getFileName().toString();
        String extension = name.substring(name.lastIndexOf(".") + 1);
        if (Arrays.asList(LilypondPlugin.getFileTypes()).contains(extension)) {
            fileConsumer.accept(file);

        } else if (name.equals(".config")) {
            Files.copy(file, outDir.resolve(root.relativize(file)));
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        System.out.println("could not open file " + file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }
}
