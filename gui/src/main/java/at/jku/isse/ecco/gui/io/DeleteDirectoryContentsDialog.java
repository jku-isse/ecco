package at.jku.isse.ecco.gui.io;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteDirectoryContentsDialog extends Alert {
    private final Path directory;

    public DeleteDirectoryContentsDialog(Path directory) {
        super(Alert.AlertType.CONFIRMATION,
                "Base directory is not empty. Delete contents?",
                ButtonType.YES,
                ButtonType.CANCEL);

        this.directory = directory;
    }

    public boolean showBlocked() throws IOException {
        showAndWait();

        if (getResult() == ButtonType.YES) {
            Files.walkFileTree(directory, new DeleteDirectoryVisitor());
            return true;
        }

        return false;
    }


}
