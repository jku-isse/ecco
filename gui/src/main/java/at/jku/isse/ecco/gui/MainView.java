package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.gui.view.artifacts.ArtifactsView;
import at.jku.isse.ecco.gui.view.operation.checkout.CheckoutView;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A single {@link MenuBar} (Repository / Local / Distributed / Analysis / Visualization /
 * Preferences) replaces what used to be two separate navigation layers - an 11-button action
 * toolbar and a flat, ungrouped 11-tab strip below it - collapsing them into one, organized by
 * workflow instead of by "is this a dialog or a tab". "Action" items (New, Open, Close, Commit,
 * Import From Git, Checkout, Fetch, Pull, Push) behave exactly as their old toolbar buttons did,
 * via {@link #openDialog}. "Content" items (Variants, Remotes, Feature Model, Commits,
 * Associations, Artifacts, Charts, Knowledge Graph, Artifact Graph, Dependency Graph) swap a single
 * shared content area via {@link #switchTo} - the same "one view visible at a time" model the tab
 * pane already had, just menu-driven instead of tab-driven. Each view's own internal toolbar (e.g.
 * Knowledge Graph's entity/layout controls) is untouched either way. Status (repository/base
 * directory info) has no menu entry of its own - it's simply the default view {@link #switchTo}
 * lands on at startup, before any other item has been picked, so a separate way to navigate back
 * to it would be redundant.
 * <p>
 * Settings (Plugins/LLM/Minimization/Lilypond/Server, all now one {@link PreferencesView} dialog)
 * is reached differently depending on platform: on macOS it's wired as a "Preferences…" item in
 * the real, native "ECCO" application menu (the bold, leftmost one) via
 * {@link MenuToolkit#setApplicationMenu} - {@code java.awt.Desktop.setPreferencesHandler} looks
 * like the obvious way to do this but does NOT work here: it hooks in through AWT's own Cocoa
 * bridge, which is a separate native toolkit from the Glass bridge this class's own
 * {@link MenuBar#setUseSystemMenuBar} already uses, so an AWT-registered handler is never
 * reflected in the menu Glass actually has on screen. {@code MenuToolkit.setApplicationMenu}
 * instead talks to AppKit's live {@code NSApplication.mainMenu} directly (via JNA) and only
 * replaces its item 0 (the app menu), leaving everything Glass installed at positions 1+ (our own
 * Repository/Local/etc. menus) untouched - see the {@code isMac} block below. Where that's not
 * available (Windows/Linux), there's no equivalent native app menu to hook into, so a plain
 * leftmost "ECCO" {@link Menu} (Preferences.../Quit) is added to the regular MenuBar instead,
 * mirroring the native one's position and contents as closely as an embedded menu can.
 */
public class MainView extends BorderPane implements EccoListener {
	private final EccoService eccoService;

	private final MenuItem newMenuItem = new MenuItem("New");
	private final MenuItem openMenuItem = new MenuItem("Open...");
	private final MenuItem closeMenuItem = new MenuItem("Close");

	private final MenuItem commitMenuItem = new MenuItem("Commit...");
	private final MenuItem importGitMenuItem = new MenuItem("Import From Git...");
	private final MenuItem checkoutMenuItem = new MenuItem("Checkout...");

	private final MenuItem fetchMenuItem = new MenuItem("Fetch...");
	private final MenuItem pullMenuItem = new MenuItem("Pull...");
	private final MenuItem pushMenuItem = new MenuItem("Push...");

	/**
	 * Every menu item disabled while no repository is open - see {@link #updateView()}. Populated
	 * in the constructor once the content items exist too (New, Open, and the whole Preferences
	 * menu are the only items that stay enabled either way - everything else, including every
	 * content item, needs an open repository to mean anything).
	 */
	private final List<MenuItem> requiresOpenRepository = new ArrayList<>(List.of(
			closeMenuItem, commitMenuItem, importGitMenuItem, checkoutMenuItem,
			fetchMenuItem, pullMenuItem, pushMenuItem));

	private final Label headerLabel = new Label();
	private final BorderPane contentArea = new BorderPane();
	private Region currentContentView;

	public MainView(EccoService eccoService) {
		this.eccoService = eccoService;

		this.newMenuItem.setOnAction(event -> this.openDialog("New", new InitView(eccoService)));
		this.openMenuItem.setOnAction(event -> this.openDialog("Open", new OpenView(eccoService)));
		this.closeMenuItem.setOnAction(event -> this.eccoService.close());

		this.commitMenuItem.setOnAction(event -> this.openDialog("Commit", new CommitView(eccoService)));
		this.importGitMenuItem.setOnAction(event -> this.openDialog("Import from Git", new ImportGitView(eccoService)));
		this.checkoutMenuItem.setOnAction(event -> this.openDialog("Checkout", new CheckoutView(eccoService)));

		this.fetchMenuItem.setOnAction(event -> this.openDialog("Fetch", new FetchView(eccoService)));
		this.pullMenuItem.setOnAction(event -> this.openDialog("Pull", new PullView(eccoService)));
		this.pushMenuItem.setOnAction(event -> this.openDialog("Push", new PushView(eccoService)));

		// same check NSMenuFX's own NativeAdapterProvider uses internally to pick between its
		// MacNativeAdapter and a no-op DummyNativeAdapter - mirrored here so this class can decide
		// whether it still needs its own JavaFX-menu fallback "ECCO" menu (see below and the
		// isMac block after the MenuBar is built).
		boolean isMac = System.getProperty("os.name", "").startsWith("Mac");

		Menu eccoMenu = null;
		if (!isMac) {
			MenuItem settingsMenuItem = new MenuItem("Preferences...");
			settingsMenuItem.setOnAction(event -> this.openDialog("Settings", new PreferencesView(eccoService)));
			// Ctrl+, - not a strong platform convention outside macOS (which never reaches this
			// branch), but harmless and matches the accelerator macOS gets for free from the OS.
			settingsMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));

			MenuItem quitMenuItem = new MenuItem("Quit ECCO");
			quitMenuItem.setOnAction(event -> Platform.exit());
			quitMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));

			eccoMenu = new Menu("ECCO");
			eccoMenu.getItems().setAll(settingsMenuItem, new SeparatorMenuItem(), quitMenuItem);
		}

		// Cmd+O on macOS, Ctrl+O elsewhere (SHORTCUT_DOWN maps to the platform's own shortcut
		// modifier). A MenuItem accelerator registers itself against the Scene once the MenuBar
		// becomes part of one, so - unlike the old toolbar Button this replaces - this needs no
		// sceneProperty() listener workaround.
		this.openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));

		// Cmd+Q (quit) / Cmd+W (close window) - standard macOS conventions, same shortcut modifier
		// as above elsewhere. Not tied to any single MenuItem/menu (there's no visible "Quit" or
		// "Close Window" item), so these register directly on each Scene's own accelerator map
		// instead - see installStandardAccelerators, applied here once the main Scene exists and
		// again per dialog Scene in openDialog, since each dialog is its own separate Scene/Stage.
		this.sceneProperty().addListener((observable, oldScene, newScene) -> {
			if (newScene != null) {
				installStandardAccelerators(newScene);
				// Overrides installStandardAccelerators' Cmd+W above: this is the app's only
				// window (unlike a dialog's Scene, where closing the Stage is exactly right), so
				// closing it triggers Platform.implicitExit and quits the whole app - not what
				// "close" should mean here when a repository is open and you might want to open a
				// different one next. Mirrors closeMenuItem's action instead; a no-op (like
				// closeMenuItem's own disabled state) when no repository is open to close.
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


		// one shared "Minimize Presence Conditions" run, triggered from the Feature Model view and
		// observed by every other view that displays a minimized condition
		MinimizationResults minimizationResults = new MinimizationResults(eccoService);

		// every content view, constructed eagerly exactly as before - only how each becomes
		// visible changed (menu-driven swap instead of a tab click), not construction order/timing
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

		MenuItem variantsMenuItem = new MenuItem("Variants");
		variantsMenuItem.setOnAction(event -> this.switchTo("Variants", variantsView));
		MenuItem remotesMenuItem = new MenuItem("Remotes");
		remotesMenuItem.setOnAction(event -> this.switchTo("Remotes", remotesView));
		MenuItem featuresMenuItem = new MenuItem("Feature Model");
		featuresMenuItem.setOnAction(event -> this.switchTo("Feature Model", featuresView));
		MenuItem commitsMenuItem = new MenuItem("Commits");
		commitsMenuItem.setOnAction(event -> this.switchTo("Commits", commitsView));
		MenuItem associationsMenuItem = new MenuItem("Associations");
		associationsMenuItem.setOnAction(event -> this.switchTo("Associations", associationsView));
		MenuItem artifactsMenuItem = new MenuItem("Artifacts");
		artifactsMenuItem.setOnAction(event -> this.switchTo("Artifacts", artifactsView));
		MenuItem chartsMenuItem = new MenuItem("Charts");
		chartsMenuItem.setOnAction(event -> this.switchTo("Charts", chartsView));
		MenuItem knowledgeGraphMenuItem = new MenuItem("Knowledge Graph");
		knowledgeGraphMenuItem.setOnAction(event -> this.switchTo("Knowledge Graph", knowledgeGraphView));
		MenuItem artifactGraphMenuItem = new MenuItem("Artifact Graph");
		artifactGraphMenuItem.setOnAction(event -> this.switchTo("Artifact Graph", artifactsGraphView));
		MenuItem dependencyGraphMenuItem = new MenuItem("Dependency Graph");
		dependencyGraphMenuItem.setOnAction(event -> this.switchTo("Dependency Graph", dependencyGraphView));

		// every content item needs an open repository to mean anything - see requiresOpenRepository's javadoc
		this.requiresOpenRepository.addAll(List.of(
				variantsMenuItem, remotesMenuItem, featuresMenuItem, commitsMenuItem,
				associationsMenuItem, artifactsMenuItem, chartsMenuItem, knowledgeGraphMenuItem,
				artifactGraphMenuItem, dependencyGraphMenuItem));


		Menu repositoryMenu = new Menu("Repository");
		repositoryMenu.getItems().setAll(newMenuItem, openMenuItem, closeMenuItem);

		Menu localMenu = new Menu("Local");
		localMenu.getItems().setAll(commitMenuItem, importGitMenuItem, checkoutMenuItem, new SeparatorMenuItem(), variantsMenuItem);

		Menu distributedMenu = new Menu("Distributed");
		distributedMenu.getItems().setAll(remotesMenuItem, new SeparatorMenuItem(), fetchMenuItem, pullMenuItem, pushMenuItem);

		Menu analysisMenu = new Menu("Analysis");
		analysisMenu.getItems().setAll(featuresMenuItem, commitsMenuItem, associationsMenuItem, artifactsMenuItem, chartsMenuItem);

		Menu visualizationMenu = new Menu("Visualization");
		visualizationMenu.getItems().setAll(knowledgeGraphMenuItem, artifactGraphMenuItem, dependencyGraphMenuItem);

		List<Menu> menus = new ArrayList<>();
		if (eccoMenu != null) {
			// leftmost, mirroring where macOS's native app menu always sits - see isMac block below.
			menus.add(eccoMenu);
		}
		menus.addAll(List.of(repositoryMenu, localMenu, distributedMenu, analysisMenu, visualizationMenu));

		MenuBar menuBar = new MenuBar();
		menuBar.getMenus().setAll(menus);
		// on macOS, renders as the real system menu bar at the top of the screen instead of embedded
		// in the window - silently has no effect on platforms without a global menu bar (Windows/
		// Linux, which this project also ships), so it's safe to always set.
		menuBar.setUseSystemMenuBar(true);
		this.setTop(menuBar);

		if (isMac) {
			// Deferred one pulse: Glass installs its native menu bar (from useSystemMenuBar(true)
			// above) asynchronously once this MainView is actually part of a shown Scene/Stage, and
			// MenuToolkit.setApplicationMenu() falls back to REPLACING THE WHOLE NSApplication.
			// mainMenu (wiping out Repository/Local/etc.) if it runs before that's happened and
			// finds no existing native menu to patch item 0 of - see MacNativeAdapter.
			// setApplicationMenu()'s null-mainMenu branch. Running this after the current pulse
			// (during which MainView gets attached and shown) reliably avoids that.
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
	 * just triggered by a menu click now instead of {@code Tab.selectedProperty()}.
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
	 * closing that Stage would quit the whole app via the same {@code implicitExit} path instead of
	 * just closing the repository. Ctrl+Q/Ctrl+W on non-macOS platforms (SHORTCUT_DOWN maps to the
	 * platform's own shortcut modifier).
	 */
	private static void installStandardAccelerators(Scene scene) {
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), Platform::exit);
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
				() -> ((Stage) scene.getWindow()).close());
	}


	/** Same action as {@link #closeMenuItem}; a no-op when there's no open repository to close. */
	private void closeRepositoryIfOpen() {
		if (this.eccoService.isInitialized()) {
			this.eccoService.close();
		}
	}


	private void updateView() {
		boolean initialized = this.eccoService.isInitialized();

		this.newMenuItem.setDisable(initialized);
		this.openMenuItem.setDisable(initialized);
		for (MenuItem menuItem : this.requiresOpenRepository) {
			menuItem.setDisable(!initialized);
		}
		this.contentArea.setDisable(!initialized);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(this::updateView);
	}

}
