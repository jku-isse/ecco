package at.jku.isse.ecco.gui.common;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * The folder a {@code DirectoryChooser} may start in. JavaFX takes it only where
 * {@code File#isDirectory} holds, else it throws "Folder parameter must be a valid folder" from
 * {@code showDialog}; the dialogs asked {@code Files.isDirectory}, which is true for the empty path
 * (the current directory) - an empty folder field made the button throw on the FX thread.
 */
public final class InitialDirectory {

    private InitialDirectory() {
    }

    /** [text] as a folder to start in, absolute; null if it is none (empty, missing, a file, not a path). */
    public static File of(String text) {
        if (text == null || text.isBlank())
            return null;
        try {
            return of(Path.of(text.trim()));
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** [path] as a folder to start in, absolute; null if it is none. */
    public static File of(Path path) {
        if (path == null || path.toString().isEmpty())
            return null;
        File folder = path.toAbsolutePath().normalize().toFile();
        return folder.isDirectory() ? folder : null;
    }
}
