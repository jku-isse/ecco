package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

// TODO: this view is currently not in use
public class ForkView extends OperationView {

	private EccoService service;


	private Button forkButton;
	private TextField repositoryDirTextField;
	private TextField remoteAddressTextField;


	public ForkView(EccoService service) {
		this.service = service;

		this.forkButton = new Button("Fork |");
		this.repositoryDirTextField = new TextField(service.getRepositoryDir().toString());
		this.remoteAddressTextField = new TextField();

		this.forkButton.setOnAction(event1 -> {
			try {
				Path repositoryDir = Paths.get(repositoryDirTextField.getText());
				this.service.setRepositoryDir(repositoryDir);
				this.service.setBaseDir(repositoryDir.getParent());
				this.service.init();

				String remoteAddress = remoteAddressTextField.getText();
				Path path;
				try {
					path = Paths.get(remoteAddress);
				} catch (InvalidPathException | NullPointerException ex) {
					path = null;
				}

				if (path != null) {
					this.service.addRemote(EccoService.ORIGIN_REMOTE_NAME, remoteAddress, Remote.Type.LOCAL);
				} else if (remoteAddress.matches("[a-zA-Z]+:[0-9]+")) {
					this.service.addRemote(EccoService.ORIGIN_REMOTE_NAME, remoteAddress, Remote.Type.REMOTE);
				} else {
					throw new EccoException("ERROR: Invalid remote address provided."); // TODO: disable fork button if this is not the case?
				}


				this.service.fetch(EccoService.ORIGIN_REMOTE_NAME);
				this.service.pull(EccoService.ORIGIN_REMOTE_NAME);
				this.stepSuccess("Repository was sucessfully forked.");
			} catch (Exception e) {
				this.stepError("Error forking repository.", e);
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

		toolBar.getItems().setAll(cancelButton, spacerLeft, headerLabel, spacerRight, selectButton, forkButton);

		this.setTop(toolBar);


		// toolbar bottom
		ToolBar toolBarBottom = new ToolBar();
		this.setBottom(toolBarBottom);


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

		Label repositoryDirLabel = new Label("Repository Directory: ");
		gridPane.add(repositoryDirLabel, 0, row, 1, 1);

		repositoryDirTextField.setDisable(false);
		repositoryDirTextField.setPrefWidth(300);
		repositoryDirLabel.setLabelFor(repositoryDirTextField);
		gridPane.add(repositoryDirTextField, 1, row, 1, 1);

		Button selectRepositoryDirectoryButton = new Button("...");
		gridPane.add(selectRepositoryDirectoryButton, 2, row, 1, 1);

		row++;

		selectRepositoryDirectoryButton.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			try {
				Path directory = Paths.get(repositoryDirTextField.getText());
				if (directory.getFileName().equals(EccoService.REPOSITORY_DIR_NAME))
					directory = directory.getParent();
				if (Files.exists(directory) && Files.isDirectory(directory))
					directoryChooser.setInitialDirectory(directory.toFile());
			} catch (Exception e) {
				// do nothing
			}
			final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory != null) {
				repositoryDirTextField.setText(selectedDirectory.toPath().resolve(EccoService.REPOSITORY_DIR_NAME).toString());
			}
		});

		Label remoteLabel = new Label("Remote Address: ");
		gridPane.add(remoteLabel, 0, row, 1, 1);

		remoteAddressTextField.setDisable(false);
		remoteAddressTextField.setPrefWidth(300);
		remoteLabel.setLabelFor(remoteAddressTextField);
		gridPane.add(remoteAddressTextField, 1, row, 2, 1);

		row++;


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

		toolBar.getItems().setAll(remoteButton, spacerLeft, headerLabel, spacerRight, forkButton);

		this.setTop(toolBar);


		// toolbar bottom
		ToolBar toolBarBottom = new ToolBar();
		this.setBottom(toolBarBottom);


		// main content
		TreeTableView<FeatureInfo> featureSelectionTreeTable = new TreeTableView<>();

		// create columns
		TreeTableColumn<FeatureInfo, String> idCol = new TreeTableColumn<>("Identifier");
		idCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().toString()));

		TreeTableColumn<FeatureInfo, String> nameCol = new TreeTableColumn<>("Name");
		nameCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<FeatureInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().toString()));

		TreeTableColumn<Node, String> associationNodeCol = new TreeTableColumn<>("Association");
		associationNodeCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<Node, String> param) ->
				{
					if (param.getValue().getValue().getArtifact() != null) {
						Association containingAssociation = param.getValue().getValue().getArtifact().getContainingNode().getContainingAssociation();
						if (containingAssociation != null)
							return new ReadOnlyStringWrapper(String.valueOf(containingAssociation.getId()));

					}
					return new ReadOnlyStringWrapper("null");
				}
		);

		TreeTableColumn<FeatureInfo, Boolean> isSelectedCol = new TreeTableColumn<>("Selected");
		isSelectedCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<FeatureInfo, Boolean> param) ->
				{
					FeatureInfo featureInfo = param.getValue().getValue();

//					if (artifact != null) {
//						SimpleBooleanProperty sbp = new SimpleBooleanProperty() {
//							@Override
//							public boolean get() {
//								return super.get();
//							}
//
//							@Override
//							public void set(boolean value) {
//								if (value)
//									artifact.putProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION, value);
//								else
//									artifact.removeProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION);
//								super.set(value);
//							}
//						};
//						sbp.set(artifact.getProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION).isPresent());
//						return sbp;
//						//return new ReadOnlyBooleanWrapper(artifact.getProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION).isPresent());
//					} else {
//						return new ReadOnlyBooleanWrapper(false);
//					}
					return new ReadOnlyBooleanWrapper(false);
				}
		);
		isSelectedCol.setCellFactory(CheckBoxTreeTableCell.forTreeTableColumn(isSelectedCol));
		isSelectedCol.setEditable(true);


		TreeTableColumn<FeatureInfo, String> featureSelectionCol = new TreeTableColumn<>("Features");
		featureSelectionCol.getColumns().setAll(idCol, nameCol, isSelectedCol);


		featureSelectionTreeTable.getColumns().setAll(featureSelectionCol);

		featureSelectionTreeTable.setEditable(true);
		featureSelectionTreeTable.setTableMenuButtonVisible(true);
		featureSelectionTreeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
		featureSelectionTreeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		this.setCenter(featureSelectionTreeTable);


		TreeItem<FeatureInfo> rootTreeItem = new TreeItem<FeatureInfo>();
		for (Feature feature : this.service.getRemote(EccoService.ORIGIN_REMOTE_NAME).getFeatures()) {
			TreeItem<FeatureInfo> featureTreeItem = new TreeItem<FeatureInfo>();
			featureTreeItem.setValue(new FeatureInfo(feature, null));
			for (FeatureRevision featureVersion : feature.getRevisions()) {
				TreeItem<FeatureInfo> featureVersionTreeItem = new TreeItem<FeatureInfo>();
				featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
				featureTreeItem.getChildren().add(featureVersionTreeItem);
			}
			rootTreeItem.getChildren().add(featureTreeItem);
		}


		this.fit();
	}


	public static class FeatureInfo {
		private Feature feature;
		private FeatureRevision featureVersion;

		private FeatureInfo(Feature feature, FeatureRevision featureVersion) {
			this.feature = feature;
			this.featureVersion = featureVersion;
		}

		public Feature getFeature() {
			return this.feature;
		}

		public FeatureRevision getFeatureVersion() {
			return this.featureVersion;
		}
	}


}
