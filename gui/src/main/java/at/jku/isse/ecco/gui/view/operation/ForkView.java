package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import at.jku.isse.ecco.service.RemoteAddress;
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
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Creates a new repository from another one - a local repository (or its working directory) or
 * the host:port of an ECCO server - optionally leaving out feature revisions, like the command
 * line's {@code fork [--exclude <revisions>] <remote>}. The remote is registered as origin.
 */
public class ForkView extends OperationView {

	private final EccoService service;

	public ForkView(EccoService service) {
		super();
		this.service = service;

		this.step1();
	}


	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Fork Repository");

		Button forkButton = new Button("Fork");
		forkButton.setDefaultButton(true);
		this.rightButtons.getChildren().setAll(forkButton);


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

		Label remoteLabel = new Label("Fork From: ");
		gridPane.add(remoteLabel, 0, row, 1, 1);

		TextField remoteTextField = new TextField();
		remoteTextField.setPromptText("repository directory, or host:port of an ECCO server");
		remoteTextField.setPrefWidth(360);
		remoteLabel.setLabelFor(remoteTextField);
		gridPane.add(remoteTextField, 1, row, 1, 1);

		Button selectRemoteButton = new Button("...");
		gridPane.add(selectRemoteButton, 2, row, 1, 1);
		row++;

		Label repositoryDirLabel = new Label("New Repository: ");
		gridPane.add(repositoryDirLabel, 0, row, 1, 1);

		TextField repositoryDirTextField = new TextField(service.getRepositoryDir().toString());
		repositoryDirLabel.setLabelFor(repositoryDirTextField);
		gridPane.add(repositoryDirTextField, 1, row, 1, 1);

		Button selectRepositoryDirectoryButton = new Button("...");
		gridPane.add(selectRepositoryDirectoryButton, 2, row, 1, 1);
		row++;

		Label excludeLabel = new Label("Exclude: ");
		gridPane.add(excludeLabel, 0, row, 1, 1);

		TextField excludeTextField = new TextField();
		excludeTextField.setPromptText("feature revisions to leave out, e.g. Video.3f2a9c1 (optional)");
		excludeLabel.setLabelFor(excludeTextField);
		gridPane.add(excludeTextField, 1, row, 2, 1);
		row++;

		final ProgressBar pb = new ProgressBar();
		pb.setMaxWidth(Double.MAX_VALUE);
		pb.setVisible(false);
		pb.setProgress(0.0f);
		gridPane.add(pb, 0, row, 3, 1);

		selectRemoteButton.setOnAction(event -> {
			File selected = chooseDirectory(remoteTextField.getText());
			if (selected != null)
				remoteTextField.setText(selected.toString());
		});
		selectRepositoryDirectoryButton.setOnAction(event -> {
			File selected = chooseDirectory(repositoryDirTextField.getText());
			if (selected != null)
				repositoryDirTextField.setText(selected.toPath().resolve(EccoService.REPOSITORY_DIR_NAME).toString());
		});

		forkButton.setOnAction(event -> {
			String remote = remoteTextField.getText() == null ? "" : remoteTextField.getText().trim();
			String exclude = excludeTextField.getText() == null ? "" : excludeTextField.getText().trim();

			// host:port first: "127.0.0.1:3770" is also a syntactically valid path
			Optional<InetSocketAddress> hostPort = RemoteAddress.parseHostPort(remote);
			Path originPath = null;
			if (hostPort.isEmpty()) {
				try {
					originPath = remote.isEmpty() ? null : Paths.get(remote);
				} catch (InvalidPathException ignored) {
				}
				if (originPath == null || !Files.isDirectory(originPath)) {
					invalidInput("Enter the directory of an existing repository, or host:port of an ECCO server.");
					return;
				}
			}

			Path repositoryDir;
			try {
				repositoryDir = Paths.get(repositoryDirTextField.getText());
			} catch (InvalidPathException e) {
				invalidInput("Invalid repository directory: " + e.getMessage());
				return;
			}
			if (Files.exists(repositoryDir)) {
				invalidInput("A repository already exists at\n" + repositoryDir + "\nChoose a directory without one.");
				return;
			}
			Path baseDir = repositoryDir.getParent();
			if (baseDir != null && !Files.exists(baseDir)) {
				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
						"The directory\n" + baseDir + "\ndoes not exist. Create it?",
						ButtonType.YES, ButtonType.CANCEL);
				confirm.setHeaderText("Create Directory");
				Optional<ButtonType> result = confirm.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.YES)
					return;
				try {
					Files.createDirectories(baseDir);
				} catch (IOException e) {
					stepError("Error creating directory.", e);
					return;
				}
			}

			this.service.setRepositoryDir(repositoryDir);
			this.service.setBaseDir(baseDir);

			final Path origin = originPath;
			Task<Void> task = new Task<>() {
				@Override
				protected Void call() {
					if (hostPort.isPresent())
						service.fork(hostPort.get().getHostString(), hostPort.get().getPort(), exclude);
					else
						service.fork(origin, exclude);
					return null;
				}
			};
			task.setOnFailed(e -> {
				pb.setVisible(false);
				stepError("Error forking repository.", task.getException());
			});
			task.setOnSucceeded(e -> {
				RecentRepositories.addRecentRepository(service.getRepositoryDir());
				stepSuccess("Repository was successfully forked.");
			});

			forkButton.setDisable(true);
			pb.setProgress(-1.0f);
			pb.setVisible(true);
			Thread th = new Thread(task);
			th.setDaemon(true);
			th.start();
		});


		this.fit();
	}

	private void invalidInput(String text) {
		Alert alert = new Alert(Alert.AlertType.WARNING, text, ButtonType.OK);
		alert.setHeaderText("Cannot Fork");
		alert.showAndWait();
	}

	private File chooseDirectory(String current) {
		final DirectoryChooser directoryChooser = new DirectoryChooser();
		try {
			Path directory = Paths.get(current);
			if (EccoService.REPOSITORY_DIR_NAME.equals(directory.getFileName()))
				directory = directory.getParent();
			if (directory != null && Files.isDirectory(directory))
				directoryChooser.setInitialDirectory(directory.toFile());
		} catch (Exception ignored) {
		}
		return directoryChooser.showDialog(this.getScene().getWindow());
	}

}
