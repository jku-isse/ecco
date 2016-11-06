package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.gui.view.*;
import at.jku.isse.ecco.gui.view.graph.ArtifactGraphView;
import at.jku.isse.ecco.gui.view.graph.CommitGraphView;
import at.jku.isse.ecco.gui.view.graph.DependencyGraphView;
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
	}

}
