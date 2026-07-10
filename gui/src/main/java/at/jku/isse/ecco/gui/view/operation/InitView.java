package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

public class InitView extends OperationView {

	private EccoService service;

	public InitView(EccoService service) {
		super();
		this.service = service;

		this.step1();
	}


	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Repository Directory");

		Button initButton = new Button("Init");
		initButton.setDefaultButton(true);
		this.rightButtons.getChildren().setAll(initButton);


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
			} catch (Exception ignored) {
			}
			final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory != null) {
				repositoryDirTextField.setText(selectedDirectory.toPath().resolve(EccoService.REPOSITORY_DIR_NAME).toString());
			}
		});
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				service.init();
				return null;
			}
		};
		task.setOnFailed(event -> stepError("Error initializing repository.", task.getException()));
		task.setOnSucceeded(event -> {
			RecentRepositories.addRecentRepository(service.getRepositoryDir());
			stepSuccess("Repository was successfully initialized.");
		});
		initButton.setOnAction(event -> {
			Path repositoryDir = Paths.get(repositoryDirTextField.getText());
			Path baseDir = repositoryDir.getParent();

			// the text field is free-form (the user can type a path directly, not just pick an
			// existing one via the "..." chooser above), so the target directory may not exist yet,
			// or may still hold a ".ecco" from an earlier attempt - handle both, but only after the
			// user explicitly confirms, since one creates on disk and the other deletes on disk
			if (baseDir != null && !Files.exists(baseDir)) {
				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
						"The directory\n" + baseDir + "\ndoes not exist. Create it?",
						ButtonType.YES, ButtonType.CANCEL);
				confirm.setHeaderText("Create Directory");
				Optional<ButtonType> result = confirm.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.YES) {
					return;
				}
				try {
					Files.createDirectories(baseDir);
				} catch (IOException e) {
					stepError("Error creating directory.", e);
					return;
				}
			}

			if (Files.exists(repositoryDir)) {
				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
						"A repository already exists at\n" + repositoryDir + "\nDelete it and start fresh?",
						ButtonType.YES, ButtonType.CANCEL);
				confirm.setHeaderText("Delete Existing Repository");
				Optional<ButtonType> result = confirm.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.YES) {
					return;
				}
				try {
					deleteRecursively(repositoryDir);
				} catch (IOException e) {
					stepError("Error deleting existing repository.", e);
					return;
				}
			}

			this.service.setRepositoryDir(repositoryDir);
			this.service.setBaseDir(baseDir);
			pb.setProgress(-1.0f);
			pb.setVisible(true);
			Thread th = new Thread(task);
			th.setDaemon(true);
			th.start();
		});


		this.fit();
	}

	private static void deleteRecursively(Path directory) throws IOException {
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

}
