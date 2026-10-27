package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.gui.TaskFailures;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.BiConsumer;

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
	private final Label statusLabel = new Label();

	private BiConsumer<String, Remote> onRemoteChanged = (name, remote) -> {
	};


	public RemoteDetailView(EccoService service) {
		this.service = service;

		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button saveButton = new Button("Save");
		saveButton.setOnAction(event -> this.save());
		Button fetchButton = new Button("Fetch");
		fetchButton.setOnAction(event -> this.fetch());
		toolBar.getItems().setAll(saveButton, new Separator(), fetchButton, new Separator(), this.statusLabel);


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

	/**
	 * Called with the name the remote had and the remote after Save or Fetch changed it, e.g. to
	 * update a list of remotes - also when another remote is shown by then.
	 */
	public void setOnRemoteChanged(BiConsumer<String, Remote> onRemoteChanged) {
		this.onRemoteChanged = onRemoteChanged;
	}

	/**
	 * Stores the edited name, address and type in the background - on the FX thread it waited for a
	 * fetch holding the service, and froze the window; a failure is shown in the toolbar and changes
	 * nothing.
	 */
	private void save() {
		String name = this.currentRemote.getName();
		String newName = this.remoteName.getText().trim();
		String address = this.remoteAddress.getText().trim();
		Remote.Type type = this.remoteType.getValue();
		this.run(new Task<>() {
			@Override
			protected Remote call() {
				return RemoteDetailView.this.service.updateRemote(name, newName, address, type);
			}
		}, name, "Saving...", "Saved.");
	}

	/** Fetches the shown remote's features in the background and shows them. */
	private void fetch() {
		String name = this.currentRemote.getName();
		this.run(new Task<>() {
			@Override
			protected Remote call() {
				RemoteDetailView.this.service.fetch(name);
				return RemoteDetailView.this.service.getRemote(name);
			}
		}, name, "Fetching...", "Fetched.");
	}

	/**
	 * Runs {@code task} on a daemon thread (it must not keep the application from quitting) and
	 * shows the remote it returns with {@code done}; {@code null} = the remote was removed meanwhile.
	 * If another remote was picked meanwhile (which enables the toolbar again), that one stays shown:
	 * the remote is only reported as changed, a failure named in the status.
	 */
	private void run(Task<Remote> task, String name, String working, String done) {
		Remote started = this.currentRemote;
		this.toolBar.setDisable(true);
		this.statusLabel.setText(working);
		task.setOnSucceeded(event -> {
			Remote remote = task.getValue();
			boolean stillShown = this.currentRemote == started;
			if (remote == null) {
				// removed while this ran: there is nothing to show or to report as changed
				if (stillShown) {
					this.showRemote(null);
					this.statusLabel.setText("The remote no longer exists.");
				}
				return;
			}
			// the list's update reselects the remote, which shows it again and cleared the status:
			// report first, then show it with its status
			this.onRemoteChanged.accept(name, remote);
			if (stillShown) {
				this.showRemote(remote);
				this.statusLabel.setText(done);
			}
		});
		task.setOnFailed(event -> {
			if (this.currentRemote == started) {
				this.toolBar.setDisable(false);
				this.statusLabel.setText(TaskFailures.reason(task.getException()));
			} else
				this.statusLabel.setText("'" + name + "': " + TaskFailures.reason(task.getException()));
		});
		Thread thread = new Thread(task, "remote " + working);
		thread.setDaemon(true);
		thread.start();
	}

	public final void showRemote(Remote remote) {
		this.currentRemote = remote;
		this.statusLabel.setText("");

		if (remote != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			// show remote details
			this.remoteName.setText(remote.getName());
			this.remoteAddress.setText(remote.getAddress());
			this.remoteType.getSelectionModel().select(remote.getType());

			// show the features last fetched from this remote - it read those of the remote named
			// "origin", whichever was shown, and failed if there was none
			TreeItem<FeatureInfo> rootTreeItem = new TreeItem<>();
			for (Feature feature : remote.getFeatures()) {
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
