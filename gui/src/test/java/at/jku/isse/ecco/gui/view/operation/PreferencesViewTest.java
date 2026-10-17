package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.mining.MinimizationPreferences;
import at.jku.isse.ecco.service.AdapterPreferences;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LilypondPreferences;
import at.jku.isse.ecco.service.LlmPreferences;
import com.sun.net.httpserver.HttpServer;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Settings dialog decides: which category it shows, what each section reads from and
 * writes to the user's preferences, that Save persists every section (also the ones not on screen)
 * and Cancel none, and what the LLM section's Refresh lists. The preferences are the developer's
 * real ones (java.util.prefs, user node): every key the dialog writes is saved before each test and
 * put back after it.
 */
public class PreferencesViewTest {

    /** Every key the dialog's Save writes, by the class whose package names its preferences node. */
    private static final Map<Class<?>, List<String>> KEYS = Map.of(
            AdapterPreferences.class, List.of("disabledAdapterPluginIds", "pythonAdapterPythonPath",
                    "llmEndpointUrl", "llmModelName",
                    "lilypondExecutablePath", "lilypondSearchPaths", "lilypondPythonPath", "lilypondLymodelPath"),
            MinimizationPreferences.class, List.of("minimizationMinWitness", "minimizationConfidence", "minimizationUseInCheckout"));

    private final Map<String, String> original = new LinkedHashMap<>();
    private Stage stage;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @BeforeEach
    public void saveAndSeedPreferences() throws Exception {
        for (Map.Entry<Class<?>, List<String>> node : KEYS.entrySet())
            for (String key : node.getValue())
                original.put(node.getKey().getName() + "#" + key, prefs(node.getKey()).get(key, null));
        AdapterPreferences.setDisabledPluginIds(Set.of(pluginId("java.JavaPlugin")));
        AdapterPreferences.setPythonAdapterPython("");
        LlmPreferences.setEndpointUrl("http://localhost:1");
        LlmPreferences.setModelName("");
        LilypondPreferences.setExecutablePath("");
        LilypondPreferences.setSearchPaths(List.of());
        LilypondPreferences.setPythonPath("");
        LilypondPreferences.setLymodelPath("");
        MinimizationPreferences.setMinWitness(4);
        MinimizationPreferences.setConfidence(0.9);
        MinimizationPreferences.setUsedInCheckout(false);
    }

    @AfterEach
    public void restorePreferences() throws Exception {
        try {
            if (stage != null)
                onFx(() -> stage.close());
        } finally {
            for (Map.Entry<Class<?>, List<String>> node : KEYS.entrySet()) {
                Preferences prefs = prefs(node.getKey());
                for (String key : node.getValue()) {
                    String value = original.get(node.getKey().getName() + "#" + key);
                    if (value == null)
                        prefs.remove(key);
                    else
                        prefs.put(key, value);
                }
                prefs.flush();
            }
        }
    }

    @Test
    @Timeout(60)
    public void opensOnTheRequestedCategoryAndShowsTheOneChosen(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LILYPOND);
            assertEquals(PreferencesView.Section.LILYPOND, onFx(() -> categories(view).getSelectionModel().getSelectedItem()));
            assertTrue(onFx(() -> labelled(view, "Executable Path: ")));

            onFx(() -> categories(view).getSelectionModel().select(PreferencesView.Section.PLUGINS));
            assertTrue(onFx(() -> labelled(view, "Python adapter's Python: ")));
            assertEquals(List.of(PreferencesView.Section.values()), onFx(() -> List.copyOf(categories(view).getItems())));
        }
    }

    @Test
    @Timeout(60)
    public void theDefaultCategoryIsPlugins(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = onFx(() -> {
                PreferencesView v = new PreferencesView(service);
                stage = stage(v);
                return v;
            });
            assertEquals(PreferencesView.Section.PLUGINS, onFx(() -> categories(view).getSelectionModel().getSelectedItem()));
        }
    }

    /** One checkbox per adapter on the classpath, labelled with its module so the two "JavaArtifactPlugin"s differ, ticked unless deactivated. */
    @Test
    @Timeout(60)
    public void listsEveryAdapterTickedUnlessDeactivated(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.PLUGINS);
            List<CheckBox> boxes = onFx(() -> pluginBoxes(view));
            assertEquals(ArtifactPlugin.getArtifactPlugins().length, boxes.size());

            assertFalse(onFx(() -> pluginBox(view, "JavaArtifactPlugin (java)").isSelected()), "deactivated in the preferences");
            assertTrue(onFx(() -> pluginBox(view, "JavaArtifactPlugin (challenge)").isSelected()), "the other Java adapter is a different one");
            assertTrue(onFx(() -> pluginBox(view, "TextArtifactPlugin (text)").isSelected()));
            assertEquals(1, onFx(() -> boxes.stream().filter(b -> !b.isSelected()).count()));
        }
    }

    @Test
    @Timeout(60)
    public void saveDeactivatesTheUntickedAdaptersAndKeepsTheTrimmedPython(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.PLUGINS);
            onFx(() -> {
                pluginBox(view, "JavaArtifactPlugin (java)").setSelected(true);
                pluginBox(view, "TextArtifactPlugin (text)").setSelected(false);
                pluginBox(view, "MarkdownArtifactPlugin (markdown)").setSelected(false);
                textFields(view).get(0).setText("  /opt/python3  ");
                button(view, "Save").fire();
            });

            assertFalse(onFx(stage::isShowing), "Save closes the dialog");
            assertEquals(Set.of(pluginId("text.TextPlugin"), pluginId("markdown.MarkdownPlugin")), AdapterPreferences.getDisabledPluginIds());
            assertEquals("/opt/python3", AdapterPreferences.getPythonAdapterPython());
        }
    }

    @Test
    @Timeout(60)
    public void eachSectionStartsFromTheSavedPreferences(@TempDir Path tmp) throws Exception {
        AdapterPreferences.setPythonAdapterPython("/usr/bin/python3");
        LlmPreferences.setEndpointUrl("http://example.invalid:1234");
        LlmPreferences.setModelName("saved-model");
        LilypondPreferences.setExecutablePath("/opt/lilypond");
        LilypondPreferences.setSearchPaths(List.of("/a", "/b"));
        LilypondPreferences.setPythonPath("/opt/lypython");
        LilypondPreferences.setLymodelPath("/opt/lymodel");
        MinimizationPreferences.setMinWitness(7);
        MinimizationPreferences.setConfidence(0.75);
        MinimizationPreferences.setUsedInCheckout(true);
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.PLUGINS);
            assertEquals("/usr/bin/python3", onFx(() -> textFields(view).get(0).getText()));

            select(view, PreferencesView.Section.LLM);
            assertEquals("http://example.invalid:1234", onFx(() -> textFields(view).get(0).getText()));
            assertEquals("saved-model", onFx(() -> modelBox(view).getValue()));
            assertEquals(List.of("saved-model"), onFx(() -> List.copyOf(modelBox(view).getItems())), "shown before any Refresh");

            select(view, PreferencesView.Section.MINIMIZATION);
            assertEquals(7, onFx(() -> spinners(view).get(0).getValue()));
            assertEquals(0.75, onFx(() -> spinners(view).get(1).getValue()));
            assertTrue(onFx(() -> checkBoxes(view).get(0).isSelected()));

            select(view, PreferencesView.Section.LILYPOND);
            assertEquals(List.of("/opt/lilypond", "/a|/b", "/opt/lypython", "/opt/lymodel"),
                    onFx(() -> textFields(view).stream().map(TextField::getText).toList()));
        }
    }

    /** Edits made in sections no longer on screen are saved too, together with the one shown. */
    @Test
    @Timeout(60)
    public void saveWritesEverySectionNotOnlyTheOneShown(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LLM);
            onFx(() -> {
                textFields(view).get(0).setText("http://127.0.0.1:9999");
                modelBox(view).getEditor().setText("  typed-model  "); // typed, never committed with Enter
            });
            select(view, PreferencesView.Section.MINIMIZATION);
            onFx(() -> {
                spinners(view).get(0).getEditor().setText("12"); // typed, never committed with Enter
                spinners(view).get(1).getEditor().setText("0.5");
                checkBoxes(view).get(0).setSelected(true);
            });
            select(view, PreferencesView.Section.LILYPOND);
            onFx(() -> {
                List<TextField> fields = textFields(view);
                fields.get(0).setText(" /opt/lilypond/bin/lilypond ");
                fields.get(1).setText(" /x | |/y|");
                fields.get(2).setText(" /opt/py ");
                fields.get(3).setText(" /opt/lymodel ");
            });
            select(view, PreferencesView.Section.PLUGINS);
            onFx(() -> button(view, "Save").fire());

            assertEquals("http://127.0.0.1:9999", LlmPreferences.getEndpointUrl());
            assertEquals("typed-model", LlmPreferences.getModelName());
            assertEquals(12, MinimizationPreferences.getMinWitness());
            assertEquals(0.5, MinimizationPreferences.getConfidence());
            assertTrue(MinimizationPreferences.isUsedInCheckout());
            assertTrue(service.isMinimizedConditionsInCheckout(), "the open repository's checkout follows at once");
            assertEquals("/opt/lilypond/bin/lilypond", LilypondPreferences.getExecutablePath());
            assertEquals(List.of("/x", "/y"), LilypondPreferences.getSearchPaths(), "split at '|', blanks dropped, trimmed");
            assertEquals("/opt/py", LilypondPreferences.getPythonPath());
            assertEquals("/opt/lymodel", LilypondPreferences.getLymodelPath());
        }
    }

    @Test
    @Timeout(60)
    public void blankSearchPathsAreSavedAsNone(@TempDir Path tmp) throws Exception {
        LilypondPreferences.setSearchPaths(List.of("/old"));
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LILYPOND);
            onFx(() -> {
                textFields(view).get(1).setText("   ");
                button(view, "Save").fire();
            });
            assertEquals(List.of(), LilypondPreferences.getSearchPaths());
        }
    }

    @Test
    @Timeout(60)
    public void cancelSavesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LLM);
            onFx(() -> {
                textFields(view).get(0).setText("http://changed:1");
                button(view, "Cancel").fire();
            });
            assertFalse(onFx(stage::isShowing));
            assertEquals("http://localhost:1", LlmPreferences.getEndpointUrl());
            assertEquals(Set.of(pluginId("java.JavaPlugin")), AdapterPreferences.getDisabledPluginIds());
            assertFalse(service.isMinimizedConditionsInCheckout());
        }
    }

    /** Refresh asks the typed server for its models; a model the user typed stays chosen, else the first one listed. */
    @Test
    @Timeout(60)
    public void refreshListsTheServersModels(@TempDir Path tmp) throws Exception {
        HttpServer server = modelsServer("{\"data\":[{\"id\":\"zeta\"},{\"id\":\"alpha\"}]}");
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LLM);
            onFx(() -> {
                textFields(view).get(0).setText("http://127.0.0.1:" + server.getAddress().getPort());
                button(view, "Refresh").fire();
            });
            waitUntil("the models", () -> statusText(view).endsWith("found."));
            assertEquals("2 model(s) found.", onFx(() -> statusText(view)));
            assertEquals(List.of("alpha", "zeta"), onFx(() -> List.copyOf(modelBox(view).getItems())));
            assertEquals("alpha", onFx(() -> modelBox(view).getValue()), "nothing typed: the first model");
            assertFalse(onFx(() -> button(view, "Refresh").isDisable()));

            onFx(() -> {
                modelBox(view).getEditor().setText("zeta");
                button(view, "Refresh").fire();
            });
            waitUntil("the models again", () -> statusText(view).endsWith("found.") && !button(view, "Refresh").isDisable());
            assertEquals("zeta", onFx(() -> modelBox(view).getValue()), "the typed model stays");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(60)
    public void anUnreachableServerIsReportedAndRefreshCanBeTriedAgain(@TempDir Path tmp) throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        try (EccoService service = repository(tmp.resolve("repo"))) {
            PreferencesView view = show(service, PreferencesView.Section.LLM);
            onFx(() -> {
                textFields(view).get(0).setText("http://127.0.0.1:" + closedPort);
                button(view, "Refresh").fire();
            });
            waitUntil("the failure", () -> statusText(view).startsWith("Could not list models: "));
            assertFalse(onFx(() -> button(view, "Refresh").isDisable()));
            assertEquals(List.of(), onFx(() -> List.copyOf(modelBox(view).getItems())), "nothing saved, nothing listed");
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    private static Preferences prefs(Class<?> owner) {
        return Preferences.userNodeForPackage(owner);
    }

    /** The id of the adapter class {@code suffix} (e.g. "text.TextPlugin") below at.jku.isse.ecco.adapter. */
    private static String pluginId(String suffix) {
        String id = "at.jku.isse.ecco.adapter." + suffix;
        assertTrue(Arrays.stream(ArtifactPlugin.getArtifactPlugins()).anyMatch(p -> p.getPluginId().equals(id)), id);
        return id;
    }

    private static HttpServer modelsServer(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private Stage stage(PreferencesView view) {
        Stage stage = new Stage();
        stage.setScene(new Scene(view));
        stage.setX(-10_000);
        stage.setY(-10_000);
        stage.show();
        return stage;
    }

    /** In an off-screen window: Save and Cancel close it. */
    private PreferencesView show(EccoService service, PreferencesView.Section section) throws Exception {
        return onFx(() -> {
            PreferencesView view = new PreferencesView(service, section);
            stage = stage(view);
            return view;
        });
    }

    @SuppressWarnings("unchecked")
    private static ListView<PreferencesView.Section> categories(PreferencesView view) {
        return (ListView<PreferencesView.Section>) find(view.getCenter(), n -> n instanceof ListView);
    }

    private static void select(PreferencesView view, PreferencesView.Section section) throws Exception {
        onFx(() -> categories(view).getSelectionModel().select(section));
    }

    /** The shown section's content. */
    private static Node content(PreferencesView view) {
        return ((ScrollPane) find(view.getCenter(), n -> n instanceof ScrollPane && n.getParent() == view.getCenter())).getContent();
    }

    private static boolean labelled(PreferencesView view, String text) {
        return find(content(view), n -> n instanceof Label l && text.equals(l.getText())) != null;
    }

    /** The shown section's text fields in their order on screen, without a ComboBox's or Spinner's own editor. */
    private static List<TextField> textFields(PreferencesView view) {
        return findAll(content(view), n -> n instanceof TextField && !(n.getParent() instanceof ComboBox) && !(n.getParent() instanceof Spinner))
                .stream().map(n -> (TextField) n).toList();
    }

    private static List<CheckBox> checkBoxes(PreferencesView view) {
        return findAll(content(view), n -> n instanceof CheckBox).stream().map(n -> (CheckBox) n).toList();
    }

    private static List<CheckBox> pluginBoxes(PreferencesView view) {
        return checkBoxes(view);
    }

    private static CheckBox pluginBox(PreferencesView view, String prefix) {
        List<CheckBox> matching = pluginBoxes(view).stream().filter(b -> b.getText().startsWith(prefix + " – ")).toList();
        assertEquals(1, matching.size(), prefix + " in " + pluginBoxes(view).stream().map(CheckBox::getText).toList());
        return matching.get(0);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> modelBox(PreferencesView view) {
        return (ComboBox<String>) find(content(view), n -> n instanceof ComboBox);
    }

    @SuppressWarnings("unchecked")
    private static List<Spinner<Number>> spinners(PreferencesView view) {
        return findAll(content(view), n -> n instanceof Spinner).stream().map(n -> (Spinner<Number>) n).toList();
    }

    /** The LLM section's status line: the label below the model row. */
    private static String statusText(PreferencesView view) {
        List<Node> labels = findAll(content(view), n -> n instanceof Label l && l.getParent() == content(view));
        String text = ((Label) labels.get(labels.size() - 1)).getText();
        return text == null ? "" : text;
    }
}
