package at.jku.isse.ecco.gui;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Message boxes open over the window that has the focus - they had no owner, and opened in the
 * middle of the main screen, also when the dialog they were about was elsewhere.
 */
public class OwnedAlertTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(30)
    public void anAlertIsOwnedByTheFocusedWindow() throws Exception {
        Stage stage = onFx(() -> {
            Stage s = new Stage();
            s.setScene(new Scene(new Pane(), 200, 100));
            s.show();
            s.requestFocus();
            return s;
        });
        try {
            waitUntil("the focus", stage::isFocused);
            assertSame(stage, onFx(() -> new OwnedAlert(Alert.AlertType.INFORMATION, "x").getOwner()));
            assertSame(stage, onFx(() -> new ExceptionAlert(new RuntimeException("y")).getOwner()));
            assertSame(stage, onFx(() -> new at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))).getOwner()));
        } finally {
            onFx(stage::close);
        }
    }
}
