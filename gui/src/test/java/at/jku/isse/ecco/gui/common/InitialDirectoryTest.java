package at.jku.isse.ecco.gui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A folder chooser starts in a folder only where JavaFX takes it: JavaFX asks {@code File#isDirectory},
 * which is false for the empty path - the current directory to {@code Files.isDirectory} - and threw
 * "Folder parameter must be a valid folder" on the FX thread when a dialog's folder field was empty.
 */
public class InitialDirectoryTest {

    @Test
    public void anEmptyPathIsNoFolderToStartIn() {
        assertTrue(Files.isDirectory(Path.of("")), "what the dialogs asked");
        assertFalse(new File("").isDirectory(), "what JavaFX asks");
        assertNull(InitialDirectory.of(Path.of("")));
        assertNull(InitialDirectory.of(""));
        assertNull(InitialDirectory.of((String) null));
        assertNull(InitialDirectory.of((Path) null));
    }

    @Test
    public void aFolderIsStartedInAbsoluteAFileOrAMissingFolderNot(@TempDir Path tmp) throws Exception {
        File folder = InitialDirectory.of(tmp.toString());
        assertNotNull(folder);
        assertTrue(folder.isAbsolute() && folder.isDirectory());
        assertNull(InitialDirectory.of(Files.writeString(tmp.resolve("f.txt"), "x")));
        assertNull(InitialDirectory.of(tmp.resolve("missing")));
        assertNull(InitialDirectory.of("\0bad"));
        // relative: where the GUI runs, absolute
        File here = InitialDirectory.of(".");
        assertNotNull(here);
        assertTrue(here.isAbsolute());
    }
}
