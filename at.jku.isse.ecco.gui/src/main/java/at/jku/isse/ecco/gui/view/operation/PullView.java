package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class PullView extends OperationView {

	private EccoService service;


	private Button pullButton;
	private ComboBox<Remote> remoteComboBox;


	public PullView(EccoService service) {
		super();
		this.service = service;

		this.pullButton = new Button("Pull |");
		this.remoteComboBox = new ComboBox<>();

		this.pullButton.setOnAction(event1 -> {
			try {
				Remote remote = this.remoteComboBox.getValue();

				this.service.fetch(remote.getName());
				this.service.pull(remote.getName());
				this.stepSuccess("Pull operation was successful.");
			} catch (Exception e) {
				this.stepError("Error during pull operation.", e);
			}
		});

		this.step1();
	}


	private void step1() {
		// toolbar top
		ToolBar toolBar = new ToolBar();

		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event1 -> {
			((Stage) this.getScene().getWindow()).close();
		});

		Label headerLabel = new Label("Remote");

		Button selectButton = new Button("Select >");
		selectButton.setOnAction(event1 -> {
			this.stepSelect();
		});

		toolBar.getItems().setAll(cancelButton, spacerLeft, headerLabel, spacerRight, selectButton, pullButton);

		this.setTop(toolBar);


		// main content
		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setMinWidth(GridPane.USE_PREF_SIZE);
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.setCenter(gridPane);

		int row = 0;

		Label remoteLabel = new Label("Remote: ");
		gridPane.add(remoteLabel, 0, row, 1, 1);

		remoteLabel.setLabelFor(remoteComboBox);
		gridPane.add(remoteComboBox, 1, row, 1, 1);

		row++;


		this.remoteComboBox.getItems().add(null);
		for (Remote remote : this.service.getRemotes()) {
			this.remoteComboBox.getItems().add(remote);
		}


		selectButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());
		pullButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());


		this.fit();
	}


	private void stepSelect() {
		// toolbar top
		ToolBar toolBar = new ToolBar();

		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		Button remoteButton = new Button("< Remote");
		remoteButton.setOnAction(event1 -> {
			this.step1();
		});

		Label headerLabel = new Label("Selection");

		toolBar.getItems().setAll(remoteButton, spacerLeft, headerLabel, spacerRight, pullButton);

		this.setTop(toolBar);


		// main content
//		TreeTableView<FeatureInfo> featureSelectionTreeTable = new TreeTableView<>();
//		featureSelectionTreeTable.setEditable(true);
//		featureSelectionTreeTable.setTableMenuButtonVisible(true);
//		featureSelectionTreeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
//		featureSelectionTreeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
//		//featureSelectionTreeTable.setRowFactory(item -> new CheckBoxTreeTableRow<>());
//
//		// create columns
//		TreeTableColumn<FeatureInfo, String> idCol = new TreeTableColumn<>("Identifier");
//		idCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper("aa"));
//
//		TreeTableColumn<FeatureInfo, String> nameCol = new TreeTableColumn<>("Name");
//		nameCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper("bb"));
//
//		TreeTableColumn<FeatureInfo, Boolean> isSelectedCol = new TreeTableColumn<>("Selected");
//		isSelectedCol.setCellValueFactory(
//				(TreeTableColumn.CellDataFeatures<FeatureInfo, Boolean> param) ->
//				{
////					FeatureInfo featureInfo = param.getValue().getValue();
//////					if (featureInfo.getFeatureVersion() != null) {
////					return featureInfo.isSelectedProperty();
//////					} else {
//////						for (FeatureInfo child : param.)
//////					}
////					return new ReadOnlyBooleanWrapper(false);
//				}
//		);
//		isSelectedCol.setCellFactory(CheckBoxTreeTableCell.forTreeTableColumn(isSelectedCol));
//		isSelectedCol.setEditable(true);
//
//
//		TreeTableColumn<FeatureInfo, String> featuresCol = new TreeTableColumn<>("Features");
//		featuresCol.getColumns().setAll(idCol, nameCol, isSelectedCol);
//
//		featureSelectionTreeTable.getColumns().setAll(featuresCol);
//
//
//		this.setCenter(featureSelectionTreeTable);
//
//
//		CheckBoxTreeItem<FeatureInfo> rootTreeItem = new CheckBoxTreeItem<FeatureInfo>();
//		//for (Feature feature : this.service.getRemote(EccoService.ORIGIN_REMOTE_NAME).getFeatures()) {
//		for (Feature feature : this.service.getRepository().getFeatures()) {
//			TreeItem<FeatureInfo> featureTreeItem = new TreeItem<>();
//			featureTreeItem.setValue(new FeatureInfo(feature, null));
//			for (FeatureVersion featureVersion : feature.getVersions()) {
//				TreeItem<FeatureInfo> featureVersionTreeItem = new TreeItem<FeatureInfo>();
//				featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
//				featureTreeItem.getChildren().add(featureVersionTreeItem);
//			}
//			rootTreeItem.getChildren().add(featureTreeItem);
//		}
//		featureSelectionTreeTable.setRoot(rootTreeItem);


		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setMinWidth(GridPane.USE_PREF_SIZE);
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		RowConstraints row1constraint = new RowConstraints();
		RowConstraints row2constraint = new RowConstraints();
		row2constraint.setVgrow(Priority.ALWAYS);
		row2constraint.setFillHeight(true);
		gridPane.getRowConstraints().addAll(row1constraint, row2constraint);

		this.setCenter(gridPane);

		int row = 0;


		gridPane.setStyle("-fx-border-color: red;");


		Label selectionLabel = new Label("Deselection: ");
		gridPane.add(selectionLabel, 0, row, 1, 1);
		TextField selectionTextField = new TextField();
		selectionTextField.setDisable(false);
		selectionLabel.setLabelFor(selectionTextField);
		gridPane.add(selectionTextField, 1, row, 1, 1);
		row++;


		TreeView<FeatureInfo> featureSelectionTreeView = new TreeView<>();
		featureSelectionTreeView.setCellFactory(CheckBoxTreeCell.<FeatureInfo>forTreeView());
		featureSelectionTreeView.setShowRoot(true);
		featureSelectionTreeView.setEditable(false);
		//this.setCenter(featureSelectionTreeView);

		TitledPane titledPane = new TitledPane();
		titledPane.setText("Features");
		titledPane.setCollapsible(false);
		titledPane.setContent(featureSelectionTreeView);
		titledPane.setMaxWidth(Double.MAX_VALUE);
		titledPane.setMaxHeight(Double.MAX_VALUE);
		gridPane.add(titledPane, 0, row, 2, 1);
		row++;


		Collection<CheckBoxTreeItem<FeatureInfo>> deselectedFeatureVersionTreeItems = new ArrayList<>();
		CheckBoxTreeItem<FeatureInfo> rootTreeItem = new CheckBoxTreeItem<FeatureInfo>();
		//for (Feature feature : this.service.getRemote(EccoService.ORIGIN_REMOTE_NAME).getFeatures()) {
		for (Feature feature : this.service.getRepository().getFeatures()) {
			CheckBoxTreeItem<FeatureInfo> featureTreeItem = new CheckBoxTreeItem<>();
			featureTreeItem.setValue(new FeatureInfo(feature, null));
			for (FeatureVersion featureVersion : feature.getVersions()) {
				CheckBoxTreeItem<FeatureInfo> featureVersionTreeItem = new CheckBoxTreeItem<>();
				featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
				//featureVersionTreeItem.setSelected(true);
				featureTreeItem.getChildren().add(featureVersionTreeItem);


				featureVersionTreeItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
					if (!newValue) {
						deselectedFeatureVersionTreeItems.add(featureVersionTreeItem);
					} else {
						deselectedFeatureVersionTreeItems.remove(featureVersionTreeItem);
					}

					String deselectionString = deselectedFeatureVersionTreeItems.stream().map(featureInfoCheckBoxTreeItem -> {
						return featureInfoCheckBoxTreeItem.getValue().getFeatureVersion().toString();
					}).collect(Collectors.joining(", "));
					selectionTextField.setText(deselectionString);

//					StringBuilder sb = new StringBuilder();
//					for (CheckBoxTreeItem<FeatureInfo> featureInfoCheckBoxTreeItem : deselectedFeatureVersionTreeItems) {
//						if (!featureInfoCheckBoxTreeItem.isSelected()) {
//							sb.append(featureInfoCheckBoxTreeItem.getValue().getFeatureVersion().toString());
//						}
//					}
//					selectionTextField.setText(sb.toString());
				});
			}
			rootTreeItem.getChildren().add(featureTreeItem);
		}
		rootTreeItem.setSelected(true);
		featureSelectionTreeView.setRoot(rootTreeItem);
		rootTreeItem.setExpanded(true);


		this.fit();
	}


	public static class FeatureInfo {
		private Feature feature;
		private FeatureVersion featureVersion;
		private SimpleBooleanProperty sbp;

		private FeatureInfo(Feature feature, FeatureVersion featureVersion) {
			this.feature = feature;
			this.featureVersion = featureVersion;
			this.sbp = new SimpleBooleanProperty(true);
		}

		public Feature getFeature() {
			return this.feature;
		}

		public FeatureVersion getFeatureVersion() {
			return this.featureVersion;
		}

		public SimpleBooleanProperty isSelectedProperty() {
			return this.sbp;
		}

		@Override
		public String toString() {
			if (this.feature == null && this.featureVersion == null) {
				return "FEATURE";
			} else if (this.feature != null && this.featureVersion == null)
				return this.feature.getId() + " - " + this.feature.getName();
			else if (this.featureVersion != null) {
				return this.featureVersion.getId();
			}
			return "";
		}
	}


}
