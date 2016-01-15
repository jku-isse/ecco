package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;

public class OperationsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<FileInfo> logData = FXCollections.observableArrayList();

	public OperationsView(EccoService service) {
		this.service = service;

		Label configurationStringLabel = new Label("Configuration:");
		TextField configurationStringInput = new TextField();
		Button commitButton = new Button("Commit");
		Button checkoutButton = new Button("Checkout");

		ToolBar toolBar = new ToolBar();
		toolBar.getItems().addAll(configurationStringLabel, configurationStringInput, commitButton, checkoutButton);
		this.setTop(toolBar);

		commitButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				OperationsView.this.logData.clear();
				OperationsView.this.service.getReader().addListener(OperationsView.this);

				final String configurationString = configurationStringInput.getText();

				Task commitTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						if (configurationString == null || configurationString.isEmpty())
							OperationsView.this.service.commit();
						else
							OperationsView.this.service.commit(configurationString);
						return null;
					}

					public void finished() {
						OperationsView.this.service.getReader().removeListener(OperationsView.this);
						toolBar.setDisable(false);
					}

					@Override
					public void succeeded() {
						super.succeeded();
						this.finished();

						Alert alert = new Alert(Alert.AlertType.INFORMATION);
						alert.setTitle("Commit Successful");
						alert.setHeaderText("Commit Successful");
						alert.setContentText("Commit Successful!");

						alert.showAndWait();
					}

					@Override
					public void cancelled() {
						super.cancelled();
					}

					@Override
					public void failed() {
						super.failed();
						this.finished();

						ExceptionAlert alert = new ExceptionAlert(this.getException());
						alert.setTitle("Commit Error");
						alert.setHeaderText("Commit Error");

						alert.showAndWait();
					}
				};

				new Thread(commitTask).start();
			}
		});

		checkoutButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				OperationsView.this.logData.clear();
				OperationsView.this.service.getWriter().addListener(OperationsView.this);

				Task commitTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						OperationsView.this.service.checkout(configurationStringInput.getText());
						return null;
					}

					public void finished() {
						OperationsView.this.service.getWriter().removeListener(OperationsView.this);
						toolBar.setDisable(false);
					}

					@Override
					public void succeeded() {
						super.succeeded();
						this.finished();

						Alert alert = new Alert(Alert.AlertType.INFORMATION);
						alert.setTitle("Checkout Successful");
						alert.setHeaderText("Checkout Successful");
						alert.setContentText("Checkout Successful!");

						alert.showAndWait();
					}

					@Override
					public void cancelled() {
						super.cancelled();
					}

					@Override
					public void failed() {
						super.failed();
						this.finished();

						ExceptionAlert alert = new ExceptionAlert(this.getException());
						alert.setTitle("Checkout Error");
						alert.setHeaderText("Checkout Error");

						alert.showAndWait();
					}
				};

				new Thread(commitTask).start();
			}
		});

		// TABLE VIEW

		TableView<FileInfo> fileTable = new TableView<FileInfo>();
		fileTable.setEditable(false);
		fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FileInfo, String> actionCol = new TableColumn<FileInfo, String>("Action");
		TableColumn<FileInfo, String> pathCol = new TableColumn<FileInfo, String>("Path");
		TableColumn<FileInfo, String> pluginCol = new TableColumn<FileInfo, String>("Plugin");

		fileTable.getColumns().setAll(actionCol, pathCol, pluginCol);

		actionCol.setCellValueFactory(new PropertyValueFactory<FileInfo, String>("action"));
		pathCol.setCellValueFactory(new PropertyValueFactory<FileInfo, String>("path"));
		pluginCol.setCellValueFactory(new PropertyValueFactory<FileInfo, String>("plugin"));

		fileTable.setItems(this.logData);

		this.setCenter(fileTable);

		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				this.setDisable(false);
			});
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {

	}

	private static final String READ_ACTION_STRING = "READ";
	private static final String WRITE_ACTION_STRING = "WRITE";

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		Platform.runLater(() -> {
			this.logData.add(new FileInfo(this.READ_ACTION_STRING, file.toString(), reader.getPluginId()));
		});
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		Platform.runLater(() -> {
			this.logData.add(new FileInfo(this.WRITE_ACTION_STRING, file.toString(), writer.getPluginId()));
		});
	}

	public static class FileInfo {
		private final SimpleStringProperty action;
		private final SimpleStringProperty path;
		private final SimpleStringProperty plugin;

		private FileInfo(String action, String path, String plugin) {
			this.action = new SimpleStringProperty(action);
			this.path = new SimpleStringProperty(path);
			this.plugin = new SimpleStringProperty(plugin);
		}

		public String getAction() {
			return this.action.get();
		}

		public void setAction(String action) {
			this.action.set(action);
		}

		public String getPath() {
			return this.path.get();
		}

		public void setPath(String path) {
			this.path.set(path);
		}

		public String getPlugin() {
			return this.plugin.get();
		}

		public void setPlugin(String plugin) {
			this.plugin.set(plugin);
		}
	}

}
