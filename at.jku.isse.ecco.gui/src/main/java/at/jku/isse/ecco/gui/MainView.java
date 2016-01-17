package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

public class MainView extends BorderPane {

	public MainView(EccoService eccoService) {
		TabPane tabPane = new TabPane();
		this.setCenter(tabPane);

		// status
		Tab statusTab = new Tab();
		statusTab.setText("Status");
		statusTab.setClosable(false);
		tabPane.getTabs().add(statusTab);

		SettingsView statusView = new SettingsView(eccoService);
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
	}

}
