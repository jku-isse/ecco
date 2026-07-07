package at.jku.isse.ecco.util.directory;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class DirectoryUtils {

    /**
     * Deletes a folder if it exists. The folder will be deleted independent of whether it is empty or not.
     * @param folderPath Path to folder. Must not be null.
     */
    public static void deleteFolderIfItExists(Path folderPath) throws DirectoryException {
        assert(folderPath != null);

        if (!Files.exists(folderPath)) {
            return;
        }
        if (!Files.isDirectory(folderPath)){
            throw new DirectoryException(String.format("Given path does not point to a folder: %s", folderPath));
        }
        try {
            FileUtils.deleteDirectory(folderPath.toFile());
        } catch (IOException e) {
            throw new DirectoryException(e);
        }
    }

    /**
     * Delete a folder if it exists and independent of whether it is empty or not.
     * Create the folder again afterward without content.
     * @param folderPath Path to folder to delete and create again. Must not be null.
     */
    public static void deleteAndCreateFolder(Path folderPath) throws DirectoryException {
        assert(folderPath != null);

        deleteFolderIfItExists(folderPath);
        try {
            Files.createDirectory(folderPath);
        } catch (IOException e) {
            throw new DirectoryException(e);
        }
    }

    /**
     * Get a list of paths of subdirectories of the given directory.
     * @param folderPath Must not be null.
     * @return List of paths of subdirectories of given directory.
     */
    public static List<Path> getSubDirectoryPaths(Path folderPath) throws DirectoryException {
        assert(folderPath != null);

        try (Stream<Path> stream = Files.list(folderPath)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e){
            throw new DirectoryException(e);
        }
    }

    /**
     * Compare two folders and create two if they equal content. The folder names are not checked.
     * @param folderPath1 first folder. Must not be Null.
     * @param folderPath2 second folder. Must not be Null.
     * @return true if folders are equal and false if not.
     */
    public static boolean foldersAreEqual(Path folderPath1, Path folderPath2) throws IOException {
        assert(folderPath1 != null);
        assert(folderPath2 != null);

        File folder1 = folderPath1.toFile();
        File folder2 = folderPath2.toFile();

        if (!folder1.isDirectory() || !folder2.isDirectory()) {
            throw new IllegalArgumentException("Both inputs must be directories.");
        }

        File[] files1 = folder1.listFiles();
        File[] files2 = folder2.listFiles();

        assert files1 != null;
        assert files2 != null;
        if (files1.length != files2.length) {
            return false;
        }

        java.util.Arrays.sort(files1, Comparator.comparing(File::getName));
        java.util.Arrays.sort(files2, Comparator.comparing(File::getName));

        for (int i = 0; i < files1.length; i++) {
            File file1 = files1[i];
            File file2 = files2[i];

            if (!file1.getName().equals(file2.getName())) {
                return false;
            }

            if (file1.isDirectory() && file2.isDirectory()) {
                if (!foldersAreEqual(file1.toPath(), file2.toPath())) {
                    return false;
                }
            } else if (file1.isFile() && file2.isFile()) {
                if (!(Files.mismatch(file1.toPath(), file2.toPath()) == -1)) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }
}
