package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CommitView extends OperationView implements EccoListener {

	private EccoService service;

	private final ObservableList<FileInfo> logData = FXCollections.observableArrayList();


	private SplitPane splitPane;
	private CommitDetailView commitDetailView;
	private TableView<FileInfo> logTable;


	public CommitView(EccoService service) {
		super();
		this.service = service;


		// split pane
		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);

		// commit detail view
		this.commitDetailView = new CommitDetailView(service);

		// log table
		this.logTable = new TableView<>();
		logTable.setEditable(false);
		logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<FileInfo, String> actionCol = new TableColumn<>("Action");
		TableColumn<FileInfo, String> pathCol = new TableColumn<>("Path");
		TableColumn<FileInfo, String> pluginCol = new TableColumn<>("Plugin");

		logTable.getColumns().setAll(actionCol, pathCol, pluginCol);

		actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
		pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
		pluginCol.setCellValueFactory(new PropertyValueFactory<>("plugin"));

		logTable.setItems(this.logData);

		splitPane.getItems().add(logTable);


		this.step1();
	}


	/**
	 * Base directory and configuration string.
	 */
	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Directory and Configuration");

		Button diffButton = new Button("Diff");
		Button commitButton = new Button("Commit");
		this.rightButtons.getChildren().setAll(diffButton, commitButton);


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


		Label baseDirLabel = new Label("Base Directory: ");
		gridPane.add(baseDirLabel, 0, row, 1, 1);

		TextField baseDirTextField = new TextField(service.getBaseDir().toString());
		baseDirTextField.setDisable(false);
		baseDirLabel.setLabelFor(baseDirTextField);
		gridPane.add(baseDirTextField, 1, row, 1, 1);

		Button selectBaseDirectoryButton = new Button("...");
		gridPane.add(selectBaseDirectoryButton, 2, row, 1, 1);
		row++;


		Label configurationStringLabel = new Label("Configuration: ");
		gridPane.add(configurationStringLabel, 0, row, 1, 1);

		TextField configurationStringTextField = new TextField();
		configurationStringTextField.setDisable(false);
		configurationStringLabel.setLabelFor(configurationStringTextField);
		gridPane.add(configurationStringTextField, 1, row, 2, 1);
		row++;


		selectBaseDirectoryButton.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			try {
				Path directory = Paths.get(baseDirTextField.getText());
				if (Files.exists(directory) && Files.isDirectory(directory))
					directoryChooser.setInitialDirectory(directory.toFile());
			} catch (Exception e) {
				// do nothing
			}
			final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory != null) {
				baseDirTextField.setText(selectedDirectory.toPath().toString());
			}
		});


		commitButton.setOnAction(event -> {
			this.step2();

			Path baseDir = Paths.get(baseDirTextField.getText());
			String configurationString = configurationStringTextField.getText();

			this.service.setBaseDir(baseDir);

			this.logData.clear();
			this.service.addListener(this);

			Task<Commit> commitTask = new Task<Commit>() {
				@Override
				public Commit call() {
					if (!configurationString.isEmpty())
						return CommitView.this.service.commit(configurationString);
					else
						return CommitView.this.service.commit();
				}

				@Override
				public void succeeded() {
					super.succeeded();
					CommitView.this.service.removeListener(CommitView.this);
					// show value in commit detail view
					CommitView.this.commitDetailView.showCommit(this.getValue());
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logTable, CommitView.this.commitDetailView);
					CommitView.this.showSuccessHeader();
				}

				@Override
				public void cancelled() {
					super.cancelled();
					CommitView.this.service.removeListener(CommitView.this);
					// show exception textarea instead of commit detail view
					CommitView.this.commitDetailView.showCommit(null);
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logTable, new ExceptionTextArea(this.getException()));
					CommitView.this.showErrorHeader();
				}

				@Override
				public void failed() {
					super.failed();
					CommitView.this.service.removeListener(CommitView.this);
					// show exception textarea instead of commit detail view
					CommitView.this.commitDetailView.showCommit(null);
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logTable, new ExceptionTextArea(this.getException()));
					CommitView.this.showErrorHeader();
				}
			};
			new Thread(commitTask).start();
		});


		this.fit();

		Platform.runLater(configurationStringTextField::requestFocus);
	}

	/**
	 * Log table and success or error.
	 */
	private void step2() {
		Button cancelButton = new Button("Cancel");
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Committing ...");

		this.rightButtons.getChildren().clear();


		this.setCenter(splitPane);


		this.fit();
	}


	private static final String READ_ACTION_STRING = "READ";
	private static final String WRITE_ACTION_STRING = "WRITE";
	private static final String ASSOCIATION_SELECTION_STRING = "SELECT";

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		Platform.runLater(() -> this.logData.add(new FileInfo(this.READ_ACTION_STRING, file.toString(), reader.getPluginId())));
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		Platform.runLater(() -> this.logData.add(new FileInfo(this.WRITE_ACTION_STRING, file.toString(), writer.getPluginId())));
	}

	@Override
	public void associationSelectedEvent(EccoService service, Association association) {
		Platform.runLater(() -> this.logData.add(new FileInfo(this.ASSOCIATION_SELECTION_STRING, String.valueOf(association.getId()), association.computeCondition().toString())));
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
