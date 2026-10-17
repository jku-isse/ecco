package at.jku.isse.ecco.gui.io;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Asks before a checkout deletes the contents of a folder that is not empty. It said "Base
 * directory is not empty. Delete contents?" with Yes the default button - without naming the
 * folder, which was by default the repository's own, the user's working files: one Enter deleted
 * them. Now it names the folder and how much is in it, warns when it is the repository's folder,
 * and Cancel is the default.
 */
public class DeleteDirectoryContentsDialog extends Alert {

	/** The button that deletes - never the default one. */
	public static final ButtonType DELETE = new ButtonType("Delete Contents", ButtonBar.ButtonData.OTHER);

	private final Path directory;

	public DeleteDirectoryContentsDialog(Path directory) {
		super(Alert.AlertType.WARNING, message(directory), DELETE, ButtonType.CANCEL);
		this.directory = directory;
		this.setTitle("Delete Contents");
		this.setHeaderText("Delete the contents of " + directory.getFileName() + "?");
		((Button) this.getDialogPane().lookupButton(DELETE)).setDefaultButton(false);
		((Button) this.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
		this.getDialogPane().setMinWidth(480);
	}

	/** What is deleted, and - for the repository's own folder - whose files those are. Package-visible for testing. */
	static String message(Path directory) {
		long entries = entries(directory);
		String text = directory + " has " + entries + (entries == 1 ? " file or folder" : " files and folders")
				+ ". The checkout deletes them first - this cannot be undone.";
		if (Files.isDirectory(directory.resolve(".ecco")))
			text += "\n\nThis is the repository's own folder: the files you work on there would be deleted (only .ecco is kept). "
					+ "Cancel, and choose an empty folder to check out into.";
		return text;
	}

	/** The entries of [directory] the deletion removes: all but .ecco. */
	private static long entries(Path directory) {
		try (Stream<Path> children = Files.list(directory)) {
			return children.filter(child -> !child.getFileName().toString().equals(".ecco")).count();
		} catch (IOException e) {
			return 0;
		}
	}

	public boolean showBlocked() throws IOException {
		showAndWait();

		if (getResult() == DELETE) {
			Files.walkFileTree(directory, new DeleteDirectoryVisitor(directory));
			return true;
		}

		return false;
	}
}
