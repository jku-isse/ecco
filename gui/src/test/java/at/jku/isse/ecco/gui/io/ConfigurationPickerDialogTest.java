package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
 * The configuration picker: checking a feature includes its latest revision, picking another
 * revision or unchecking it rewrites the configuration string, typing a string checks the matching
 * features, and OK hands back exactly the configuration the string names. The dialog is never
 * shown; its content and result converter are driven directly.
 */
public class ConfigurationPickerDialogTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** The picker's controls, found in its content. */
    private record Picker(ConfigurationPickerDialog dialog, TextField configString, TextField filter, List<HBox> rows) {

        static Picker of(ConfigurationPickerDialog dialog) {
            Node content = dialog.getDialogPane().getContent();
            List<TextField> fields = findAll(content, n -> n instanceof TextField).stream().map(n -> (TextField) n).toList();
            List<HBox> rows = findAll(content, n -> n instanceof HBox h && h.getChildren().size() == 3
                    && h.getChildren().get(0) instanceof CheckBox).stream().map(n -> (HBox) n).toList();
            return new Picker(dialog, fields.get(0), fields.get(1), rows);
        }

        HBox row(String feature) {
            return rows.stream().filter(r -> feature.equals(((Label) r.getChildren().get(1)).getText())).findFirst().orElseThrow();
        }

        CheckBox include(String feature) {
            return (CheckBox) row(feature).getChildren().get(0);
        }

        @SuppressWarnings("unchecked")
        ComboBox<FeatureRevision> revision(String feature) {
            return (ComboBox<FeatureRevision>) row(feature).getChildren().get(2);
        }

        List<String> names() {
            return rows.stream().map(r -> ((Label) r.getChildren().get(1)).getText()).toList();
        }
    }

    /** A picker with its features read - in the background. */
    private static Picker picker(EccoService service) throws Exception {
        ConfigurationPickerDialog dialog = onFx(() -> new ConfigurationPickerDialog(service));
        waitUntil("the features", () -> !dialog.reading());
        return onFx(() -> Picker.of(dialog));
    }

    /**
     * While a commit holds the service's lock, opening the picker does not freeze the window: the
     * features are read in the background, once the lock is free. The FX thread waited for the lock to copy the features (and a commit adding a
     * feature meanwhile failed the copy).
     */
    @Test
    @Timeout(60)
    public void theFeaturesAreNotReadOnTheFxThread(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            ConfigurationPickerDialog dialog;
            synchronized (service) { // as a commit running
                // onFx fails after 10 s if the FX thread waits for the lock
                dialog = onFx(() -> new ConfigurationPickerDialog(service));
                assertEquals(List.of(), onFx(() -> Picker.of(dialog).names()), "not read while the lock is held");
            }
            waitUntil("the features", () -> !dialog.reading());
            Picker picker = onFx(() -> Picker.of(dialog));
            assertEquals(List.of("A", "b", "C"), onFx(picker::names));
            onFx(() -> picker.configString().setText("A.1"));
            assertTrue(onFx(() -> picker.include("A").isSelected()));
            assertEquals("1", onFx(() -> picker.revision("A").getValue().getId()));
        }
    }

    private static EccoService twoRevisionsOfA(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1, b.1", Map.of("a.txt", "a\n"));
        commit(service, tmp.resolve("v2"), "A.2, C.1", Map.of("a.txt", "a2\n", "c.txt", "c\n"));
        return service;
    }

    private static Set<String> revisions(Configuration configuration) {
        return Arrays.stream(configuration.getFeatureRevisions())
                .map(r -> r.getFeature().getName() + "." + r.getId()).collect(Collectors.toSet());
    }

    /** Every feature gets a row, sorted case-insensitively, unchecked, with no revision and a disabled revision box. */
    @Test
    @Timeout(60)
    public void listsEveryFeatureSortedAndUnchecked(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            assertEquals(List.of("A", "b", "C"), onFx(picker::names));
            onFx(() -> {
                for (String feature : picker.names()) {
                    assertFalse(picker.include(feature).isSelected(), feature);
                    assertNull(picker.revision(feature).getValue(), feature);
                    assertTrue(picker.revision(feature).isDisabled(), "no revision to pick while excluded: " + feature);
                }
                assertEquals("", picker.configString().getText());
            });
            assertEquals(List.of("1", "2"), onFx(() -> picker.revision("A").getItems().stream().map(FeatureRevision::getId).sorted().toList()));
        }
    }

    /** Checking a feature includes its latest revision; unchecking it takes it out of the string again. */
    @Test
    @Timeout(60)
    public void checkingAFeatureIncludesItsLatestRevision(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> picker.include("A").setSelected(true));
            assertEquals("A.2", onFx(() -> picker.configString().getText()));
            assertEquals("2", onFx(() -> picker.revision("A").getValue().getId()));
            assertFalse(onFx(() -> picker.revision("A").isDisabled()));

            onFx(() -> picker.include("C").setSelected(true));
            assertEquals("A.2, C.1", onFx(() -> picker.configString().getText()));

            onFx(() -> picker.include("A").setSelected(false));
            assertEquals("C.1", onFx(() -> picker.configString().getText()));
            assertNull(onFx(() -> picker.revision("A").getValue()));
        }
    }

    /** Picking an earlier revision in the dropdown puts that revision's full id into the string. */
    @Test
    @Timeout(60)
    public void pickingAnEarlierRevisionRewritesTheString(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> {
                picker.include("A").setSelected(true);
                ComboBox<FeatureRevision> box = picker.revision("A");
                box.setValue(box.getItems().stream().filter(r -> r.getId().equals("1")).findFirst().orElseThrow());
            });
            assertEquals("A.1", onFx(() -> picker.configString().getText()));
        }
    }

    /** Typing a configuration string checks exactly the features it names, with the named revisions. */
    @Test
    @Timeout(60)
    public void typingAStringChecksTheNamedFeatures(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> picker.configString().setText("A.1, b.1"));
            onFx(() -> {
                assertTrue(picker.include("A").isSelected());
                assertEquals("1", picker.revision("A").getValue().getId());
                assertTrue(picker.include("b").isSelected());
                assertFalse(picker.include("C").isSelected());
                assertNull(picker.revision("C").getValue());
                // the field keeps what was typed, it is not rewritten from the checkboxes
                assertEquals("A.1, b.1", picker.configString().getText());
            });

            onFx(() -> picker.configString().setText("C.1"));
            onFx(() -> {
                assertFalse(picker.include("A").isSelected());
                assertFalse(picker.include("b").isSelected());
                assertTrue(picker.include("C").isSelected());
            });
        }
    }

    /** A string that does not parse (yet) leaves the checkboxes as they were. */
    @Test
    @Timeout(60)
    public void anUnparsableStringLeavesTheCheckboxesAlone(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> picker.include("A").setSelected(true));
            onFx(() -> picker.configString().setText("A.2, ,,"));
            onFx(() -> {
                assertTrue(picker.include("A").isSelected());
                assertEquals("2", picker.revision("A").getValue().getId());
            });
        }
    }

    /** The filter hides (and unmanages) every row whose feature name does not contain it, case-insensitively. */
    @Test
    @Timeout(60)
    public void theFilterHidesNonMatchingFeatures(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> picker.filter().setText(" c "));
            onFx(() -> {
                assertTrue(picker.row("C").isVisible() && picker.row("C").isManaged());
                assertFalse(picker.row("A").isVisible() || picker.row("A").isManaged());
                assertFalse(picker.row("b").isVisible() || picker.row("b").isManaged());
            });
            onFx(() -> picker.filter().setText(""));
            assertTrue(onFx(() -> picker.rows().stream().allMatch(r -> r.isVisible() && r.isManaged())));
        }
    }

    /** OK returns the configuration the string names (the repository's own revisions); Cancel returns nothing. */
    @Test
    @Timeout(60)
    public void okReturnsTheComposedConfigurationAndCancelNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoRevisionsOfA(tmp)) {
            Picker picker = picker(service);
            onFx(() -> {
                picker.include("A").setSelected(true);
                picker.include("b").setSelected(true);
            });
            Configuration result = onFx(() -> picker.dialog().getResultConverter().call(ButtonType.OK));
            assertEquals(Set.of("A.2", "b.1"), revisions(result));
            assertNull(onFx(() -> picker.dialog().getResultConverter().call(ButtonType.CANCEL)));
            assertTrue(onFx(() -> picker.dialog().getDialogPane().getButtonTypes().containsAll(List.of(ButtonType.OK, ButtonType.CANCEL))));
        }
    }
}
