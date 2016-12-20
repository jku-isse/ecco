package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class RemoteDetailView extends BorderPane {

	private EccoService service;

	private Remote currentRemote;

	final ObservableList<Feature> featureVersionsData = FXCollections.observableArrayList();

	private TextField remoteName;
	private TextField remoteAddress;
	private SplitPane splitPane;
	private ToolBar toolBar;
	private ComboBox<Remote.Type> remoteType;
	private TreeView<FeatureInfo> featureSelectionTreeView;


	public RemoteDetailView(EccoService service) {
		this.service = service;

		this.currentRemote = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button saveButton = new Button("Save");
		Button fetchButton = new Button("Fetch");
		toolBar.getItems().setAll(saveButton, new Separator(), fetchButton, new Separator());


		// splitpane
		this.splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);


		// remote details
		GridPane remoteDetails = new GridPane();
		remoteDetails.setHgap(10);
		remoteDetails.setVgap(10);
		remoteDetails.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		remoteDetails.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.remoteName = new TextField();
		this.remoteAddress = new TextField();
		this.remoteType = new ComboBox<>();
		this.remoteType.getItems().setAll(Remote.Type.LOCAL, Remote.Type.REMOTE);

		int row = 0;
		remoteDetails.add(new Label("Name: "), 1, row, 1, 1);
		row++;
		remoteDetails.add(this.remoteName, 1, row, 1, 1);
		row++;
		remoteDetails.add(new Label("Address: "), 1, row, 1, 1);
		row++;
		remoteDetails.add(this.remoteAddress, 1, row, 1, 1);
		row++;
		remoteDetails.add(new Label("Type: "), 1, row, 1, 1);
		row++;
		remoteDetails.add(this.remoteType, 1, row, 1, 1);
		row++;


		splitPane.getItems().add(remoteDetails);


		// list of feature versions

		featureSelectionTreeView = new TreeView<>();
		featureSelectionTreeView.setShowRoot(true);
		featureSelectionTreeView.setEditable(false);
		splitPane.getItems().add(featureSelectionTreeView);


		// show nothing initially
		this.showRemote(null);
	}

	public void showRemote(Remote remote) {
		this.currentRemote = remote;

		if (remote != null) {
			this.setCenter(this.splitPane);
			this.toolBar.setDisable(false);

			// show remote details
			this.remoteName.setText(remote.getName());
			this.remoteAddress.setText(remote.getAddress());
			this.remoteType.getSelectionModel().select(remote.getType());

			// show feature versions
			TreeItem<FeatureInfo> rootTreeItem = new TreeItem<>();
			for (Feature feature : this.service.getRemote(EccoService.ORIGIN_REMOTE_NAME).getFeatures()) {
				TreeItem<FeatureInfo> featureTreeItem = new TreeItem<>();
				featureTreeItem.setValue(new FeatureInfo(feature, null));
				for (FeatureVersion featureVersion : feature.getVersions()) {
					TreeItem<FeatureInfo> featureVersionTreeItem = new TreeItem<>();
					featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
					featureTreeItem.getChildren().add(featureVersionTreeItem);
				}
				rootTreeItem.getChildren().add(featureTreeItem);
			}
			featureSelectionTreeView.setRoot(rootTreeItem);
			rootTreeItem.setExpanded(true);
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.remoteName.setText("");
			this.remoteAddress.setText("");
			this.remoteType.getSelectionModel().select(0);
			this.featureSelectionTreeView.setRoot(null);
		}
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
