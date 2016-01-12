package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.Collection;

public class FeaturesView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<FeatureInfo> featuresData = FXCollections.observableArrayList();
	final ObservableList<FeatureVersionInfo> featureVersionsData = FXCollections.observableArrayList();


	public FeaturesView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");

		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				FeaturesView.this.featuresData.clear();
				FeaturesView.this.featureVersionsData.clear();

				Task commitTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Feature> featureVersions = FeaturesView.this.service.getFeatures();
						Platform.runLater(() -> {
							for (Feature feature : featureVersions) {
								FeaturesView.this.featuresData.add(new FeatureInfo(feature.getName(), feature.getDescription(), feature.getVersions()));
							}
						});
						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(commitTask).start();
			}
		});

		toolBar.getItems().add(refreshButton);


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of commits
		TableView<FeatureInfo> featuresTable = new TableView<FeatureInfo>();
		featuresTable.setEditable(false);
		featuresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FeatureInfo, String> featureNameCol = new TableColumn<FeatureInfo, String>("Name");
		TableColumn<FeatureInfo, String> featureDescriptionCol = new TableColumn<FeatureInfo, String>("Description");
		TableColumn<FeatureInfo, String> featuresCol = new TableColumn<FeatureInfo, String>("Features");

		featuresCol.getColumns().addAll(featureNameCol, featureDescriptionCol);
		featuresTable.getColumns().setAll(featuresCol);

		featureNameCol.setCellValueFactory(new PropertyValueFactory<FeatureInfo, String>("name"));
		featureDescriptionCol.setCellValueFactory(new PropertyValueFactory<FeatureInfo, String>("description"));

		featuresTable.setItems(this.featuresData);

//		TitledPane commitsTitledPane = new TitledPane("Commits", commitsTable);
//		commitsTitledPane.setAnimated(false);
//		commitsTitledPane.setCollapsible(false);

		featuresTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			//Check whether item is selected and set value of selected item to Label
			if (newValue != null) {

				FeaturesView.this.featureVersionsData.clear();
				for (FeatureVersion featureVersion : newValue.getFeatureVersions()) {
					FeaturesView.this.featureVersionsData.add(new FeatureVersionInfo(featureVersion.getVersion()));
				}
			}
		});


		// list of associations
		TableView<FeatureVersionInfo> versionsTable = new TableView<FeatureVersionInfo>();
		versionsTable.setEditable(false);
		versionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FeatureVersionInfo, String> versionCol = new TableColumn<FeatureVersionInfo, String>("Version");
		TableColumn<FeatureVersionInfo, String> featureVersionsCol = new TableColumn<FeatureVersionInfo, String>("Feature Versions");

		featureVersionsCol.getColumns().setAll(versionCol);
		versionsTable.getColumns().setAll(featureVersionsCol);

		versionCol.setCellValueFactory(new PropertyValueFactory<FeatureVersionInfo, String>("version"));

		versionsTable.setItems(this.featureVersionsData);

//		TitledPane associationsTitledPane = new TitledPane("Associations", associationsTable);
//		associationsTitledPane.setAnimated(false);
//		associationsTitledPane.setCollapsible(false);


		// add to split pane
		splitPane.getItems().addAll(featuresTable, versionsTable);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				this.setDisable(false);
			});
			Collection<Feature> features = service.getFeatures();
			Platform.runLater(() -> {
				for (Feature feature : features) {
					this.featuresData.add(new FeatureInfo(feature.getName(), feature.getDescription(), feature.getVersions()));
				}
			});
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {

	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}


	public static class FeatureInfo {
		private final SimpleStringProperty name;
		private final SimpleStringProperty description;

		Collection<FeatureVersion> featureVersions;

		public Collection<FeatureVersion> getFeatureVersions() {
			return this.featureVersions;
		}

		private FeatureInfo(String name, String description, Collection<FeatureVersion> featureVersions) {
			this.name = new SimpleStringProperty(name);
			this.description = new SimpleStringProperty(description);
			this.featureVersions = featureVersions;
		}

		public String getName() {
			return this.name.get();
		}

		public void setName(String name) {
			this.name.set(name);
		}

		public String getDescription() {
			return this.description.get();
		}

		public void setDescription(String description) {
			this.description.set(description);
		}
	}

	public static class FeatureVersionInfo {
		private final SimpleIntegerProperty version;

		private FeatureVersionInfo(int version) {
			this.version = new SimpleIntegerProperty(version);
		}

		public int getVersion() {
			return this.version.get();
		}

		public void setVersion(int version) {
			this.version.set(version);
		}
	}

}
