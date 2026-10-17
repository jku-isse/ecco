package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.gui.view.*;
import at.jku.isse.ecco.gui.view.artifacts.ArtifactsView;
import at.jku.isse.ecco.gui.view.graph.ArtifactGraphView;
import at.jku.isse.ecco.gui.view.graph.DependencyGraphView;
import at.jku.isse.ecco.gui.view.graph.KnowledgeGraphView;
import at.jku.isse.ecco.gui.view.operation.ImportGitView;
import at.jku.isse.ecco.gui.view.operation.OpenView;
import at.jku.isse.ecco.service.EccoService;
import atlantafx.base.controls.Tab;
import atlantafx.base.controls.TabLine;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The main window's wiring: which ribbon buttons are usable with and without an open repository
 * (following open/close events), which content view each content button shows, that Close, the
 * Cmd+W accelerator and the window's close control close the repository rather than the app, and
 * which operation dialogs open (and how modal they are). Paths that end in a modal Alert or the
 * system file manager (Show in Finder) are not driven.
 */
public class MainViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private static TabLine tabLine(MainView view) {
        return (TabLine) find(view, n -> n instanceof TabLine);
    }

    /** The ribbon button {@code label} on tab {@code tab} - only the selected tab's band is in the scene graph. */
    private static Button ribbonButton(MainView view, String tab, String label) {
        TabLine tabs = tabLine(view);
        Tab match = tabs.getTabs().stream().filter(t -> tab.equals(t.getText())).findFirst().orElseThrow(() -> new AssertionError("no tab " + tab));
        tabs.getSelectionModel().select(match);
        return button(view.getTop(), label);
    }

    private static BorderPane contentArea(MainView view) {
        return (BorderPane) view.getCenter();
    }

    private static String header(MainView view) {
        return ((Label) contentArea(view).getTop()).getText();
    }

    /** A repository in {@code tmp}/repo/.ecco, closed again: MainView starts without an open repository. */
    private static EccoService closedRepository(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
        service.close();
        return service;
    }

    private static final List<String[]> NEEDS_REPOSITORY = List.of(
            new String[]{"Repositories", "Close Repository"},
            new String[]{"Versions", "Commit..."},
            new String[]{"Versions", "Commit Multiple Versions..."},
            new String[]{"Versions", "Checkout..."},
            new String[]{"Versions", "Variants"},
            new String[]{"Versions", "Import from Git..."},
            new String[]{"Repositories", MainView.showDirectoryLabel()},
            new String[]{"Collaborate", "Remotes"},
            new String[]{"Collaborate", "Fetch..."},
            new String[]{"Collaborate", "Pull..."},
            new String[]{"Collaborate", "Push..."},
            new String[]{"Collaborate", "Server..."},
            new String[]{"Explore", "Feature Model"},
            new String[]{"Explore", "Charts"},
            new String[]{"Explore", "Knowledge Graph"},
            new String[]{"Explore", "Dependency Graph"});

    private static void assertRepositoryButtons(MainView view, boolean open) {
        for (String label : List.of("New...", "Open...", "Fork..."))
            assertEquals(open, ribbonButton(view, "Repositories", label).isDisable(), label + " is only usable without an open repository");
        for (String[] button : NEEDS_REPOSITORY)
            assertEquals(!open, ribbonButton(view, button[0], button[1]).isDisable(), button[1] + " needs an open repository");
        // the start page works without one; the other views need one
        assertEquals(!open && !(contentArea(view).getCenter() instanceof StartView), contentArea(view).isDisable(), "the content area needs an open repository");
    }

    /** Without an open repository only New/Open/Fork are usable; opening and closing it flips that. */
    @Test
    @Timeout(120)
    public void buttonsFollowWhetherARepositoryIsOpen(@TempDir Path tmp) throws Exception {
        try (EccoService service = closedRepository(tmp)) {
            MainView view = onFx(() -> new MainView(service));
            onFx(() -> {
                // no repository open: the recent ones to open - the window was empty
                assertEquals("Start", header(view));
                assertInstanceOf(StartView.class, contentArea(view).getCenter());
                assertFalse(contentArea(view).isDisable());
                assertRepositoryButtons(view, false);
            });

            service.open();
            waitUntil("the open repository's buttons", () -> !ribbonButton(view, "Repositories", "Close Repository").isDisable());
            onFx(() -> assertRepositoryButtons(view, true));
            assertEquals("Status", onFx(() -> header(view)), "opened: its status");

            onFx(() -> ribbonButton(view, "Repositories", "Close Repository").fire());
            assertFalse(service.isInitialized(), "Close closes the repository");
            waitUntil("the closed repository's buttons", () -> ribbonButton(view, "Repositories", "Close Repository").isDisable());
            onFx(() -> assertRepositoryButtons(view, false));
            assertEquals("Start", onFx(() -> header(view)), "closed: the start page again");
        }
    }

    /** Each content button swaps the one shared content area to its view and names it in the header. */
    @Test
    @Timeout(120)
    public void contentButtonsSwitchTheSharedContentArea(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            MainView view = onFx(() -> new MainView(service));
            Map<String[], Class<?>> expected = new LinkedHashMap<>();
            expected.put(new String[]{"Repositories", "Status"}, SettingsView.class);
            expected.put(new String[]{"Versions", "Variants"}, VariantsView.class);
            expected.put(new String[]{"Collaborate", "Remotes"}, RemotesView.class);
            expected.put(new String[]{"Explore", "Feature Model"}, FeaturesView.class);
            expected.put(new String[]{"Explore", "Commits"}, CommitsView.class);
            expected.put(new String[]{"Explore", "Artifacts"}, ArtifactsView.class);
            expected.put(new String[]{"Explore", "Charts"}, ChartsView.class);
            expected.put(new String[]{"Explore", "Knowledge Graph"}, KnowledgeGraphView.class);
            expected.put(new String[]{"Explore", "Artifact Graph"}, ArtifactGraphView.class);
            expected.put(new String[]{"Explore", "Dependency Graph"}, DependencyGraphView.class);

            Map<Class<?>, Node> shown = new LinkedHashMap<>();
            for (Map.Entry<String[], Class<?>> entry : expected.entrySet()) {
                String[] button = entry.getKey();
                onFx(() -> {
                    ribbonButton(view, button[0], button[1]).fire();
                    assertEquals(button[1], header(view));
                    Node center = contentArea(view).getCenter();
                    assertInstanceOf(entry.getValue(), center, button[1]);
                    shown.put(entry.getValue(), center);
                });
            }
            // the views are built once: going back shows the same instance again
            onFx(() -> {
                ribbonButton(view, "Versions", "Variants").fire();
                assertSame(shown.get(VariantsView.class), contentArea(view).getCenter());
            });
            // the graph views render on the AWT thread; let that finish before the repository closes
            onFx(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /**
     * The tabs: Repositories first - the tab shown at start - and the ECCO tab (Preferences, Quit;
     * macOS has them in its app menu) last; the views in one Explore tab. Every button has a tooltip.
     */
    @Test
    @Timeout(120)
    public void theTabsStartWithRepositoriesAndEveryButtonExplainsItself(@TempDir Path tmp) throws Exception {
        try (EccoService service = closedRepository(tmp)) {
            MainView view = onFx(() -> new MainView(service));
            assertEquals(List.of("Repositories", "Versions", "Collaborate", "Explore", "ECCO"),
                    onFx(() -> tabLine(view).getTabs().stream().map(Tab::getText).toList()));
            assertEquals("Repositories", onFx(() -> tabLine(view).getSelectionModel().getSelectedItem().getText()), "shown at start");
            for (String tab : List.of("Repositories", "Versions", "Collaborate", "Explore", "ECCO"))
                onFx(() -> {
                    firstLabel(view, tab); // shows the tab's band
                    for (Node node : findAll(view.getTop(), n -> n instanceof Button))
                        assertNotNull(((Button) node).getTooltip(), tab + ": " + ((Button) node).getText());
                });
        }
    }

    /**
     * The server is Collaborate > Server..., a window of its own that does not block the main one
     * (it was a section of Preferences); Help lists what each button does, from the ribbon; About
     * says which version runs.
     */
    @Test
    @Timeout(120)
    public void serverHelpAndAboutOpenTheirWindows(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            MainView view = onFx(() -> new MainView(service));
            Stage main = onFx(() -> {
                Stage s = new Stage();
                s.setScene(new Scene(view));
                s.show();
                return s;
            });
            try {
                Stage server = onFx(() -> opened(view, "Collaborate", "Server..."));
                assertInstanceOf(at.jku.isse.ecco.gui.view.operation.ServerView.class, server.getScene().getRoot());
                assertEquals(Modality.NONE, server.getModality());
                onFx(server::hide);

                Stage help = onFx(() -> opened(view, "ECCO", "Help"));
                assertEquals("ECCO Help", help.getTitle());
                assertEquals(Modality.NONE, help.getModality());
                List<String> texts = onFx(() -> findAll(help.getScene().getRoot(), n -> n instanceof Label).stream().map(n -> ((Label) n).getText()).toList());
                assertTrue(texts.contains("Explore") && texts.contains("Server...")
                        && texts.contains("Serve this repository to other ECCO instances: start, stop, and its log"), texts.toString());
                assertTrue(texts.contains("Cmd/Ctrl+1 to 7"), texts.toString());
                onFx(help::hide);

                Stage about = onFx(() -> opened(view, "ECCO", "About ECCO"));
                List<String> aboutTexts = onFx(() -> findAll(about.getScene().getRoot(), n -> n instanceof Label).stream().map(n -> ((Label) n).getText()).toList());
                assertTrue(aboutTexts.contains("Development build"), "run from the classes: no manifest - " + aboutTexts);
                onFx(about::hide);
                assertTrue(onFx(() -> main.getScene().getAccelerators().containsKey(new KeyCodeCombination(KeyCode.F1))));
            } finally {
                onFx(main::hide);
                onFx(() -> { });
                javax.swing.SwingUtilities.invokeAndWait(() -> { });
            }
        }
    }

    /** Cmd/Ctrl+N is New, Cmd/Ctrl+K Commit, Cmd/Ctrl+1 to 7 the views - each only while its button is usable. */
    @Test
    @Timeout(120)
    public void shortcutsFireTheirButtons(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            MainView view = onFx(() -> new MainView(service));
            Stage stage = onFx(() -> {
                Stage s = new Stage();
                s.setScene(new Scene(view));
                return s;
            });
            Map<KeyCombination, Runnable> accelerators = onFx(() -> Map.copyOf(stage.getScene().getAccelerators()));
            List<String> views = List.of("Feature Model", "Commits", "Artifacts", "Charts", "Knowledge Graph", "Artifact Graph", "Dependency Graph");
            for (int i = 0; i < views.size(); i++) {
                Runnable shortcut = accelerators.get(new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i + 1)), KeyCombination.SHORTCUT_DOWN));
                onFx(shortcut::run);
                assertEquals(views.get(i), onFx(() -> header(view)));
            }
            assertTrue(accelerators.containsKey(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN)));
            // a repository is open: New is disabled, and so is its shortcut
            List<Window> before = onFx(() -> List.copyOf(Window.getWindows()));
            onFx(() -> accelerators.get(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN)).run());
            assertEquals(before, onFx(() -> List.copyOf(Window.getWindows())), "no New dialog");
            onFx(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static String firstLabel(MainView view, String tab) {
        TabLine tabs = tabLine(view);
        tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t -> tab.equals(t.getText())).findFirst().orElseThrow());
        return ((Button) find(view.getTop(), n -> n instanceof Button)).getText();
    }

    /**
     * In the main window Cmd+W and the window's close control close the repository, not the app;
     * with no repository open they do nothing. Cmd+Q and Cmd+O are registered too.
     */
    @Test
    @Timeout(120)
    public void closingTheMainWindowClosesOnlyTheRepository(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            MainView view = onFx(() -> new MainView(service));
            Stage stage = onFx(() -> {
                Stage s = new Stage();
                s.setScene(new Scene(view));
                return s;
            });
            Map<KeyCombination, Runnable> accelerators = onFx(() -> Map.copyOf(stage.getScene().getAccelerators()));
            assertTrue(accelerators.containsKey(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN)));
            assertTrue(accelerators.containsKey(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN)));
            Runnable closeShortcut = accelerators.get(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));

            onFx(closeShortcut::run);
            assertFalse(service.isInitialized(), "Cmd+W closed the repository");
            onFx(closeShortcut::run); // no repository open: nothing to do, no failure

            service.open();
            WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            onFx(() -> stage.getOnCloseRequest().handle(request));
            assertTrue(request.isConsumed(), "the window stays open");
            assertFalse(service.isInitialized(), "the close control closed the repository");
        }
    }

    /**
     * Open... opens the Open operation in a window-modal dialog over the main window; Import From
     * Git... opens a non-modal one, so the main window stays usable during a long import.
     */
    @Test
    @Timeout(120)
    public void operationButtonsOpenTheirDialogs(@TempDir Path tmp) throws Exception {
        try (EccoService service = closedRepository(tmp)) {
            MainView view = onFx(() -> new MainView(service));
            Stage main = onFx(() -> {
                Stage s = new Stage();
                s.setScene(new Scene(view));
                s.show();
                return s;
            });
            try {
                Stage open = onFx(() -> opened(view, "Repositories", "Open..."));
                assertEquals("Open", open.getTitle());
                assertInstanceOf(OpenView.class, open.getScene().getRoot());
                assertEquals(Modality.WINDOW_MODAL, open.getModality());
                assertSame(main, open.getOwner());
                assertTrue(open.getScene().getAccelerators().containsKey(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN)));
                onFx(() -> open.getScene().getAccelerators().get(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN)).run());
                assertFalse(onFx(open::isShowing), "Cmd+W closes a dialog");

                service.open();
                waitUntil("the open repository's buttons", () -> !ribbonButton(view, "Versions", "Import from Git...").isDisable());
                Stage importGit = onFx(() -> opened(view, "Versions", "Import from Git..."));
                try {
                    assertEquals("Import from Git", importGit.getTitle());
                    assertInstanceOf(ImportGitView.class, importGit.getScene().getRoot());
                    assertEquals(Modality.NONE, importGit.getModality());
                } finally {
                    onFx(importGit::hide);
                }
            } finally {
                onFx(main::hide);
            }
        }
    }

    /** Fires the ribbon button and returns the one window it opened. */
    private static Stage opened(MainView view, String tab, String label) {
        List<Window> before = List.copyOf(Window.getWindows());
        ribbonButton(view, tab, label).fire();
        List<Window> added = Window.getWindows().stream().filter(w -> !before.contains(w)).toList();
        assertEquals(1, added.size(), label + " opens one window: " + added);
        return (Stage) added.get(0);
    }
}
