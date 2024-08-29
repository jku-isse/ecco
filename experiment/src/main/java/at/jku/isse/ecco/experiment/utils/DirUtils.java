package at.jku.isse.ecco.experiment.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class DirUtils {
    public static void createDir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void deleteDir(Path path) {
        try {
            File dir = path.toFile();
            if (dir.exists()) FileUtils.deleteDirectory(dir);
        }
        catch (IOException e){
            throw new RuntimeException(String.format("Could not delete directory %s: ", path) + e.getMessage());
        }
    }

    public static List<Path> getSubDirectoryPaths(Path basePath){
        try (Stream<Path> stream = Files.list(basePath)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}
