package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.gui.view.detail.FeatureDetailView;
import at.jku.isse.ecco.listener.RepositoryListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.Collection;

public class FeaturesView extends BorderPane implements RepositoryListener {

	private EccoService service;

	final ObservableList<FeatureInfo> featuresData = FXCollections.observableArrayList();


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

				Task featuresRefreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Feature> featureVersions = FeaturesView.this.service.getFeatures();
						Platform.runLater(() -> {
							for (Feature feature : featureVersions) {
								FeaturesView.this.featuresData.add(new FeatureInfo(feature));
							}
						});
						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(featuresRefreshTask).start();
			}
		});

		toolBar.getItems().add(refreshButton);


		TextField searchField = new TextField();
		Button searchButton = new Button("Search");
		toolBar.getItems().addAll(searchField, searchButton);

		searchButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				FeaturesView.this.featuresData.clear();

				Task commitTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Feature> featureVersions = FeaturesView.this.service.getFeatures();
						Platform.runLater(() -> {
							for (Feature feature : featureVersions) {
								FeaturesView.this.featuresData.add(new FeatureInfo(feature));
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


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		FilteredList<FeatureInfo> filteredData = new FilteredList<>(this.featuresData, p -> true);
		filteredData.setPredicate(featureInfo -> {
			if (featureInfo.getFeature().getName().contains(searchField.getText()) || featureInfo.getFeature().getDescription().contains(searchField.getText())) {
				return true;
			} else {
				return false;
			}
		});

		// list of features
		TableView<FeatureInfo> featuresTable = new TableView<>();
		featuresTable.setEditable(false);
		featuresTable.setTableMenuButtonVisible(true);
		featuresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FeatureInfo, String> featureNameCol = new TableColumn<>("Name");
		TableColumn<FeatureInfo, String> featureDescriptionCol = new TableColumn<>("Description");
		TableColumn<FeatureInfo, String> featuresCol = new TableColumn<>("Features");

		featuresCol.getColumns().addAll(featureNameCol, featureDescriptionCol);
		featuresTable.getColumns().setAll(featuresCol);

		featureNameCol.setCellValueFactory((TableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getFeature().getName()));
		featureDescriptionCol.setCellValueFactory((TableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getFeature().getDescription()));


		SortedList<FeatureInfo> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(featuresTable.comparatorProperty());

		featuresTable.setItems(sortedData);


		// feature details view
		FeatureDetailView featureDetailView = new FeatureDetailView(service);


		featuresTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				featureDetailView.showFeature(newValue.getFeature());
			} else {
				featureDetailView.showFeature(null);
			}
		});


		// add to split pane
		splitPane.getItems().addAll(featuresTable, featureDetailView);


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
				this.featuresData.clear();
				for (Feature feature : features) {
					this.featuresData.add(new FeatureInfo(feature));
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
		private Feature feature;

		private FeatureInfo(Feature feature) {
			this.feature = feature;
		}

		public Feature getFeature() {
			return this.feature;
		}
	}

}
