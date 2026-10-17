package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.gui.view.artifacts.ArtifactsView;
import at.jku.isse.ecco.gui.view.operation.checkout.CheckoutView;
import at.jku.isse.ecco.gui.ribbon.RibbonAction;
import at.jku.isse.ecco.gui.ribbon.RibbonBar;
import at.jku.isse.ecco.gui.ribbon.RibbonGroup;
import at.jku.isse.ecco.gui.ribbon.RibbonTabSpec;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.gui.view.*;
import at.jku.isse.ecco.gui.view.graph.ArtifactGraphView;
import at.jku.isse.ecco.gui.view.graph.DependencyGraphView;
import at.jku.isse.ecco.gui.view.graph.KnowledgeGraphView;
import at.jku.isse.ecco.gui.view.operation.*;
import at.jku.isse.ecco.gui.view.operation.ForkView;
import at.jku.isse.ecco.gui.view.operation.InitView;
import at.jku.isse.ecco.service.listener.EccoListener;
import de.jangassen.MenuToolkit;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.feather.Feather;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link RibbonBar} (Repositories / Versions / Collaborate / Explore, plus a last "ECCO" tab
 * on non-macOS platforms) replaces the {@link MenuBar} this class used to
 * build - same organizing principle as before (grouped by workflow, not by "is this a dialog or a
 * tab"), different chrome: each former top-level {@code Menu} is now a ribbon tab, and each former
 * flat {@code MenuItem} list is now a "band" of buttons shown below the tab row once its tab is
 * selected, instead of a dropdown. This is navigation only - every button runs the exact same
 * action a {@code MenuItem} used to, and no content view's own internal toolbar (e.g. Knowledge
 * Graph's entity/layout controls) is touched by this change either way.
 * <p>
 * "Action" actions (New, Open, Close Repository, Commit, Import from Git, Checkout, Fetch, Pull,
 * Push) behave exactly as their old {@code MenuItem}s did, via {@link #openDialog}. "Content"
 * actions (Status, Variants, Remotes, Feature Model, Commits, Artifacts, Charts, Knowledge Graph,
 * Artifact Graph, Dependency Graph) swap a single shared content area via {@link #switchTo} - the
 * same "one view visible at a time" model as before, just ribbon-driven instead of menu-driven.
 * Status (repository/base directory info) is also the view shown at startup. A label ends in
 * "..." when its button opens a dialog that asks for something; every button has a tooltip.
 * <p>
 * Settings (Plugins/LLM/Minimization/Lilypond/Server, all now one {@link PreferencesView} dialog)
 * is reached differently depending on platform: on macOS it's wired as a "Preferences…" item in
 * the real, native "ECCO" application menu (the bold, leftmost one, at the top of the *screen*,
 * not the window) via {@link MenuToolkit#setApplicationMenu} - {@code java.awt.Desktop.
 * setPreferencesHandler} looks like the obvious way to do this but does NOT work here: it hooks in
 * through AWT's own Cocoa bridge, a separate native toolkit from the Glass bridge this app's
 * windowing otherwise uses, so an AWT-registered handler is never reflected in the menu actually on
 * screen. {@code MenuToolkit.setApplicationMenu} instead talks to AppKit's live
 * {@code NSApplication.mainMenu} directly (via JNA) - see the {@code isMac} block below. Unlike
 * when this class still built a {@link MenuBar} with {@code setUseSystemMenuBar(true)}, this app
 * menu is now the ONLY thing meant to live in the native macOS menu bar - Repository/Local/etc.
 * navigation lives entirely inside the window's own {@link RibbonBar} now, which never touches the
 * native menu bar at all. Where no native app menu is available (Windows/Linux), a plain leftmost
 * "ECCO" ribbon tab (Preferences.../Quit) covers the same ground instead.
 */
public class MainView extends BorderPane implements EccoListener {
	private final EccoService eccoService;

	/**
	 * Every button disabled while no repository is open - see {@link #updateView()}. Populated in
	 * the constructor once every {@link RibbonAction} exists (New, Open, and the whole Preferences
	 * path are the only actions that stay enabled either way - everything else, including every
	 * content action, needs an open repository to mean anything).
	 */
	private final List<Button> requiresOpenRepository = new ArrayList<>();

	private final Button newButton;
	private final Button openButton;
	private final Button forkButton;

	private final Label headerLabel = new Label();
	private final BorderPane contentArea = new BorderPane();
	private Region currentContentView;

	public MainView(EccoService eccoService) {
		this.eccoService = eccoService;

		// same check NSMenuFX's own NativeAdapterProvider uses internally to pick between its
		// MacNativeAdapter and a no-op DummyNativeAdapter - mirrored here so this class can decide
		// whether it still needs its own ribbon "ECCO" tab fallback (see below and the isMac block
		// after the RibbonBar is built).
		// on macOS's own window system: under Monocle (the headless tests) there is no native menu bar,
		// and setting AppKit's menu from its FX thread aborted the JVM
		boolean isMac = System.getProperty("os.name", "").startsWith("Mac") && !"Monocle".equalsIgnoreCase(System.getProperty("glass.platform"));

		// "Action" actions - open a dialog or act directly, independent of any content view.
		// a label ends in "..." when its button opens a dialog that asks for something
		RibbonAction newAction = new RibbonAction("New...", Feather.FOLDER_PLUS,
				() -> this.openDialog("New", new InitView(eccoService)), false, "Create a repository in a folder");
		RibbonAction openAction = new RibbonAction("Open...", Feather.FOLDER,
				() -> this.openDialog("Open", new OpenView(eccoService)), false, "Open a repository");
		RibbonAction forkAction = new RibbonAction("Fork...", Feather.COPY,
				() -> this.openDialog("Fork", new ForkView(eccoService)), false, "Copy a repository, or a part of it, into a new one");
		// "Close" alone read like closing the window
		RibbonAction closeAction = new RibbonAction("Close Repository", Feather.X,
				() -> this.eccoService.close(), true, "Close the open repository");

		RibbonAction commitAction = new RibbonAction("Commit...", Feather.CHECK_CIRCLE,
				() -> this.openDialog("Commit", new CommitBaseDirView(eccoService)), true, "Commit a variant folder with its configuration");
		RibbonAction commitMultipleAction = new RibbonAction("Commit Multiple Versions...", Feather.CHECK_SQUARE,
				() -> this.openDialog("Commit Multiple Versions", new CommitView(eccoService)), true, "Commit several variant folders, one commit each");
		RibbonAction importGitAction = new RibbonAction("Import from Git...", Feather.LOG_IN,
				() -> this.openDialog("Import from Git", new ImportGitView(eccoService), Modality.NONE), true,
				"Import the commits of a Git repository, each as a variant");
		RibbonAction checkoutAction = new RibbonAction("Checkout...", Feather.PACKAGE,
				() -> this.openDialog("Checkout", new CheckoutView(eccoService)), true, "Compose and write the variant of a configuration");
		// it shows the base directory in the file manager - "Open Directory..." read like opening a repository
		RibbonAction showDirectoryAction = new RibbonAction(showDirectoryLabel(), Feather.EXTERNAL_LINK,
				() -> this.openBaseDirectory(), true, "Show the base directory - where variants are committed from and checked out to");

		RibbonAction fetchAction = new RibbonAction("Fetch...", Feather.ARROW_DOWN_CIRCLE,
				() -> this.openDialog("Fetch", new FetchView(eccoService)), true, "Get what a remote has that this repository has not, without merging it");
		RibbonAction pullAction = new RibbonAction("Pull...", Feather.DOWNLOAD_CLOUD,
				() -> this.openDialog("Pull", new PullView(eccoService)), true, "Fetch from a remote and merge it");
		RibbonAction pushAction = new RibbonAction("Push...", Feather.UPLOAD_CLOUD,
				() -> this.openDialog("Push", new PushView(eccoService)), true, "Send this repository's features to a remote");
		// an operation, not a preference: it was a section of Preferences; not modal - it runs, and logs, while you work
		RibbonAction serverAction = new RibbonAction("Server...", Feather.RADIO,
				() -> this.openDialog("Server", new ServerView(eccoService), Modality.NONE), true,
				"Serve this repository to other ECCO instances: start, stop, and its log");

		// Preferences/Quit - only built as ribbon actions on non-mac platforms; on macOS these are
		// reached via the native app menu instead (see the isMac block below), and preferencesAction
		// stays null so the Scene-level Cmd+, accelerator below is skipped too (macOS gets that
		// accelerator for free from the native menu item instead).
		RibbonAction preferencesAction = null;
		RibbonAction quitAction = null;
		if (!isMac) {
			preferencesAction = new RibbonAction("Preferences...", Feather.SETTINGS,
					() -> this.openDialog("Preferences", new PreferencesView(eccoService)), false, "Adapters, the LLM, minimization and LilyPond");
			quitAction = new RibbonAction("Quit ECCO", Feather.LOG_OUT, Platform::exit, false, "Close the window and end ECCO");
		}
		// there was no help; on macOS both are in the app menu (see the isMac block below)
		RibbonAction aboutAction = new RibbonAction("About ECCO", Feather.INFO,
				this::openAbout, false, "The version, and where the project is");
		RibbonAction helpAction = new RibbonAction("Help", Feather.HELP_CIRCLE,
				this::openHelp, false, "What each button does, the shortcuts, and the documentation");

		// one shared "Minimize Presence Conditions" run, started from the views that display a
		// minimized condition and after every review in the Feature Model view
		MinimizationResults minimizationResults = new MinimizationResults(eccoService);

		// every content view, constructed eagerly exactly as before - only how each becomes
		// visible changed (ribbon-driven swap instead of a tab click or dropdown menu), not
		// construction order/timing
		SettingsView statusView = new SettingsView(eccoService);
		// while no repository is open: the recent ones to open - the window was empty
		this.startView = new StartView(eccoService, newAction.action(), openAction.action(), forkAction.action(),
				() -> this.switchTo("Status", statusView));
		this.statusView = statusView;
		FeaturesView featuresView = new FeaturesView(eccoService, minimizationResults);
		RemotesView remotesView = new RemotesView(eccoService);
		CommitsView commitsView = new CommitsView(eccoService);
		ArtifactsView artifactsView = new ArtifactsView(eccoService, minimizationResults);
		ChartsView chartsView = new ChartsView(eccoService);
		VariantsView variantsView = new VariantsView(eccoService);
		ArtifactGraphView artifactsGraphView = new ArtifactGraphView(eccoService);
		DependencyGraphView dependencyGraphView = new DependencyGraphView(eccoService);
		KnowledgeGraphView knowledgeGraphView = new KnowledgeGraphView(eccoService);

		// Every content view above starts hidden except whichever switchTo() shows last (Status,
		// below) - without this, a TabVisibilityAware view (whose own tabVisible field defaults to
		// true, precisely so a caller that doesn't wire this up gets the old always-render behavior)
		// stays "visible" for its entire lifetime despite never actually being shown, since nothing
		// ever tells it otherwise until the user visits it at least once. That silently defeated each
		// such view's whole point of skipping expensive per-commit work while hidden - see
		// DependencyGraphView#tabVisible for the GraphStream crash that actually caused in practice
		// (repeatedly tearing down/recreating a GraphStream viewer, invisibly, on every single commit
		// of a multi-folder "Commit Multiple Versions" batch).
		for (Region contentView : List.of(featuresView, remotesView, commitsView, artifactsView,
				chartsView, variantsView, artifactsGraphView, dependencyGraphView, knowledgeGraphView)) {
			if (contentView instanceof TabVisibilityAware aware) {
				aware.setTabVisible(false);
			}
		}

		// "Content" actions - swap the shared content area to a pre-built view via switchTo.
		// it could not be reached again once another view was shown
		RibbonAction statusAction = new RibbonAction("Status", Feather.INFO,
				() -> {
					if (eccoService.isInitialized())
						this.switchTo("Status", statusView);
					else // nothing open: the recent repositories to open
						this.switchTo("Start", this.startView);
				}, false, "Which repository is open, and its folders - or, with none, the recent ones");
		RibbonAction variantsAction = new RibbonAction("Variants", Feather.LAYERS,
				() -> this.switchTo("Variants", variantsView), true, "The saved variants: configurations to check out by name");
		RibbonAction remotesAction = new RibbonAction("Remotes", Feather.SERVER,
				() -> this.switchTo("Remotes", remotesView), true, "The remotes this repository fetches from and pushes to");
		RibbonAction featuresAction = new RibbonAction("Feature Model", Feather.TOGGLE_RIGHT,
				() -> this.switchTo("Feature Model", featuresView), true, "The features as a tree, with the constraints mined from the commits");
		RibbonAction commitsAction = new RibbonAction("Commits", Feather.LIST,
				() -> this.switchTo("Commits", commitsView), true, "The commits, their artifacts, and a comparison of two");
		RibbonAction artifactsAction = new RibbonAction("Artifacts", Feather.FILE_TEXT,
				() -> this.switchTo("Artifacts", artifactsView), true, "The associations with their conditions, and the artifacts of those you tick");
		RibbonAction chartsAction = new RibbonAction("Charts", Feather.BAR_CHART_2,
				() -> this.switchTo("Charts", chartsView), true, "Artifacts per association and depth, revisions per feature");
		RibbonAction knowledgeGraphAction = new RibbonAction("Knowledge Graph", Feather.SHARE_2,
				() -> this.switchTo("Knowledge Graph", knowledgeGraphView), true, "Features, commits and associations, and how they connect");
		RibbonAction artifactGraphAction = new RibbonAction("Artifact Graph", Feather.GRID,
				() -> this.switchTo("Artifact Graph", artifactsGraphView), true, "The artifact tree as a graph, coloured by association");
		RibbonAction dependencyGraphAction = new RibbonAction("Dependency Graph", Feather.CODEPEN,
				() -> this.switchTo("Dependency Graph", dependencyGraphView), true, "Which associations' artifacts depend on which others'");

		RibbonTabSpec repositoryTab = new RibbonTabSpec("Repositories", Feather.DATABASE, List.of(
				new RibbonGroup(List.of(newAction, openAction, forkAction, closeAction)),
				new RibbonGroup(List.of(statusAction, showDirectoryAction))));

		RibbonTabSpec localTab = new RibbonTabSpec("Versions", Feather.HARD_DRIVE, List.of(
				new RibbonGroup(List.of(commitAction, commitMultipleAction, importGitAction)),
				new RibbonGroup(List.of(checkoutAction, variantsAction))));

		RibbonTabSpec distributedTab = new RibbonTabSpec("Collaborate", Feather.GLOBE, List.of(
				new RibbonGroup(List.of(remotesAction)),
				new RibbonGroup(List.of(fetchAction, pullAction, pushAction)),
				new RibbonGroup(List.of(serverAction))));

		// one tab for everything to look at: View and Visualize were two tabs of views, and which
		// one a view was under could not be guessed - the graphs are a group of their own
		RibbonTabSpec exploreTab = new RibbonTabSpec("Explore", Feather.COMPASS, List.of(
				new RibbonGroup(List.of(featuresAction, commitsAction, artifactsAction, chartsAction)),
				new RibbonGroup(List.of(knowledgeGraphAction, artifactGraphAction, dependencyGraphAction))));

		List<RibbonTabSpec> tabSpecs = new ArrayList<>(List.of(repositoryTab, localTab, distributedTab, exploreTab));
		if (!isMac) {
			// last: the first tab is the one shown at start, and that was Preferences/Quit instead
			// of New/Open (macOS has these in its native app menu - see the isMac block below)
			tabSpecs.add(new RibbonTabSpec("ECCO", Feather.MENU, List.of(
					new RibbonGroup(List.of(preferencesAction)),
					new RibbonGroup(List.of(helpAction, aboutAction)),
					new RibbonGroup(List.of(quitAction)))));
		}
		this.tabSpecs = List.copyOf(tabSpecs);

		RibbonBar ribbonBar = new RibbonBar(tabSpecs);
		// back to the Repositories tab: the recent repositories again, to open another or tidy the list
		ribbonBar.setOnTabSelected(label -> {
			if (label.equals(repositoryTab.label())) {
				this.startView.refresh();
				this.switchTo("Start", this.startView);
				this.contentArea.setDisable(false);
			}
		});
		this.setTop(ribbonBar);

		this.newButton = ribbonBar.getButton(newAction);
		this.openButton = ribbonBar.getButton(openAction);
		this.forkButton = ribbonBar.getButton(forkAction);
		for (RibbonTabSpec tabSpec : tabSpecs) {
			for (RibbonGroup group : tabSpec.groups()) {
				for (RibbonAction action : group.actions()) {
					if (action.requiresOpenRepository()) {
						this.requiresOpenRepository.add(ribbonBar.getButton(action));
					}
				}
			}
		}

		// Cmd+Q (quit) / Cmd+W (close repository) / Cmd+O (open) / Ctrl+, (preferences, non-mac) -
		// standard conventions, all registered directly on the Scene's own accelerator map since
		// ribbon buttons (unlike MenuItems) have no auto-registering accelerator mechanism of their
		// own - see installStandardAccelerators, applied here once the main Scene exists and again
		// per dialog Scene in openDialog, since each dialog is its own separate Scene/Stage.
		final RibbonAction finalPreferencesAction = preferencesAction;
		this.sceneProperty().addListener((observable, oldScene, newScene) -> {
			if (newScene != null) {
				installStandardAccelerators(newScene);

				// Cmd+O on macOS, Ctrl+O elsewhere (SHORTCUT_DOWN maps to the platform's own
				// shortcut modifier).
				newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
						openAction.action());
				newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::openHelp);
				// fired through their buttons, so they do nothing while the button is disabled
				newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
						() -> ribbonBar.getButton(newAction).fire());
				newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
						() -> ribbonBar.getButton(commitAction).fire());
				// the views in the order of the Explore tab: Cmd/Ctrl+1 to 7
				List<RibbonAction> views = List.of(featuresAction, commitsAction, artifactsAction, chartsAction,
						knowledgeGraphAction, artifactGraphAction, dependencyGraphAction);
				for (int i = 0; i < views.size(); i++) {
					RibbonAction view = views.get(i);
					newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i + 1)), KeyCombination.SHORTCUT_DOWN),
							() -> ribbonBar.getButton(view).fire());
				}

				if (finalPreferencesAction != null) {
					// Ctrl+, - not a strong platform convention outside macOS (which never
					// reaches this branch, finalPreferencesAction being null there), but harmless
					// and matches the accelerator macOS gets for free from its native app menu.
					newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
							finalPreferencesAction.action());
				}

				// Overrides installStandardAccelerators' Cmd+W above: this is the app's only
				// window (unlike a dialog's Scene, where closing the Stage is exactly right), so
				// closing it triggers Platform.implicitExit and quits the whole app - not what
				// "close" should mean here when a repository is open and you might want to open a
				// different one next. Mirrors closeAction's behavior instead; a no-op (like
				// closeAction's own disabled state) when no repository is open to close.
				newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
						this::closeRepositoryIfOpen);

				// Same reasoning again for the OS's own window-close control (the traffic-light
				// icon on macOS): by default, closing this Stage would quit the whole app via the
				// same implicitExit path. windowProperty() rather than reading newScene.getWindow()
				// directly here - primaryStage.setScene(scene) hasn't run yet at this point in
				// EccoGui.showMainStage(), so the Scene has no Window to attach to yet.
				newScene.windowProperty().addListener((observable2, oldWindow, newWindow) -> {
					if (newWindow instanceof Stage stage) {
						stage.setOnCloseRequest(event -> {
							event.consume();
							this.closeRepositoryIfOpen();
						});
					}
				});
			}
		});

		if (isMac) {
			// Deferred one pulse, same defensive reasoning as before this class moved off MenuBar:
			// NSApplication.mainMenu isn't guaranteed populated until the app is actually attached
			// to a shown Scene/Stage, and MenuToolkit.setApplicationMenu() falls back to REPLACING
			// THE WHOLE NSApplication.mainMenu if it finds none yet to patch item 0 of - see
			// MacNativeAdapter.setApplicationMenu()'s null-mainMenu branch. That fallback happens
			// to be harmless here regardless (this app menu is meant to be the ONLY thing in the
			// native macOS menu bar now - Repository/Local/etc. live in the in-window RibbonBar
			// instead, which never touches the native menu bar at all), but keeping the same
			// deferred timing avoids re-testing a fragile edge case that already caused real bugs
			// once.
			Platform.runLater(() -> {
				MenuToolkit tk = MenuToolkit.toolkit();

				MenuItem preferencesItem = new MenuItem("Preferences…");
				preferencesItem.setOnAction(event -> this.openDialog("Preferences", new PreferencesView(eccoService)));
				preferencesItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));

				// No About item: tk.createNativeAboutMenuItem() is explicitly @Beta in NSMenuFX's
				// own source, and it's not just a doc caveat - it SIGABRTs the whole JVM natively
				// (a crash no Java try/catch can guard against) when clicked. Nothing here asked
				// for an About item anyway, so it's simplest to leave it out rather than swap in
				// the non-Beta custom-Stage alternative (tk.createAboutMenuItem) untested.
				// our own windows, not the native About (above)
				MenuItem aboutItem = new MenuItem("About ECCO");
				aboutItem.setOnAction(event -> this.openAbout());
				MenuItem helpItem = new MenuItem("ECCO Help");
				helpItem.setOnAction(event -> this.openHelp());
				helpItem.setAccelerator(new KeyCodeCombination(KeyCode.SLASH, KeyCombination.META_DOWN, KeyCombination.SHIFT_DOWN));

				Menu appMenu = new Menu("ECCO");
				appMenu.getItems().setAll(
						aboutItem,
						helpItem,
						new SeparatorMenuItem(),
						preferencesItem,
						new SeparatorMenuItem(),
						tk.createHideMenuItem("ECCO"),
						tk.createHideOthersMenuItem(),
						tk.createUnhideAllMenuItem(),
						new SeparatorMenuItem(),
						tk.createQuitMenuItem("ECCO"));

				tk.setApplicationMenu(appMenu);
			});
		}


		this.headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
		this.headerLabel.setPadding(new Insets(8, 10, 8, 10));
		this.contentArea.setTop(headerLabel);
		this.setCenter(contentArea);

		this.eccoService.addListener(this);

		if (eccoService.isInitialized())
			this.switchTo("Status", statusView);
		else
			this.switchTo("Start", this.startView);
		this.wasInitialized = eccoService.isInitialized();
		this.updateView();
	}


	/** The ribbon's tabs, for the help. */
	private List<RibbonTabSpec> tabSpecs = List.of();

	/** What each button does, read from the ribbon, with the shortcuts - not modal: read it while you work. */
	private void openHelp() {
		Map<String, String> shortcuts = new LinkedHashMap<>();
		shortcuts.put("Cmd/Ctrl+N", "New...");
		shortcuts.put("Cmd/Ctrl+O", "Open...");
		shortcuts.put("Cmd/Ctrl+W", "Close Repository (in a dialog: close it)");
		shortcuts.put("Cmd/Ctrl+K", "Commit...");
		shortcuts.put("Cmd/Ctrl+1 to 7", "Feature Model, Commits, Artifacts, Charts, Knowledge Graph, Artifact Graph, Dependency Graph");
		shortcuts.put("Cmd/Ctrl+,", "Preferences...");
		shortcuts.put("F1", "Help");
		shortcuts.put("Cmd/Ctrl+Q", "Quit ECCO");
		this.openDialog("ECCO Help", withCloseButton(new HelpView(this.tabSpecs, shortcuts, () -> browse(AboutView.PROJECT_URL + "#readme"))), Modality.NONE);
	}

	private void openAbout() {
		this.openDialog("About ECCO", withCloseButton(new AboutView(() -> browse(AboutView.PROJECT_URL))));
	}

	/** [content] above a Close button - Enter and Esc close it too; there was only Cmd+W. */
	private static Parent withCloseButton(Parent content) {
		javafx.scene.control.Button close = new javafx.scene.control.Button("Close");
		close.setDefaultButton(true);
		close.setCancelButton(true);
		close.setOnAction(event -> close.getScene().getWindow().hide());
		javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(close);
		bar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
		bar.setPadding(new Insets(8, 12, 10, 12));
		BorderPane pane = new BorderPane(content);
		pane.setBottom(bar);
		return pane;
	}

	/** Opens [url] in the browser, or says why it could not. */
	private static void browse(String url) {
		try {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
				throw new UnsupportedOperationException("Opening a web page is not supported on this platform: " + url);
			Desktop.getDesktop().browse(java.net.URI.create(url));
		} catch (Exception e) {
			new ExceptionAlert(e).showAndWait();
		}
	}

	/** "Show in Finder" on macOS, "Show in Explorer" on Windows, "Show in File Manager" elsewhere. */
	static String showDirectoryLabel() {
		String os = System.getProperty("os.name", "");
		return os.startsWith("Mac") ? "Show in Finder" : os.startsWith("Windows") ? "Show in Explorer" : "Show in File Manager";
	}

	/**
	 * Swaps the shared content area to {@code view}, updating {@link #headerLabel} to
	 * {@code displayName}. Calls {@link TabVisibilityAware#setTabVisible} on the outgoing view
	 * (if it implements it) before swapping and on the incoming one after - same
	 * skip-expensive-work-while-hidden rationale each implementor's own javadoc already documents,
	 * just triggered by a ribbon button click now instead of {@code Tab.selectedProperty()}.
	 */
	private void switchTo(String displayName, Region view) {
		if (this.currentContentView instanceof TabVisibilityAware previous) {
			previous.setTabVisible(false);
		}
		this.headerLabel.setText(displayName);
		this.contentArea.setCenter(view);
		this.currentContentView = view;
		if (view instanceof TabVisibilityAware aware) {
			aware.setTabVisible(true);
		}
	}


	/**
	 * Opens {@link EccoService#getBaseDir()} in the platform's file manager (Finder/Explorer/whatever
	 * the Linux desktop environment registered) via {@link Desktop#open}, which dispatches to
	 * whatever application is associated with directories on the current platform - no OS-specific
	 * command needed.
	 */
	private void openBaseDirectory() {
		java.nio.file.Path baseDir = this.eccoService.getBaseDir();
		if (baseDir == null) {
			new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.WARNING, "No base directory is set.").showAndWait();
			return;
		}
		try {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				throw new UnsupportedOperationException("Opening a directory in the system file manager is not supported on this platform.");
			}
			Desktop.getDesktop().open(baseDir.toFile());
		} catch (Exception e) {
			new ExceptionAlert(e).showAndWait();
		}
	}


	private void openDialog(String title, Parent content) {
		this.openDialog(title, content, Modality.WINDOW_MODAL);
	}

	/**
	 * @param modality {@link Modality#WINDOW_MODAL} for the usual quick, one-shot operations (New,
	 *                 Open, Commit, ...) where blocking the main window is harmless and arguably
	 *                 safer; {@link Modality#NONE} for a long-running, review-driven operation
	 *                 (Import from Git) where forcing the user to sit and wait through possibly
	 *                 dozens of review/auto-import steps before touching the rest of the app is the
	 *                 actual complaint. The main window and this dialog both still react to the same
	 *                 {@link at.jku.isse.ecco.service.listener.EccoListener} broadcasts either way -
	 *                 modality only ever controlled whether the OS let the user click into the main
	 *                 window while a background write was in flight, never whether that was safe;
	 *                 see {@code at.jku.isse.ecco.service.ListenerRegistry}'s own javadoc for the
	 *                 one still-open gap in that (GUI code holding a direct reference to a live
	 *                 {@code Association}/artifact and calling methods on it entirely outside
	 *                 {@code EccoService}'s own synchronization) - non-modal makes that overlap
	 *                 happen in practice far more often than before, not a new category of risk.
	 */
	private void openDialog(String title, Parent content, Modality modality) {
		Dialogs.open(MainView.this.getScene().getWindow(), title, content, modality);
	}


	/**
	 * Cmd+Q quits the whole app (mirrors the OS quit path: closing the last window already triggers
	 * this today since {@code Platform.implicitExit} defaults to true, so this just gives it a
	 * shortcut and makes it work even while a dialog, not the main window, has focus). Cmd+W closes
	 * whichever window ({@code scene}) is currently focused - correct as-is for a dialog, but the
	 * {@code sceneProperty()} listener above overrides this for the main window's own Scene, since
	 * closing that Stage would quit the whole app via the {@code implicitExit} path instead of just
	 * closing the repository. Ctrl+Q/Ctrl+W on non-macOS platforms (SHORTCUT_DOWN maps to the
	 * platform's own shortcut modifier).
	 */
	private static void installStandardAccelerators(Scene scene) {
		Dialogs.installStandardAccelerators(scene);
	}


	/** Same action as the Repository tab's Close button; a no-op when there's no open repository to close. */
	private void closeRepositoryIfOpen() {
		if (this.eccoService.isInitialized()) {
			this.eccoService.close();
		}
	}


	// the repository the synchronized-folder warning was shown for: once each time one is opened
	private java.nio.file.Path warnedRepository;

	private void updateView() {
		boolean initialized = this.eccoService.isInitialized();

		java.nio.file.Path repository = initialized ? this.eccoService.getRepositoryDir() : null;
		if (repository != null && !repository.equals(this.warnedRepository))
			at.jku.isse.ecco.service.SyncedFolders.warning(repository).ifPresent(Notifications::info);
		this.warnedRepository = repository;

		this.newButton.setDisable(initialized);
		this.openButton.setDisable(initialized);
		this.forkButton.setDisable(initialized);
		for (Button button : this.requiresOpenRepository) {
			button.setDisable(!initialized);
		}
		// opened: its status; closed: the start page again
		if (initialized && !this.wasInitialized && this.currentContentView == this.startView)
			this.switchTo("Status", this.statusView);
		else if (!initialized && this.wasInitialized) {
			this.startView.refresh();
			this.switchTo("Start", this.startView);
		}
		this.wasInitialized = initialized;
		this.contentArea.setDisable(!initialized && this.currentContentView != this.startView);
	}

	private StartView startView;
	private SettingsView statusView;
	/** Whether a repository was open at the last update - opening and closing one switch the view. */
	private boolean wasInitialized;


	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(this::updateView);
	}

}
