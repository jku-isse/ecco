package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Status view decides: whether it is usable (only with an initialized service), what it
 * says about the service, and which base and repository directory it shows - also after they change.
 * It only reads the service; it writes no preferences.
 */
public class SettingsViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void anUninitializedServiceLeavesTheViewDisabled(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            SettingsView view = onFx(() -> new SettingsView(service));

            assertTrue(onFx(view::isDisable), "disabled at once, not only after the next event");
            assertEquals("The ECCO Service has not been initialized yet.", onFx(() -> status(view)));
            assertEquals(tmp.resolve(".ecco").toString(), onFx(() -> fields(view).get(1).getText()));
        }
    }

    @Test
    @Timeout(60)
    public void anInitializedServiceShowsItsDirectoriesReadOnly(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            SettingsView view = onFx(() -> new SettingsView(service));

            assertFalse(onFx(view::isDisable));
            assertEquals("The ECCO Service is initialized.", onFx(() -> status(view)));
            assertEquals(List.of(service.getBaseDir().toString(), tmp.resolve("repo").resolve(".ecco").toString()),
                    onFx(() -> fields(view).stream().map(TextField::getText).toList()));
            assertTrue(onFx(() -> fields(view).stream().noneMatch(TextField::isEditable)));
        }
    }

    @Test
    @Timeout(60)
    public void followsTheServiceWhenTheBaseDirectoryChangesOrTheRepositoryCloses(@TempDir Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        try {
            SettingsView view = onFx(() -> new SettingsView(service));
            Path other = Files.createDirectories(tmp.resolve("other"));
            service.setBaseDir(other);
            waitUntil("the new base directory", () -> fields(view).get(0).getText().equals(other.toString()));

            service.close();
            waitUntil("disabled once closed", view::isDisable);
            assertEquals("The ECCO Service has not been initialized yet.", onFx(() -> status(view)));
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Base directory first, then repository directory. */
    private static List<TextField> fields(SettingsView view) {
        return findAll(view, n -> n instanceof TextField).stream().map(n -> (TextField) n).toList();
    }

    private static String status(SettingsView view) {
        List<Label> labels = findAll(view, n -> n instanceof Label).stream().map(n -> (Label) n).toList();
        return labels.get(labels.size() - 1).getText();
    }
}
