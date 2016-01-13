package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.nio.file.Paths;

public class EccoGui extends Application {

	@Override
	public void start(Stage primaryStage) {
		// ECCO Service
		EccoService eccoService = new EccoService(Paths.get(".").toAbsolutePath()); // create ecco service
		eccoService.detectRepository(Paths.get("")); // detect any existing repository


		// INIT
		Application.setUserAgentStylesheet(STYLESHEET_MODENA);
		primaryStage.setTitle("ECCO");
		Group root = new Group();
		Scene scene = new Scene(root, 800, 600);
		scene.getStylesheets().add("ecco.css");


		// TOP LEVEL
		BorderPane borderPane = new BorderPane();
		// bind to take available space
		borderPane.prefHeightProperty().bind(scene.heightProperty());
		borderPane.prefWidthProperty().bind(scene.widthProperty());
		root.getChildren().add(borderPane);

		TabPane tabPane = new TabPane();
		borderPane.setCenter(tabPane);

		// status
		Tab statusTab = new Tab();
		statusTab.setText("Status");
		statusTab.setClosable(false);
		tabPane.getTabs().add(statusTab);

		StatusView statusView = new StatusView(eccoService);
		statusTab.setContent(statusView);

		// operations
		Tab operationsTab = new Tab();
		operationsTab.setText("Operations");
		operationsTab.setClosable(false);
		tabPane.getTabs().add(operationsTab);

		OperationsView operationsView = new OperationsView(eccoService);
		operationsTab.setContent(operationsView);

		// features
		Tab featuresTab = new Tab();
		featuresTab.setText("Features");
		featuresTab.setClosable(false);
		tabPane.getTabs().add(featuresTab);

		FeaturesView featuresView = new FeaturesView(eccoService);
		featuresTab.setContent(featuresView);

		// commit
		Tab commitsTab = new Tab();
		commitsTab.setText("Commits");
		commitsTab.setClosable(false);
		tabPane.getTabs().add(commitsTab);

		CommitsView commitsView = new CommitsView(eccoService);
		commitsTab.setContent(commitsView);

		// commits graph
		Tab commitGraphTab = new Tab();
		commitGraphTab.setText("Commit Graph");
		commitGraphTab.setClosable(false);
		tabPane.getTabs().add(commitGraphTab);

		CommitGraphView commitGraphView = new CommitGraphView(eccoService);
		commitGraphTab.setContent(commitGraphView);

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

		// artifacts graph
		Tab artifactsGraphTab = new Tab();
		artifactsGraphTab.setText("Artifacts Graph");
		artifactsGraphTab.setClosable(false);
		tabPane.getTabs().add(artifactsGraphTab);

		ArtifactsGraphView artifactsGraphView = new ArtifactsGraphView(eccoService);
		artifactsGraphTab.setContent(artifactsGraphView);

		// charts
		Tab chartsTab = new Tab();
		chartsTab.setText("Charts");
		chartsTab.setClosable(false);
		tabPane.getTabs().add(chartsTab);

		ChartsView chartsView = new ChartsView(eccoService);
		chartsTab.setContent(chartsView);


		primaryStage.setScene(scene);
		primaryStage.show();
	}

	public static void main(String[] args) {
		Application.launch(args);
	}

}
