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
import java.util.List;

/**
 * A {@link RibbonBar} (Repository / Local / Distributed / Analysis / Visualization, plus a
 * leftmost "ECCO" tab on non-macOS platforms) replaces the {@link MenuBar} this class used to
 * build - same organizing principle as before (grouped by workflow, not by "is this a dialog or a
 * tab"), different chrome: each former top-level {@code Menu} is now a ribbon tab, and each former
 * flat {@code MenuItem} list is now a "band" of buttons shown below the tab row once its tab is
 * selected, instead of a dropdown. This is navigation only - every button runs the exact same
 * action a {@code MenuItem} used to, and no content view's own internal toolbar (e.g. Knowledge
 * Graph's entity/layout controls) is touched by this change either way.
 * <p>
 * "Action" actions (New, Open, Close, Commit, Import From Git, Checkout, Fetch, Pull, Push) behave
 * exactly as their old {@code MenuItem}s did, via {@link #openDialog}. "Content" actions (Variants,
 * Remotes, Feature Model, Commits, Associations, Artifacts, Charts, Knowledge Graph, Artifact
 * Graph, Dependency Graph) swap a single shared content area via {@link #switchTo} - the same "one
 * view visible at a time" model as before, just ribbon-driven instead of menu-driven. Status
 * (repository/base directory info) still has no tab/button of its own - it's simply the default
 * view {@link #switchTo} lands on at startup, before anything else has been picked.
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

	private final Label headerLabel = new Label();
	private final BorderPane contentArea = new BorderPane();
	private Region currentContentView;

	public MainView(EccoService eccoService) {
		this.eccoService = eccoService;

		// same check NSMenuFX's own NativeAdapterProvider uses internally to pick between its
		// MacNativeAdapter and a no-op DummyNativeAdapter - mirrored here so this class can decide
		// whether it still needs its own ribbon "ECCO" tab fallback (see below and the isMac block
		// after the RibbonBar is built).
		boolean isMac = System.getProperty("os.name", "").startsWith("Mac");

		// "Action" actions - open a dialog or act directly, independent of any content view.
		RibbonAction newAction = new RibbonAction("New", Feather.FILE_PLUS,
				() -> this.openDialog("New", new InitView(eccoService)), false);
		RibbonAction openAction = new RibbonAction("Open...", Feather.FOLDER,
				() -> this.openDialog("Open", new OpenView(eccoService)), false);
		RibbonAction closeAction = new RibbonAction("Close", Feather.X,
				() -> this.eccoService.close(), true);

		RibbonAction commitAction = new RibbonAction("Commit...", Feather.CHECK_CIRCLE,
				() -> this.openDialog("Commit", new CommitView(eccoService)), true);
		RibbonAction importGitAction = new RibbonAction("Import From Git...", Feather.DOWNLOAD,
				() -> this.openDialog("Import from Git", new ImportGitView(eccoService)), true);
		RibbonAction checkoutAction = new RibbonAction("Checkout...", Feather.GIT_BRANCH,
				() -> this.openDialog("Checkout", new CheckoutView(eccoService)), true);
		RibbonAction openDirectoryAction = new RibbonAction("Open Directory...", Feather.EXTERNAL_LINK,
				() -> this.openBaseDirectory(), true);

		RibbonAction fetchAction = new RibbonAction("Fetch...", Feather.ARROW_DOWN_CIRCLE,
				() -> this.openDialog("Fetch", new FetchView(eccoService)), true);
		RibbonAction pullAction = new RibbonAction("Pull...", Feather.DOWNLOAD_CLOUD,
				() -> this.openDialog("Pull", new PullView(eccoService)), true);
		RibbonAction pushAction = new RibbonAction("Push...", Feather.UPLOAD_CLOUD,
				() -> this.openDialog("Push", new PushView(eccoService)), true);

		// Preferences/Quit - only built as ribbon actions on non-mac platforms; on macOS these are
		// reached via the native app menu instead (see the isMac block below), and preferencesAction
		// stays null so the Scene-level Cmd+, accelerator below is skipped too (macOS gets that
		// accelerator for free from the native menu item instead).
		RibbonAction preferencesAction = null;
		RibbonAction quitAction = null;
		if (!isMac) {
			preferencesAction = new RibbonAction("Preferences...", Feather.SETTINGS,
					() -> this.openDialog("Settings", new PreferencesView(eccoService)), false);
			quitAction = new RibbonAction("Quit ECCO", Feather.LOG_OUT, Platform::exit, false);
		}

		// one shared "Minimize Presence Conditions" run, triggered from the Feature Model view and
		// observed by every other view that displays a minimized condition
		MinimizationResults minimizationResults = new MinimizationResults(eccoService);

		// every content view, constructed eagerly exactly as before - only how each becomes
		// visible changed (ribbon-driven swap instead of a tab click or dropdown menu), not
		// construction order/timing
		SettingsView statusView = new SettingsView(eccoService);
		FeaturesView featuresView = new FeaturesView(eccoService, minimizationResults);
		RemotesView remotesView = new RemotesView(eccoService);
		CommitsView commitsView = new CommitsView(eccoService);
		AssociationsView associationsView = new AssociationsView(eccoService, minimizationResults);
		ArtifactsView artifactsView = new ArtifactsView(eccoService, minimizationResults);
		ChartsView chartsView = new ChartsView(eccoService);
		VariantsView variantsView = new VariantsView(eccoService);
		ArtifactGraphView artifactsGraphView = new ArtifactGraphView(eccoService);
		DependencyGraphView dependencyGraphView = new DependencyGraphView(eccoService);
		KnowledgeGraphView knowledgeGraphView = new KnowledgeGraphView(eccoService);

		// "Content" actions - swap the shared content area to a pre-built view via switchTo.
		RibbonAction variantsAction = new RibbonAction("Variants", Feather.LAYERS,
				() -> this.switchTo("Variants", variantsView), true);
		RibbonAction remotesAction = new RibbonAction("Remotes", Feather.SERVER,
				() -> this.switchTo("Remotes", remotesView), true);
		RibbonAction featuresAction = new RibbonAction("Feature Model", Feather.GIT_MERGE,
				() -> this.switchTo("Feature Model", featuresView), true);
		RibbonAction commitsAction = new RibbonAction("Commits", Feather.LIST,
				() -> this.switchTo("Commits", commitsView), true);
		RibbonAction associationsAction = new RibbonAction("Associations", Feather.LINK_2,
				() -> this.switchTo("Associations", associationsView), true);
		RibbonAction artifactsAction = new RibbonAction("Artifacts", Feather.BOX,
				() -> this.switchTo("Artifacts", artifactsView), true);
		RibbonAction chartsAction = new RibbonAction("Charts", Feather.BAR_CHART_2,
				() -> this.switchTo("Charts", chartsView), true);
		RibbonAction knowledgeGraphAction = new RibbonAction("Knowledge Graph", Feather.SHARE_2,
				() -> this.switchTo("Knowledge Graph", knowledgeGraphView), true);
		RibbonAction artifactGraphAction = new RibbonAction("Artifact Graph", Feather.GIT_COMMIT,
				() -> this.switchTo("Artifact Graph", artifactsGraphView), true);
		RibbonAction dependencyGraphAction = new RibbonAction("Dependency Graph", Feather.CODEPEN,
				() -> this.switchTo("Dependency Graph", dependencyGraphView), true);

		RibbonTabSpec repositoryTab = new RibbonTabSpec("Repository", Feather.FOLDER, List.of(
				new RibbonGroup(List.of(newAction, openAction, closeAction))));

		RibbonTabSpec localTab = new RibbonTabSpec("Local", Feather.HARD_DRIVE, List.of(
				new RibbonGroup(List.of(commitAction, checkoutAction)),
				new RibbonGroup(List.of(variantsAction)),
				new RibbonGroup(List.of(importGitAction)),
				new RibbonGroup(List.of(openDirectoryAction))));

		RibbonTabSpec distributedTab = new RibbonTabSpec("Distributed", Feather.GLOBE, List.of(
				new RibbonGroup(List.of(remotesAction)),
				new RibbonGroup(List.of(fetchAction, pullAction, pushAction))));

		RibbonTabSpec analysisTab = new RibbonTabSpec("Analysis", Feather.SEARCH, List.of(
				new RibbonGroup(List.of(featuresAction, commitsAction, associationsAction, artifactsAction, chartsAction))));

		RibbonTabSpec visualizationTab = new RibbonTabSpec("Visualization", Feather.EYE, List.of(
				new RibbonGroup(List.of(knowledgeGraphAction, artifactGraphAction, dependencyGraphAction))));

		List<RibbonTabSpec> tabSpecs = new ArrayList<>();
		if (!isMac) {
			// leftmost, mirroring where macOS's native app menu always sits - see isMac block below.
			tabSpecs.add(new RibbonTabSpec("ECCO", Feather.SETTINGS, List.of(
					new RibbonGroup(List.of(preferencesAction)),
					new RibbonGroup(List.of(quitAction)))));
		}
		tabSpecs.addAll(List.of(repositoryTab, localTab, distributedTab, analysisTab, visualizationTab));

		RibbonBar ribbonBar = new RibbonBar(tabSpecs);
		this.setTop(ribbonBar);

		this.newButton = ribbonBar.getButton(newAction);
		this.openButton = ribbonBar.getButton(openAction);
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
				preferencesItem.setOnAction(event -> this.openDialog("Settings", new PreferencesView(eccoService)));
				preferencesItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));

				// No About item: tk.createNativeAboutMenuItem() is explicitly @Beta in NSMenuFX's
				// own source, and it's not just a doc caveat - it SIGABRTs the whole JVM natively
				// (a crash no Java try/catch can guard against) when clicked. Nothing here asked
				// for an About item anyway, so it's simplest to leave it out rather than swap in
				// the non-Beta custom-Stage alternative (tk.createAboutMenuItem) untested.
				Menu appMenu = new Menu("ECCO");
				appMenu.getItems().setAll(
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

		this.switchTo("Status", statusView);
		this.updateView();
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
			new Alert(Alert.AlertType.WARNING, "No base directory is set.").showAndWait();
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
		final Stage dialog = new Stage();
		dialog.initStyle(StageStyle.UTILITY);
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(MainView.this.getScene().getWindow());

		Scene dialogScene = new Scene(content);
		installStandardAccelerators(dialogScene);
		dialog.setScene(dialogScene);
		dialog.setTitle(title);

		dialog.show();
		dialog.requestFocus();
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
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), Platform::exit);
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
				() -> ((Stage) scene.getWindow()).close());
	}


	/** Same action as the Repository tab's Close button; a no-op when there's no open repository to close. */
	private void closeRepositoryIfOpen() {
		if (this.eccoService.isInitialized()) {
			this.eccoService.close();
		}
	}


	private void updateView() {
		boolean initialized = this.eccoService.isInitialized();

		this.newButton.setDisable(initialized);
		this.openButton.setDisable(initialized);
		for (Button button : this.requiresOpenRepository) {
			button.setDisable(!initialized);
		}
		this.contentArea.setDisable(!initialized);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(this::updateView);
	}

}
