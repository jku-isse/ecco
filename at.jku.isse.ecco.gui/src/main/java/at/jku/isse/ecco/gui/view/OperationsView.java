package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.view.detail.CheckoutDetailView;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;

public class OperationsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<FileInfo> logData = FXCollections.observableArrayList();


	SplitPane splitPane;
	CommitDetailView commitDetailView;
	CheckoutDetailView checkoutDetailView;
	TableView<FileInfo> fileTable;


	public OperationsView(EccoService service) {
		this.service = service;


		// toolbar
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

				Task commitTask = new Task<Commit>() {
					@Override
					public Commit call() throws EccoException {
						Commit commit;
						if (configurationString == null || configurationString.isEmpty())
							commit = OperationsView.this.service.commit();
						else
							commit = OperationsView.this.service.commit(configurationString);
						return commit;
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

				commitTask.valueProperty().addListener(new ChangeListener<Commit>() {
					@Override
					public void changed(ObservableValue<? extends Commit> obs, Commit oldValue, Commit newValue) {
						if (newValue != null) {
							OperationsView.this.commitDetailView.showCommit(newValue);

							OperationsView.this.splitPane.getItems().setAll(OperationsView.this.commitDetailView, OperationsView.this.fileTable);
							OperationsView.this.setCenter(OperationsView.this.splitPane);
						}
					}
				});

				new Thread(commitTask).start();
			}
		});

		checkoutButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				OperationsView.this.logData.clear();
				OperationsView.this.service.getWriter().addListener(OperationsView.this);

				Task checkoutTask = new Task<Checkout>() {
					@Override
					public Checkout call() throws EccoException {
						Checkout checkout = OperationsView.this.service.checkout(configurationStringInput.getText());
						return checkout;
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

				checkoutTask.valueProperty().addListener(new ChangeListener<Checkout>() {
					@Override
					public void changed(ObservableValue<? extends Checkout> obs, Checkout oldValue, Checkout newValue) {
						if (newValue != null) {
							OperationsView.this.checkoutDetailView.showCheckout(newValue);

							OperationsView.this.splitPane.getItems().setAll(OperationsView.this.checkoutDetailView, OperationsView.this.fileTable);
							OperationsView.this.setCenter(OperationsView.this.splitPane);
						}
					}
				});

				new Thread(checkoutTask).start();
			}
		});


		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);


		// details view
		this.commitDetailView = new CommitDetailView(service);
		this.checkoutDetailView = new CheckoutDetailView(service);


		// TABLE VIEW

		this.fileTable = new TableView<FileInfo>();
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

		splitPane.getItems().add(fileTable);


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
