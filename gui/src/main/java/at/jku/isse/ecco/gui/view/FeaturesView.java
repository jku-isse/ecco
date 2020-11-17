package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.gui.view.detail.FeatureDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
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

import java.util.Collection;

public class FeaturesView extends BorderPane implements EccoListener {

	private final EccoService service;

	private final ObservableList<Feature> featuresData = FXCollections.observableArrayList();


	public FeaturesView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);
		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				FeaturesView.this.featuresData.clear();

				Task featuresRefreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<? extends Feature> featureRevisions = FeaturesView.this.service.getRepository().getFeatures();
						Platform.runLater(() -> {
							for (Feature feature : featureRevisions) {
								FeaturesView.this.featuresData.add(feature);
							}
						});
						Platform.runLater(() -> toolBar.setDisable(false));
						return null;
					}
				};

				new Thread(featuresRefreshTask).start();
			}
		});

		toolBar.getItems().add(new Separator());

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
						Collection<? extends Feature> featureRevisions = FeaturesView.this.service.getRepository().getFeatures();
						Platform.runLater(() -> {
							for (Feature feature : featureRevisions) {
								FeaturesView.this.featuresData.add(feature);
							}
						});
						Platform.runLater(() -> toolBar.setDisable(false));
						return null;
					}
				};

				new Thread(commitTask).start();
			}
		});

		toolBar.getItems().add(new Separator());


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		FilteredList<Feature> filteredData = new FilteredList<>(this.featuresData, p -> true);
		filteredData.setPredicate(feature -> feature.getName().contains(searchField.getText()) || feature.getDescription().contains(searchField.getText()));

		// list of features
		TableView<Feature> featuresTable = new TableView<>();
		featuresTable.setEditable(false);
		featuresTable.setTableMenuButtonVisible(true);
		featuresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<Feature, String> featureNameCol = new TableColumn<>("Name");
		TableColumn<Feature, String> featureDescriptionCol = new TableColumn<>("Description");
		TableColumn<Feature, String> featuresCol = new TableColumn<>("Features");

		featuresCol.getColumns().addAll(featureNameCol, featureDescriptionCol);
		featuresTable.getColumns().setAll(featuresCol);

		featureNameCol.setCellValueFactory((TableColumn.CellDataFeatures<Feature, String> param) -> new ReadOnlyStringWrapper(param.getValue().getName()));
		featureDescriptionCol.setCellValueFactory((TableColumn.CellDataFeatures<Feature, String> param) -> new ReadOnlyStringWrapper(param.getValue().getDescription()));


		SortedList<Feature> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(featuresTable.comparatorProperty());

		featuresTable.setItems(sortedData);


		// feature details view
		FeatureDetailView featureDetailView = new FeatureDetailView(service);


		featuresTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				featureDetailView.showFeature(newValue);
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
			Platform.runLater(() -> this.setDisable(false));
			Collection<? extends Feature> features = service.getRepository().getFeatures();
			Platform.runLater(() -> {
				this.featuresData.clear();
				for (Feature feature : features) {
					this.featuresData.add(feature);
				}
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}

}
