package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class FeatureDetailView extends BorderPane {

	private EccoService service;

	private Feature currentFeature;

	final ObservableList<FeatureVersion> featureVersionsData = FXCollections.observableArrayList();

	private TextField featureName;
	private TextArea featureDescription;
	private Pane centerPane;
	private ToolBar toolBar;


	public FeatureDetailView(EccoService service) {
		this.service = service;

		this.currentFeature = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button saveButton = new Button("Save");
		toolBar.getItems().setAll(saveButton, new Separator());


		// feature details
		GridPane featureDetails = new GridPane();
		this.centerPane = featureDetails;
		featureDetails.setHgap(10);
		featureDetails.setVgap(10);
		featureDetails.setPadding(new Insets(10, 10, 10, 10));
		this.setCenter(this.centerPane);

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		featureDetails.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.featureName = new TextField();
		this.featureName.setEditable(false);
		this.featureDescription = new TextArea();

		int row = 0;
		featureDetails.add(new Label("Name: "), 1, row, 1, 1);
		row++;
		featureDetails.add(this.featureName, 1, row, 1, 1);
		row++;
		featureDetails.add(new Label("Description: "), 1, row, 1, 1);
		row++;
		featureDetails.add(this.featureDescription, 1, row, 1, 1);
		row++;


		saveButton.setOnAction(e -> {
			toolBar.setDisable(true);

			Task saveTask = new Task<Void>() {
				@Override
				public Void call() throws EccoException {
					if (FeatureDetailView.this.currentFeature != null) {
						FeatureDetailView.this.currentFeature.setDescription(FeatureDetailView.this.featureDescription.getText());
						// TODO: implement saving/updating features
					}
					Platform.runLater(() -> toolBar.setDisable(false));
					return null;
				}
			};

			new Thread(saveTask).start();
		});


		// list of feature versions
		TableView<FeatureVersion> versionsTable = new TableView<>();
		versionsTable.setEditable(false);
		versionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FeatureVersion, String> versionCol = new TableColumn<>("Version");
		TableColumn<FeatureVersion, String> featureVersionsCol = new TableColumn<>("Feature Versions");

		featureVersionsCol.getColumns().setAll(versionCol);
		versionsTable.getColumns().setAll(featureVersionsCol);

		versionCol.setCellValueFactory(new PropertyValueFactory<>("id"));

		versionsTable.setItems(this.featureVersionsData);

		featureDetails.add(versionsTable, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showFeature(null);
	}


	public void showFeature(Feature feature) {
		this.currentFeature = feature;

		this.featureVersionsData.clear();

		if (feature != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			// show feature details
			this.featureName.setText(feature.getName());
			this.featureDescription.setText(feature.getDescription());

			// show feature versions
			for (FeatureVersion featureVersion : feature.getVersions()) {
				FeatureDetailView.this.featureVersionsData.add(featureVersion);
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.featureName.setText("");
			this.featureDescription.setText("");
		}
	}

}
