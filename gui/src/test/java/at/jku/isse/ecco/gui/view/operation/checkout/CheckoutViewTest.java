package at.jku.isse.ecco.gui.view.operation.checkout;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.io.DeleteDirectoryVisitor;
import at.jku.isse.ecco.gui.view.detail.CheckoutDetailView;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The decisions of the Checkout dialog: which configuration is checked out into which directory, the
 * question before checking out into a non-empty directory, the constraint-violation warning while a
 * configuration is typed and the question before checking it out, and what is shown afterwards. The
 * two questions are answered by the test instead of a modal dialog.
 */
public class CheckoutViewTest {

    private static final String STEP1 = "Directory and Configuration";
    private static final String VIOLATION = "Violates accepted constraint(s): ";

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void checksOutTheConfigurationIntoANewDirectoryAndLogsIt(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            ScriptedCheckoutView view = show(service);
            assertEquals(service.getRepositoryHomeDir().toString(), onFx(() -> view.baseDir().getText()), "defaults to the repository's directory");
            assertEquals(STEP1, onFx(() -> view.header()));

            Path out = tmp.resolve("out").resolve("nested");
            checkout(view, out, "BASE, A");
            waitUntil("checkout finished", () -> view.button("Done") != null);

            assertEquals("a\nb\n", Files.readString(out.resolve("f.txt")), "the directory was created and A checked out");
            assertFalse(Files.exists(out.resolve("g.txt")), "B's file is not checked out");
            assertEquals(out, service.getBaseDir());
            assertEquals(List.of(), view.clearAsked, "a new directory needs no question");
            assertEquals(List.of(), view.violationsAsked);

            assertTrue(onFx(() -> view.toolBarStyles().contains("success")));
            List<List<String>> log = onFx(view::log);
            assertTrue(log.stream().anyMatch(r -> r.get(0).equals("WRITE") && r.get(1).endsWith("f.txt")), log.toString());
            assertTrue(log.stream().noneMatch(r -> r.get(1).endsWith("g.txt")), log.toString());
            assertTrue(log.stream().anyMatch(r -> r.get(0).equals("SELECT")), log.toString());
            assertTrue(onFx(() -> view.result() instanceof CheckoutDetailView), "the checkout's details are shown");

            // the view stopped listening: a later checkout elsewhere doesn't add to its log
            int rows = log.size();
            service.setBaseDir(Files.createDirectories(tmp.resolve("other")));
            service.checkout("BASE, B");
            assertEquals(rows, (int) onFx(() -> view.log().size()));
        }
    }

    @Test
    @Timeout(60)
    public void aNonEmptyDirectoryIsOnlyUsedWhenItsContentsMayBeDeleted(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Path out = Files.createDirectories(tmp.resolve("out"));
            Files.writeString(out.resolve("keep.txt"), "keep\n");

            // declined: back to the first step, with what was entered, nothing written
            ScriptedCheckoutView view = show(service);
            view.clearAnswer = false;
            checkout(view, out, "BASE, A");
            assertEquals(List.of(out), view.clearAsked);
            assertEquals(STEP1, onFx(() -> view.header()));
            assertEquals(List.of(out.toString(), "BASE, A"), onFx(() -> List.of(view.baseDir().getText(), view.configuration().getText())),
                    "the entered directory and configuration are kept");
            assertTrue(Files.exists(out.resolve("keep.txt")));
            assertFalse(Files.exists(out.resolve("f.txt")));

            // accepted: the contents are deleted and the checkout goes ahead
            view.clearAnswer = true;
            onFx(() -> view.button("Checkout").fire());
            waitUntil("checkout finished", () -> view.button("Done") != null);
            assertEquals(List.of(out, out), view.clearAsked);
            assertFalse(Files.exists(out.resolve("keep.txt")));
            assertEquals("a\nb\n", Files.readString(out.resolve("f.txt")));

            // an existing empty directory needs no question
            Path empty = Files.createDirectories(tmp.resolve("empty"));
            ScriptedCheckoutView second = show(service);
            checkout(second, empty, "BASE, B");
            waitUntil("checkout finished", () -> second.button("Done") != null);
            assertEquals(List.of(), second.clearAsked);
            assertEquals("a\nc\n", Files.readString(empty.resolve("f.txt")));
        }
    }

    @Test
    @Timeout(120)
    public void aConstraintViolationIsShownWhileTypingAndAskedAboutBeforeCheckingOut(@TempDir Path tmp) throws Exception {
        try (EccoService service = coreExcludesExtra(tmp)) {
            ScriptedCheckoutView view = show(service);

            onFx(() -> view.configuration().setText("Core, Extra"));
            waitUntil("the warning", () -> view.warning().getText().startsWith(VIOLATION));
            assertTrue(onFx(() -> view.warning().getText()).contains("EXCLUDES"));
            onFx(() -> view.configuration().setText("Core"));
            waitUntil("the warning cleared", () -> view.warning().getText().isEmpty());
            onFx(() -> view.configuration().setText("Core, Extra"));
            waitUntil("the warning", () -> view.warning().getText().startsWith(VIOLATION));
            onFx(() -> view.configuration().setText(" "));
            waitUntil("the warning cleared for a blank configuration", () -> view.warning().getText().isEmpty());

            // declined: nothing checked out
            Path out = tmp.resolve("out");
            view.proceedAnswer = false;
            checkout(view, out, "Core, Extra");
            assertEquals(1, view.violationsAsked.size());
            assertTrue(view.violationsAsked.get(0).startsWith(VIOLATION) && view.violationsAsked.get(0).contains("EXCLUDES"), view.violationsAsked.toString());
            assertTrue(view.violationsAsked.get(0).endsWith(" / check out"));
            assertEquals(STEP1, onFx(() -> view.header()));
            assertFalse(Files.exists(out.resolve("core.txt")));
            assertEquals("Core, Extra", onFx(() -> view.configuration().getText()));
            waitUntil("the warning, back on the first step", () -> view.warning().getText().startsWith(VIOLATION));

            // confirmed: checked out anyway
            view.proceedAnswer = true;
            onFx(() -> view.button("Checkout").fire());
            waitUntil("checkout finished", () -> view.button("Done") != null);
            assertTrue(Files.exists(out.resolve("core.txt")) && Files.exists(out.resolve("extra.txt")));
            assertEquals(2, view.violationsAsked.size());

            // a configuration without violations is not asked about
            ScriptedCheckoutView second = show(service);
            checkout(second, tmp.resolve("core"), "Core");
            waitUntil("checkout finished", () -> second.button("Done") != null);
            assertEquals(List.of(), second.violationsAsked);
        }
    }

    @Test
    @Timeout(120)
    public void aKnownVariantFillsInItsConfigurationAndIsFlaggedWhenItViolatesAConstraint(@TempDir Path tmp) throws Exception {
        try (EccoService service = coreExcludesExtra(tmp)) {
            // a known variant that violates the accepted constraint
            service.addVariant("Core, Extra", "both", "");
            ScriptedCheckoutView view = show(service);
            ComboBox<Variant> variants = onFx(view::variants);
            waitUntil("the known variants", () -> variants.getItems().stream().anyMatch(v -> "both".equals(v.getName())));
            assertEquals(service.getRepository().getVariants().size(), (int) onFx(() -> variants.getItems().size()));

            Variant both = onFx(() -> variants.getItems().stream().filter(v -> "both".equals(v.getName())).findFirst().orElseThrow());
            Variant core = onFx(() -> variants.getItems().stream()
                    .filter(v -> !"both".equals(v.getName()) && v.getConfiguration().toString().contains("Core"))
                    .findFirst().orElseThrow());
            assertEquals("⚠ both", onFx(() -> cellText(variants, both)));
            assertFalse(onFx(() -> cellText(variants, core)).startsWith("⚠"));

            // picked like the combo box's skin does (none without a shown window): value, then action
            onFx(() -> {
                variants.setValue(both);
                variants.fireEvent(new ActionEvent());
            });
            assertEquals(both.getConfiguration().toString(), onFx(() -> view.configuration().getText()));
            waitUntil("the warning", () -> view.warning().getText().startsWith(VIOLATION));
        }
    }

    @Test
    @Timeout(60)
    public void aFailedCheckoutShowsTheError(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            // the base directory is a file
            Path file = Files.writeString(tmp.resolve("a-file"), "x\n");
            ScriptedCheckoutView view = show(service);
            checkout(view, file, "BASE, A");
            waitUntil("checkout failed", () -> view.button("Done") != null);
            assertTrue(onFx(() -> view.toolBarStyles().contains("error")));
            assertTrue(onFx(() -> view.result() instanceof ExceptionTextArea));
            assertEquals("x\n", Files.readString(file));

            // the checkout itself fails: a configuration that cannot be parsed
            ScriptedCheckoutView second = show(service);
            checkout(second, tmp.resolve("out"), "BASE, A, (");
            waitUntil("checkout failed", () -> second.button("Done") != null);
            assertTrue(onFx(() -> second.toolBarStyles().contains("error")));
            assertTrue(onFx(() -> second.result() instanceof ExceptionTextArea));
        }
    }

    /** Answers the view's two questions as told and records them. */
    private static final class ScriptedCheckoutView extends CheckoutView {
        final List<Path> clearAsked = new ArrayList<>();
        final List<String> violationsAsked = new ArrayList<>();
        volatile boolean clearAnswer = true;
        volatile boolean proceedAnswer = true;

        ScriptedCheckoutView(EccoService service) {
            super(service);
        }

        @Override
        boolean confirmClearDirectory(Path baseDir) throws IOException {
            this.clearAsked.add(baseDir);
            if (this.clearAnswer)
                Files.walkFileTree(baseDir, new DeleteDirectoryVisitor(baseDir));
            return this.clearAnswer;
        }

        @Override
        boolean askToProceedAnyway(String description, String actionVerb) {
            this.violationsAsked.add(description + " / " + actionVerb);
            return this.proceedAnswer;
        }

        String header() {
            return this.headerLabel.getText();
        }

        List<String> toolBarStyles() {
            return this.toolBar.getStyleClass();
        }

        TextField baseDir() {
            return (TextField) findAll(this, n -> n instanceof TextField).get(0);
        }

        TextField configuration() {
            return (TextField) findAll(this, n -> n instanceof TextField).get(1);
        }

        Label warning() {
            return (Label) find(this, n -> n instanceof Label l && Color.FIREBRICK.equals(l.getTextFill()));
        }

        @SuppressWarnings("unchecked")
        ComboBox<Variant> variants() {
            return (ComboBox<Variant>) find(this, n -> n instanceof ComboBox);
        }

        Button button(String text) {
            return (Button) find(this, n -> n instanceof Button b && text.equals(b.getText()));
        }

        /** The log rows: action, path, plugin. */
        List<List<String>> log() {
            SplitPane split = (SplitPane) this.getCenter();
            TableView<FileInfo> table = (TableView<FileInfo>) split.getItems().get(0);
            return table.getItems().stream().map(i -> List.of(i.getAction(), i.getPath(), i.getPlugin())).toList();
        }

        /** What is shown below the log after the checkout. */
        javafx.scene.Node result() {
            SplitPane split = (SplitPane) this.getCenter();
            return split.getItems().size() < 2 ? null : split.getItems().get(1);
        }
    }

    private static ScriptedCheckoutView show(EccoService service) throws Exception {
        return onFx(() -> {
            ScriptedCheckoutView view = new ScriptedCheckoutView(service);
            // a window (never shown), so the view's fit() has something to size
            new Stage().setScene(new Scene(view));
            return view;
        });
    }

    private static void checkout(ScriptedCheckoutView view, Path dir, String configuration) throws Exception {
        onFx(() -> {
            view.baseDir().setText(dir.toString());
            view.configuration().setText(configuration);
            view.button("Checkout").fire();
        });
    }

    /** The text the combo box's cell factory shows for {@code variant}, rendered as the only item of a list. */
    private static String cellText(ComboBox<Variant> comboBox, Variant variant) {
        ListCell<Variant> cell = comboBox.getCellFactory().call(null);
        cell.updateListView(new ListView<>(FXCollections.observableArrayList(variant)));
        cell.updateIndex(0);
        return cell.getText();
    }

    /** BASE, A: f.txt a b; BASE, B: f.txt a c and g.txt x. */
    private static EccoService twoVariants(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
        commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n", "g.txt", "x\n"));
        return service;
    }

    /** Core and Extra never occur together (4 witnesses each) and EXCLUDES(Core, Extra) is accepted. */
    private static EccoService coreExcludesExtra(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        for (int i = 1; i <= 4; i++) {
            commit(service, tmp.resolve("core-" + i), "Core", Map.of("core.txt", "core " + i + "\n"));
            commit(service, tmp.resolve("extra-" + i), "Extra", Map.of("extra.txt", "extra " + i + "\n"));
        }
        ConstraintMiner.Suggestion excludes = new ConstraintMiner(4, 0.9, null).mine(ConfigurationBridge.readConfigurations(service)).stream()
                .filter(s -> s.kind == ConstraintMiner.Kind.EXCLUDES && List.of(s.a, String.valueOf(s.b)).containsAll(List.of("Core", "Extra")))
                .findFirst().orElseThrow();
        service.acceptConstraint(excludes);
        return service;
    }
}
