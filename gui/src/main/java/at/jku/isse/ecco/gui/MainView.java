package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.gui.view.*;
import at.jku.isse.ecco.gui.view.graph.ArtifactGraphView;
import at.jku.isse.ecco.gui.view.graph.CommitGraphView;
import at.jku.isse.ecco.gui.view.graph.DependencyGraphView;
import at.jku.isse.ecco.gui.view.operation.*;
import at.jku.isse.ecco.gui.view.operation.InitView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainView extends BorderPane implements EccoListener {

	private EccoService eccoService;


	private Button openButton = new Button("Open");
	private Button closeButton = new Button("Close");

	private Button initButton = new Button("Init");
	private Button forkButton = new Button("Fork");

	private Button commitButton = new Button("Commit");
	private Button checkoutButton = new Button("Checkout");

	private Button fetchButton = new Button("Fetch");
	private Button pullButton = new Button("Pull");
	private Button pushButton = new Button("Push");

	private Button serverButton = new Button("Server");


	public MainView(EccoService eccoService) {
		this.eccoService = eccoService;

//		// menu
//		final Menu fileMenu = new Menu("File");
//
//		MenuBar menuBar = new MenuBar();
//		menuBar.getMenus().addAll(fileMenu);
//		this.setTop(menuBar);

		this.openButton.setOnAction(event -> this.openDialog("Open", new OpenView(eccoService)));
		this.initButton.setOnAction(event -> this.openDialog("Init", new InitView(eccoService)));
		//this.forkButton.setOnAction(event -> this.openDialog("Fork", new ForkView(eccoService)));
		this.closeButton.setOnAction(event -> this.eccoService.close());

		this.commitButton.setOnAction(event -> this.openDialog("Commit", new CommitView(eccoService)));
		this.checkoutButton.setOnAction(event -> this.openDialog("Checkout", new CheckoutView(eccoService)));

		this.fetchButton.setOnAction(event -> this.openDialog("Fetch", new FetchView(eccoService)));
		this.pullButton.setOnAction(event -> this.openDialog("Pull", new PullView(eccoService)));
		this.pushButton.setOnAction(event -> this.openDialog("Push", new PushView(eccoService)));

		this.serverButton.setOnAction(event -> this.openDialog("Server", new ServerView(eccoService)));


		// toolbar
		ToolBar toolBar = new ToolBar();
		toolBar.getItems().addAll(openButton, initButton, new Separator(), closeButton, new Separator(), commitButton, checkoutButton, new Separator(), fetchButton, pullButton, pushButton, new Separator(), serverButton, new Separator());
		this.setTop(toolBar);


		// tabs
		TabPane tabPane = new TabPane();
		this.setCenter(tabPane);

		// status
		Tab statusTab = new Tab();
		statusTab.setText("Status");
		statusTab.setClosable(false);
		tabPane.getTabs().add(statusTab);

		SettingsView statusView = new SettingsView(eccoService);
		statusTab.setContent(statusView);

//		// operations
//		Tab operationsTab = new Tab();
//		operationsTab.setText("Operations");
//		operationsTab.setClosable(false);
//		tabPane.getTabs().add(operationsTab);
//
//		OperationsView operationsView = new OperationsView(eccoService);
//		operationsTab.setContent(operationsView);


		// CORE

		// features
		Tab featuresTab = new Tab();
		featuresTab.setText("Features");
		featuresTab.setClosable(false);
		tabPane.getTabs().add(featuresTab);

		FeaturesView featuresView = new FeaturesView(eccoService);
		featuresTab.setContent(featuresView);

		// remotes
		Tab remotesTab = new Tab();
		remotesTab.setText("Remotes");
		remotesTab.setClosable(false);
		tabPane.getTabs().add(remotesTab);

		RemotesView remotesView = new RemotesView(eccoService);
		remotesTab.setContent(remotesView);

		// commits
		Tab commitsTab = new Tab();
		commitsTab.setText("Commits");
		commitsTab.setClosable(false);
		tabPane.getTabs().add(commitsTab);

		CommitsView commitsView = new CommitsView(eccoService);
		commitsTab.setContent(commitsView);

		// associations
		Tab associationsTab = new Tab();
		associationsTab.setText("Associations");
		associationsTab.setClosable(false);
		tabPane.getTabs().add(associationsTab);

		AssociationsView associationsView = new AssociationsView(eccoService);
		associationsTab.setContent(associationsView);

		// artifacts
		Tab artifactTab = new Tab();
		artifactTab.setText("Artifacts");
		artifactTab.setClosable(false);
		tabPane.getTabs().add(artifactTab);

		ArtifactsView artifactsView = new ArtifactsView(eccoService);
		artifactTab.setContent(artifactsView);


		// GRAPHS

		// artifacts graph
		Tab artifactsGraphTab = new Tab();
		artifactsGraphTab.setText("Artifact Graph");
		artifactsGraphTab.setClosable(false);
		tabPane.getTabs().add(artifactsGraphTab);

		ArtifactGraphView artifactsGraphView = new ArtifactGraphView(eccoService);
		artifactsGraphTab.setContent(artifactsGraphView);

		// dependency graph
		Tab dependencyGraphTab = new Tab();
		dependencyGraphTab.setText("Dependency Graph");
		dependencyGraphTab.setClosable(false);
		tabPane.getTabs().add(dependencyGraphTab);

		DependencyGraphView dependencyGraphView = new DependencyGraphView(eccoService);
		dependencyGraphTab.setContent(dependencyGraphView);

		// commits graph
		Tab commitGraphTab = new Tab();
		commitGraphTab.setText("Commit Graph");
		commitGraphTab.setClosable(false);
		tabPane.getTabs().add(commitGraphTab);

		CommitGraphView commitGraphView = new CommitGraphView(eccoService);
		commitGraphTab.setContent(commitGraphView);

		// charts
		Tab chartsTab = new Tab();
		chartsTab.setText("Charts");
		chartsTab.setClosable(false);
		tabPane.getTabs().add(chartsTab);

		ChartsView chartsView = new ChartsView(eccoService);
		chartsTab.setContent(chartsView);

		// presence table
		Tab persenceTableTab = new Tab();
		persenceTableTab.setText("Presence Table");
		persenceTableTab.setClosable(false);
		tabPane.getTabs().add(persenceTableTab);

		PresenceTableView presenceTableView = new PresenceTableView(eccoService);
		persenceTableTab.setContent(presenceTableView);


		this.eccoService.addListener(this);


		this.updateView();
	}


	private void openDialog(String title, Parent content) {
		final Stage dialog = new Stage();
		dialog.initStyle(StageStyle.UTILITY);
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(MainView.this.getScene().getWindow());

		Scene dialogScene = new Scene(content);
		dialog.setScene(dialogScene);
		dialog.setTitle(title);

//		dialog.setMinWidth(400);
//		dialog.setMinHeight(200);

		dialog.show();
		dialog.requestFocus();
	}


	private void updateView() {
		if (this.eccoService.isInitialized()) {
			openButton.setDisable(true);
			closeButton.setDisable(false);
			initButton.setDisable(true);
			forkButton.setDisable(true);
			commitButton.setDisable(false);
			checkoutButton.setDisable(false);
			fetchButton.setDisable(false);
			pullButton.setDisable(false);
			pushButton.setDisable(false);
			serverButton.setDisable(false);
		} else {
			openButton.setDisable(false);
			closeButton.setDisable(true);
			initButton.setDisable(false);
			forkButton.setDisable(false);
			commitButton.setDisable(true);
			checkoutButton.setDisable(true);
			fetchButton.setDisable(true);
			pullButton.setDisable(true);
			pushButton.setDisable(true);
			serverButton.setDisable(true);
		}
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(() -> {
			this.updateView();
		});
	}

}
