package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class FeatureDetailView extends BorderPane {

	private EccoService service;

	private Feature currentFeature;

	final ObservableList<FeatureVersionInfo> featureVersionsData = FXCollections.observableArrayList();

	private TextField featureName;
	private TextArea featureDescription;
	private SplitPane splitPane;
	private ToolBar toolBar;


	public FeatureDetailView(EccoService service) {
		this.service = service;

		this.currentFeature = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button saveButton = new Button("Save");
		toolBar.getItems().add(saveButton);


		// splitpane
		this.splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);


		// feature details
		GridPane featureDetails = new GridPane();
		featureDetails.setHgap(10);
		featureDetails.setVgap(10);
		featureDetails.setPadding(new Insets(10, 10, 10, 10));

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

		splitPane.getItems().add(featureDetails);


		saveButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task saveTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						if (FeatureDetailView.this.currentFeature != null) {
							FeatureDetailView.this.currentFeature.setDescription(FeatureDetailView.this.featureDescription.getText());
							// TODO: implement saving/updating features
							//FeatureDetailView.this.service.saveFeature(feature);
						}
						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(saveTask).start();
			}
		});


		// list of feature versions
		TableView<FeatureVersionInfo> versionsTable = new TableView<FeatureVersionInfo>();
		versionsTable.setEditable(false);
		versionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FeatureVersionInfo, String> versionCol = new TableColumn<FeatureVersionInfo, String>("Version");
		TableColumn<FeatureVersionInfo, String> featureVersionsCol = new TableColumn<FeatureVersionInfo, String>("Feature Versions");

		featureVersionsCol.getColumns().setAll(versionCol);
		versionsTable.getColumns().setAll(featureVersionsCol);

		versionCol.setCellValueFactory(new PropertyValueFactory<FeatureVersionInfo, String>("version"));

		versionsTable.setItems(this.featureVersionsData);

		splitPane.getItems().add(versionsTable);


		// show nothing initially
		this.showFeature(null);
	}


	public void showFeature(Feature feature) {
		this.currentFeature = feature;

		this.featureVersionsData.clear();

		if (feature != null) {
			this.setCenter(this.splitPane);
			this.toolBar.setDisable(false);

			// show feature details
			this.featureName.setText(feature.getName());
			this.featureDescription.setText(feature.getDescription());

			// show feature versions
			for (FeatureVersion featureVersion : feature.getVersions()) {
				FeatureDetailView.this.featureVersionsData.add(new FeatureVersionInfo(featureVersion.getVersion()));
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.featureName.setText("");
			this.featureDescription.setText("");
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
