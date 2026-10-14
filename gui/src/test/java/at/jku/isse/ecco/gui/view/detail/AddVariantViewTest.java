package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The variant detail pane: empty and disabled without a variant, showing the variant's
 * configuration once one is given, and empty again when it is cleared.
 */
public class AddVariantViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private static List<TextField> fields(Node root) {
        return findAll(root, n -> n instanceof TextField).stream().map(n -> (TextField) n).toList();
    }

    /** Without a variant the details are not shown and the toolbar is disabled. */
    @Test
    @Timeout(60)
    public void showsNothingWithoutAVariant() throws Exception {
        onFx(() -> {
            AddVariantView view = new AddVariantView();
            assertNull(view.getCenter());
            assertTrue(view.getTop().isDisabled());
        });
    }

    /** A variant shows its configuration (editable) and enables the toolbar; clearing it empties the field again. */
    @Test
    @Timeout(60)
    public void showsTheVariantsConfigurationAndClearsItAgain(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1, B.1", Map.of("a.txt", "a\n"));
            Variant variant = service.getRepository().getVariants().iterator().next();
            onFx(() -> {
                AddVariantView view = new AddVariantView();
                view.showVariant(variant);
                assertNotNull(view.getCenter());
                assertFalse(view.getTop().isDisabled());
                List<TextField> fields = fields(view.getCenter());
                assertEquals(2, fields.size(), "name and configuration");
                assertEquals(variant.getConfiguration().toString(), fields.get(1).getText());
                assertTrue(fields.get(1).isEditable());
                // the name field is never filled from the variant
                assertEquals("", fields.get(0).getText());

                Node centerWhileShown = view.getCenter();
                view.showVariant(null);
                assertNull(view.getCenter());
                assertTrue(view.getTop().isDisabled());
                assertEquals("", fields(centerWhileShown).get(1).getText());
            });
        }
    }
}
