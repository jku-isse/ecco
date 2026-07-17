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
 * Import From Git, Checkout, Fetch, Pull, Push, Settings, Server) behave exactly as their old
 * toolbar buttons did, via {@link #openDialog}. "Content" items (Status, Variants, Remotes, Feature
 * Model, Commits, Associations, Artifacts, Charts, Knowledge Graph, Artifact Graph, Dependency
 * Graph) swap a single shared content area via {@link #switchTo} - the same "one view visible at a
 * time" model the tab pane already had, just menu-driven instead of tab-driven. Each view's own
 * internal toolbar (e.g. Knowledge Graph's entity/layout controls) is untouched either way.
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

	private final MenuItem serverMenuItem = new MenuItem("Server...");

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

		this.serverMenuItem.setOnAction(event -> this.openDialog("Server", new ServerView(eccoService)));

		MenuItem pluginsMenuItem = new MenuItem("Plugins...");
		pluginsMenuItem.setOnAction(event -> this.openDialog("Plugins", new PreferencesView(PreferencesView.Section.PLUGINS)));
		MenuItem llmSettingsMenuItem = new MenuItem("LLM Settings...");
		llmSettingsMenuItem.setOnAction(event -> this.openDialog("LLM Settings", new PreferencesView(PreferencesView.Section.LLM)));
		MenuItem minimizationSettingsMenuItem = new MenuItem("Minimization Settings...");
		minimizationSettingsMenuItem.setOnAction(event -> this.openDialog("Minimization Settings", new PreferencesView(PreferencesView.Section.MINIMIZATION)));
		MenuItem lilypondSettingsMenuItem = new MenuItem("Lilypond Settings...");
		lilypondSettingsMenuItem.setOnAction(event -> this.openDialog("Lilypond Settings", new PreferencesView(PreferencesView.Section.LILYPOND)));

		// Cmd+O on macOS, Ctrl+O elsewhere (SHORTCUT_DOWN maps to the platform's own shortcut
		// modifier). A MenuItem accelerator registers itself against the Scene once the MenuBar
		// becomes part of one, so - unlike the old toolbar Button this replaces - this needs no
		// sceneProperty() listener workaround.
		this.openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));


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

		MenuItem statusMenuItem = new MenuItem("Status");
		statusMenuItem.setOnAction(event -> this.switchTo("Status", statusView));
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
				statusMenuItem, variantsMenuItem, remotesMenuItem, featuresMenuItem, commitsMenuItem,
				associationsMenuItem, artifactsMenuItem, chartsMenuItem, knowledgeGraphMenuItem,
				artifactGraphMenuItem, dependencyGraphMenuItem));


		Menu repositoryMenu = new Menu("Repository");
		repositoryMenu.getItems().setAll(newMenuItem, openMenuItem, closeMenuItem, new SeparatorMenuItem(), statusMenuItem);

		Menu localMenu = new Menu("Local");
		localMenu.getItems().setAll(commitMenuItem, importGitMenuItem, checkoutMenuItem, new SeparatorMenuItem(), variantsMenuItem);

		Menu distributedMenu = new Menu("Distributed");
		distributedMenu.getItems().setAll(remotesMenuItem, new SeparatorMenuItem(), fetchMenuItem, pullMenuItem, pushMenuItem);

		Menu analysisMenu = new Menu("Analysis");
		analysisMenu.getItems().setAll(featuresMenuItem, commitsMenuItem, associationsMenuItem, artifactsMenuItem, chartsMenuItem);

		Menu visualizationMenu = new Menu("Visualization");
		visualizationMenu.getItems().setAll(knowledgeGraphMenuItem, artifactGraphMenuItem, dependencyGraphMenuItem);

		Menu preferencesMenu = new Menu("Preferences");
		preferencesMenu.getItems().setAll(pluginsMenuItem, llmSettingsMenuItem, minimizationSettingsMenuItem,
				lilypondSettingsMenuItem, new SeparatorMenuItem(), serverMenuItem);

		MenuBar menuBar = new MenuBar();
		menuBar.getMenus().setAll(repositoryMenu, localMenu, distributedMenu, analysisMenu, visualizationMenu, preferencesMenu);
		this.setTop(menuBar);


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
		dialog.setScene(dialogScene);
		dialog.setTitle(title);

		dialog.show();
		dialog.requestFocus();
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
