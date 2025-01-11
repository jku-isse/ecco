package at.jku.isse.ecco.experiment.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
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

    public static void deleteAndCreateDir(Path path){
        deleteDir(path);
        createDir(path);
    }

    public static List<Path> getSubDirectoryPaths(Path basePath){
        try (Stream<Path> stream = Files.list(basePath)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static List<Path> getFilesInFolder(Path folderPath) {
        try (Stream<Path> paths = Files.list(folderPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static boolean compareFolders(File folder1, File folder2) throws IOException {
        // Check if both inputs are directories
        if (!folder1.isDirectory() || !folder2.isDirectory()) {
            throw new IllegalArgumentException("Both inputs must be directories.");
        }

        // List files in both folders
        File[] files1 = folder1.listFiles();
        File[] files2 = folder2.listFiles();

        // Check for null (e.g., permission issues)
        if (files1 == null || files2 == null) {
            return false;
        }

        // Check if the number of files/subdirectories match
        if (files1.length != files2.length) {
            return false;
        }

        // Sort files by name for deterministic comparison
        java.util.Arrays.sort(files1, (a, b) -> a.getName().compareTo(b.getName()));
        java.util.Arrays.sort(files2, (a, b) -> a.getName().compareTo(b.getName()));

        for (int i = 0; i < files1.length; i++) {
            File file1 = files1[i];
            File file2 = files2[i];

            // Check if file names match
            if (!file1.getName().equals(file2.getName())) {
                return false;
            }

            // Recursively compare subdirectories
            if (file1.isDirectory() && file2.isDirectory()) {
                if (!compareFolders(file1, file2)) {
                    return false;
                }
            } else if (file1.isFile() && file2.isFile()) {
                // Compare file contents
                if (!(Files.mismatch(file1.toPath(), file2.toPath()) == -1)) {
                    return false;
                }
            } else {
                // One is a file, the other is a directory
                return false;
            }
        }

        return true;
    }
}
