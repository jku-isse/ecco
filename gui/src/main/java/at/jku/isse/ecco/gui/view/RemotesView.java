package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.TaskFailures;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.RemoteDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.util.Collection;
import java.util.List;

public class RemotesView extends BorderPane implements EccoListener {

	private TableView<Remote> remotesTable;

	private EccoService service;

	private final ObservableList<Remote> remotesData = FXCollections.observableArrayList();


	public RemotesView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);
			this.remotesData.clear();

			Task featuresRefreshTask = new Task<Void>() {
				@Override
				public Void call() throws EccoException {
					Collection<? extends Remote> remotes = RemotesView.this.service.getRemotes();
					Platform.runLater(() -> {
						for (Remote remote : remotes) {
							RemotesView.this.remotesData.add(remote);
						}
					});
					Platform.runLater(() -> toolBar.setDisable(false));
					return null;
				}
			};

			TaskFailures.reportAndReenable(featuresRefreshTask, toolBar);
			new Thread(featuresRefreshTask).start();
		});

		Button addButton = new Button("Add");
		Label nameLabel = new Label("Name: ");
		TextField nameTextField = new TextField();
		nameLabel.setLabelFor(nameTextField);
		Label addressLabel = new Label(" Address: ");
		TextField addressTextField = new TextField();
		addressLabel.setLabelFor(addressTextField);

		Button removeButton = new Button("Remove");

		toolBar.getItems().addAll(refreshButton, new Separator(), nameLabel, nameTextField, addressLabel, addressTextField, addButton, new Separator(), removeButton, new Separator());


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of remotes
		TableView<Remote> remotesTable = this.remotesTable = new TableView<>();
		remotesTable.setEditable(true);
		remotesTable.setTableMenuButtonVisible(true);

		TableColumn<Remote, String> remoteNameCol = new TableColumn<>("Name");
		TableColumn<Remote, String> remoteAddressCol = new TableColumn<>("Address");
		TableColumn<Remote, String> remoteTypeCol = new TableColumn<>("Type");
		TableColumn<Remote, String> remotesCol = new TableColumn<>("Remotes");

		remotesCol.getColumns().addAll(remoteNameCol, remoteAddressCol, remoteTypeCol);
		remotesTable.getColumns().setAll(remotesCol);

		remoteNameCol.setCellValueFactory((TableColumn.CellDataFeatures<Remote, String> param) -> new ReadOnlyStringWrapper(param.getValue().getName()));
		remoteAddressCol.setCellValueFactory((TableColumn.CellDataFeatures<Remote, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAddress()));
		remoteTypeCol.setCellValueFactory((TableColumn.CellDataFeatures<Remote, String> param) -> new ReadOnlyStringWrapper(param.getValue().getType().toString()));

		remotesTable.setItems(this.remotesData);

		TableColumns.fitToContent(remoteNameCol, this.remotesData);
		TableColumns.fitToContent(remoteTypeCol, this.remotesData);
		TableColumns.growToFill(remotesTable, remoteAddressCol);


		// remote details view
		RemoteDetailView remoteDetailView = new RemoteDetailView(service);
		// the saved or fetched remote takes the place of its row - the same remote (a save renames it
		// in place), or else the one of its name before - at once, without asking the service (which
		// waits for its lock): the detail pane reports "Saved." after this reselects the remote. It
		// took the place of the one selected when the task ended, so a remote picked meanwhile was
		// gone from the list and the saved one listed twice; that one stays selected
		remoteDetailView.setOnRemoteChanged((name, changed) -> {
			int index = -1;
			for (int i = 0; i < this.remotesData.size() && index < 0; i++)
				if (this.remotesData.get(i) == changed)
					index = i;
			for (int i = 0; i < this.remotesData.size() && index < 0; i++)
				if (this.remotesData.get(i).getName().equals(name))
					index = i;
			boolean selected = index >= 0 && remotesTable.getSelectionModel().getSelectedIndices().contains(index);
			if (index >= 0)
				this.remotesData.set(index, changed);
			else
				this.remotesData.add(changed);
			if (selected)
				remotesTable.getSelectionModel().select(changed);
		});


		remotesTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				remoteDetailView.showRemote(newValue);
			} else {
				remoteDetailView.showRemote(null);
			}
		});


		// Add and Remove change the repository in the background, the toolbar disabled meanwhile: on
		// the FX thread they waited for the service's lock behind a commit, the window frozen (NFR-C3)
		addButton.setOnAction(event -> {
			String name = nameTextField.getText();
			String address = addressTextField.getText();
			Remote[] added = new Remote[1];
			toolBar.setDisable(true);
			RepositoryAccess.write("Add remote", () -> added[0] = this.service.addRemote(name, address), () -> {
				toolBar.setDisable(false);
				this.remotesData.add(added[0]);
			}, e -> {
				toolBar.setDisable(false);
				ExceptionAlert alert = new ExceptionAlert(e);
				alert.setTitle("Remotes Error");
				// the reason, not always "name already exists": an invalid address fails here too
				alert.setHeaderText("Error adding remote: " + TaskFailures.reason(e));
				alert.showAndWait();
			});
		});

		removeButton.setOnAction(event -> {
			List<Remote> selected = List.copyOf(remotesTable.getSelectionModel().getSelectedItems());
			if (selected.isEmpty())
				return;
			// those removed before a failure are gone from the list too
			List<Remote> removed = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
			toolBar.setDisable(true);
			RepositoryAccess.write("Remove remotes", () -> {
				for (Remote remote : selected) {
					this.service.removeRemote(remote.getName());
					removed.add(remote);
				}
			}, () -> {
				toolBar.setDisable(false);
				this.remotesData.removeAll(removed);
			}, e -> {
				toolBar.setDisable(false);
				this.remotesData.removeAll(removed);
				new ExceptionAlert(e).show();
			});
		});


		// add to split pane
		splitPane.getItems().addAll(remotesTable, remoteDetailView);

		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			// getRemotes() waits for the service's lock: on the FX thread (the first refresh, or
			// setBaseDir() firing this there) that froze the window behind a commit (NFR-C3)
			if (Thread.holdsLock(service))
				this.showRemotes(service.getRemotes(), null);
			else
				RepositoryAccess.read(service, "Remotes", EccoService::getRemotes, remotes -> this.showRemotes(remotes, null),
						failure -> new ExceptionAlert(failure).show());
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}

	/** Lists [remotes] - on the FX thread, or from any thread through runLater - selecting the one named [select], if any. */
	private void showRemotes(Collection<? extends Remote> remotes, String select) {
		Runnable show = () -> {
			this.remotesData.setAll(remotes);
			if (select != null)
				this.remotesData.stream().filter(remote -> remote.getName().equals(select)).findFirst()
						.ifPresent(remote -> this.remotesTable.getSelectionModel().select(remote));
		};
		if (Platform.isFxApplicationThread())
			show.run();
		else
			Platform.runLater(show);
	}

}
