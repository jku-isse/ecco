package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.gui.ExceptionAlert;
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

public class RemotesView extends BorderPane implements EccoListener {

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
		TableView<Remote> remotesTable = new TableView<>();
		remotesTable.setEditable(true);
		remotesTable.setTableMenuButtonVisible(true);
		remotesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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


		// remote details view
		RemoteDetailView remoteDetailView = new RemoteDetailView(service);


		remotesTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				remoteDetailView.showRemote(newValue);
			} else {
				remoteDetailView.showRemote(null);
			}
		});


		addButton.setOnAction(event -> {
			try {
				Remote remote = this.service.addRemote(nameTextField.getText(), addressTextField.getText());
				this.remotesData.add(remote);
			} catch (Exception e) {
				ExceptionAlert alert = new ExceptionAlert(e);
				alert.setTitle("Remotes Error");
				alert.setHeaderText("Error adding remote: name '" + nameTextField.getText() + "' already exists.");
				alert.showAndWait();
			}
		});

		removeButton.setOnAction(event -> {
			for (Remote remote : remotesTable.getSelectionModel().getSelectedItems()) {
				this.service.removeRemote(remote.getName());
				this.remotesData.remove(remote);
			}
		});


		// add to split pane
		splitPane.getItems().addAll(remotesTable, remoteDetailView);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			Collection<? extends Remote> remotes = service.getRemotes();
			Platform.runLater(() -> {
				this.remotesData.clear();
				for (Remote remote : remotes) {
					this.remotesData.add(remote);
				}
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}

}
