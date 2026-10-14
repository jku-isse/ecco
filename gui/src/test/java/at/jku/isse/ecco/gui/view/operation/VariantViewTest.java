package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The "add variant" operation: Add puts a variant with the typed configuration and name into the
 * repository and ends on the success header; an empty or invalid configuration adds nothing and
 * ends on the error header; Cancel closes the window.
 */
public class VariantViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private static List<TextField> fields(VariantView view) {
        return findAll(view, n -> n instanceof TextField).stream().map(n -> (TextField) n).toList();
    }

    private static Set<Set<String>> variants(EccoService service) {
        return service.getRepository().getVariants().stream()
                .map(v -> Arrays.stream(v.getConfiguration().getFeatureRevisions())
                        .map(FeatureRevision::toString).collect(Collectors.toSet()))
                .collect(Collectors.toSet());
    }

    private static boolean done(VariantView view) {
        return find(view, n -> n instanceof Button b && "Done".equals(b.getText())) != null && !view.toolBar.isDisable();
    }

    /** Add stores a variant with the typed configuration and name, and the view ends on the success header. */
    @Test
    @Timeout(60)
    public void addPutsTheTypedVariantIntoTheRepository(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            VariantView view = onFx(() -> new VariantView(service));
            assertEquals("Configuration", onFx(() -> view.headerLabel.getText()));

            onFx(() -> {
                fields(view).get(0).setText("A.1, B.1");
                fields(view).get(1).setText("my variant");
                button(view, "Add").fire();
            });
            waitUntil("the success header", () -> done(view) && view.toolBar.getStyleClass().contains("success"));

            assertEquals(Set.of(Set.of("A.1"), Set.of("A.1", "B.1")), variants(service));
            Variant added = service.getRepository().getVariants().stream()
                    .filter(v -> v.getConfiguration().getFeatureRevisions().length == 2).findFirst().orElseThrow();
            assertEquals("my variant", added.getName());
            assertFalse(onFx(() -> view.toolBar.getStyleClass().contains("error")));
        }
    }

    /** An empty configuration adds nothing and ends on the error header, not the success one. */
    @Test
    @Timeout(60)
    public void anEmptyConfigurationAddsNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            VariantView view = onFx(() -> new VariantView(service));

            onFx(() -> button(view, "Add").fire());
            waitUntil("the operation is over", () -> done(view));

            assertEquals(Set.of(Set.of("A.1")), variants(service));
            assertError(view);
        }
    }

    /** A configuration that does not parse fails the operation instead of throwing on the FX thread. */
    @Test
    @Timeout(60)
    public void anInvalidConfigurationEndsOnTheErrorHeader(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            VariantView view = onFx(() -> new VariantView(service));

            onFx(() -> {
                fields(view).get(0).setText("A.1, , ,");
                button(view, "Add").fire();
            });
            waitUntil("the operation is over", () -> done(view));

            assertEquals(Set.of(Set.of("A.1")), variants(service));
            assertError(view);
        }
    }

    private static void assertError(VariantView view) throws Exception {
        List<String> styles = onFx(() -> List.copyOf(view.toolBar.getStyleClass()));
        assertTrue(styles.contains("error"), "error header: " + styles);
        assertFalse(styles.contains("success"), "no success header: " + styles);
    }

    /** Cancel closes the window the view is in. */
    @Test
    @Timeout(60)
    public void cancelClosesTheWindow(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Stage stage = onFx(() -> {
                Stage s = new Stage();
                s.setScene(new Scene(new VariantView(service)));
                s.show();
                return s;
            });
            try {
                onFx(() -> button((Node) stage.getScene().getRoot(), "Cancel").fire());
                assertFalse(onFx(stage::isShowing));
            } finally {
                onFx(stage::hide);
            }
        }
    }
}
