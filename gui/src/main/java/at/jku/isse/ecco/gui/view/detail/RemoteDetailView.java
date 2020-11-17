package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class RemoteDetailView extends BorderPane {

	private EccoService service;

	private Remote currentRemote;

	final ObservableList<Feature> featureVersionsData = FXCollections.observableArrayList();

	private TextField remoteName;
	private TextField remoteAddress;
	private Pane centerPane;
	private ToolBar toolBar;
	private ComboBox<Remote.Type> remoteType;
	private TreeView<FeatureInfo> featuresTreeView;


	public RemoteDetailView(EccoService service) {
		this.service = service;

		this.currentRemote = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button saveButton = new Button("Save");
		Button fetchButton = new Button("Fetch");
		toolBar.getItems().setAll(saveButton, new Separator(), fetchButton, new Separator());


		// remote details
		GridPane remoteDetails = new GridPane();
		this.centerPane = remoteDetails;
		remoteDetails.setHgap(10);
		remoteDetails.setVgap(10);
		remoteDetails.setPadding(new Insets(10, 10, 10, 10));
		this.setCenter(this.centerPane);

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


		// list of feature versions

		featuresTreeView = new TreeView<>();
		featuresTreeView.setShowRoot(true);
		featuresTreeView.setEditable(false);
		remoteDetails.add(featuresTreeView, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showRemote(null);
	}

	public void showRemote(Remote remote) {
		this.currentRemote = remote;

		if (remote != null) {
			this.setCenter(this.centerPane);
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
				for (FeatureRevision featureVersion : feature.getRevisions()) {
					TreeItem<FeatureInfo> featureVersionTreeItem = new TreeItem<>();
					featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
					featureTreeItem.getChildren().add(featureVersionTreeItem);
				}
				rootTreeItem.getChildren().add(featureTreeItem);
			}
			featuresTreeView.setRoot(rootTreeItem);
			rootTreeItem.setExpanded(true);
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.remoteName.setText("");
			this.remoteAddress.setText("");
			this.remoteType.getSelectionModel().select(0);
			this.featuresTreeView.setRoot(null);
		}
	}


	public static class FeatureInfo {
		private Feature feature;
		private FeatureRevision featureVersion;
		private SimpleBooleanProperty sbp;

		private FeatureInfo(Feature feature, FeatureRevision featureVersion) {
			this.feature = feature;
			this.featureVersion = featureVersion;
			this.sbp = new SimpleBooleanProperty(true);
		}

		public Feature getFeature() {
			return this.feature;
		}

		public FeatureRevision getFeatureVersion() {
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
