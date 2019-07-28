package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenView extends OperationView {

	private EccoService service;

	public OpenView(EccoService service) {
		super();
		this.service = service;

		this.step1();
	}


	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Repository Directory");

		Button openButton = new Button("Open");
		openButton.setDefaultButton(true);
		this.rightButtons.getChildren().setAll(openButton);


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

		TextField repositoryDirTextField = new TextField(service.getRepositoryDir().toString());
		repositoryDirTextField.setDisable(false);
		repositoryDirLabel.setLabelFor(repositoryDirTextField);
		gridPane.add(repositoryDirTextField, 1, row, 1, 1);

		Button selectRepositoryDirectoryButton = new Button("...");
		gridPane.add(selectRepositoryDirectoryButton, 2, row, 1, 1);
		row++;
		final ProgressBar pb = new ProgressBar();
		pb.setMaxWidth(Double.MAX_VALUE);
		pb.setVisible(false);
		pb.setProgress(0.0f);
		gridPane.add(pb, 0, row, 3, 1);
		gridPane.setFillWidth(pb, true);

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

		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				service.open();
				return null;
			}
		};
		task.setOnFailed(event -> stepError("Error opening repository.", task.getException()));
		task.setOnSucceeded(event -> stepSuccess("Repository was successfully opened."));
		openButton.setOnAction(event -> {
			Path repositoryDir = Paths.get(repositoryDirTextField.getText());
			this.service.setRepositoryDir(repositoryDir);
			this.service.setBaseDir(repositoryDir.getParent());
			pb.setProgress(-1.0f);
			pb.setVisible(true);
			Thread th = new Thread(task);
			th.setDaemon(true);
			th.start();
		});
		this.fit();
		Platform.runLater(repositoryDirTextField::requestFocus);
	}
}
