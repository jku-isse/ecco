package at.jku.isse.ecco.gui.io;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The question before a checkout deletes a folder's contents: it names the folder and how much is
 * in it, warns when it is the repository's own folder, and Enter cancels - Yes was the default
 * button, and the folder, by default the repository's own, was not named: one Enter deleted the
 * user's files.
 */
public class DeleteDirectoryContentsDialogTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    public void namesTheFolderAndCancelIsTheDefault(@TempDir Path tmp) throws Exception {
        Path folder = Files.createDirectories(tmp.resolve("out"));
        Files.writeString(folder.resolve("a.txt"), "a");
        Files.createDirectories(folder.resolve("sub"));

        DeleteDirectoryContentsDialog dialog = onFx(() -> new DeleteDirectoryContentsDialog(folder));
        assertEquals("Delete the contents of out?", onFx(dialog::getHeaderText));
        String text = onFx(dialog::getContentText);
        assertTrue(text.startsWith(folder + " has 2 files and folders."), text);
        assertFalse(text.contains("repository's own folder"), text);
        assertTrue(onFx(() -> ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).isDefaultButton()), "Enter cancels");
        assertFalse(onFx(() -> ((Button) dialog.getDialogPane().lookupButton(DeleteDirectoryContentsDialog.DELETE)).isDefaultButton()));
        assertEquals("Delete Contents", DeleteDirectoryContentsDialog.DELETE.getText());
    }

    @Test
    public void warnsWhenItIsTheRepositorysOwnFolder(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve(".ecco"));
        Files.writeString(tmp.resolve("work.txt"), "mine");

        String text = DeleteDirectoryContentsDialog.message(tmp);
        assertTrue(text.startsWith(tmp + " has 1 file or folder."), ".ecco is kept, not counted: " + text);
        assertTrue(text.contains("This is the repository's own folder: the files you work on there would be deleted"), text);
    }
}
