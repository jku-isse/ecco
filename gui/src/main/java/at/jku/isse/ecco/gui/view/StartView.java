package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

/**
 * What ECCO shows while no repository is open, and under the Repositories tab: the repositories
 * opened before, to open with a double-click (or Enter) - another one than the open one closes that
 * first - to take off the list or to delete, and New, Open and Fork for others. The window was
 * empty until a repository was opened through the ribbon.
 */
public class StartView extends VBox {

	private final EccoService service;
	private final Runnable showOpen;
	private final ListView<Path> recent = new ListView<>();
	private final Button openButton = new Button("Open");
	private final Label errorLabel = new Label();
	/** New, Open Other and Fork - for a repository to come, so only while none is open, as in the ribbon. */
	private final List<Button> withoutOpenRepository = new java.util.ArrayList<>();

	/**
	 * @param newRepository opens the New dialog
	 * @param openOther     opens the Open dialog, for a repository not in the list
	 * @param fork          opens the Fork dialog
	 */
	public StartView(EccoService service, Runnable newRepository, Runnable openOther, Runnable fork) {
		this(service, newRepository, openOther, fork, () -> { });
	}

	/** @param showOpen shows the open repository - opening the one that is open already */
	public StartView(EccoService service, Runnable newRepository, Runnable openOther, Runnable fork, Runnable showOpen) {
		super(10);
		this.service = service;
		this.showOpen = showOpen;
		this.setPadding(new Insets(20, 24, 20, 24));
		this.setMaxWidth(760);

		Label title = new Label("Recent Repositories");
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		this.recent.setPlaceholder(new Label("No repository opened yet - create one with New..., or open one with Open Other...."));
		this.recent.setCellFactory(list -> new ListCell<>() {
			@Override
			protected void updateItem(Path repositoryDir, boolean empty) {
				super.updateItem(repositoryDir, empty);
				if (empty || repositoryDir == null) {
					this.setGraphic(null);
					this.setText(null);
					return;
				}
				// the folder worked in: its name, and where it is
				Path folder = repositoryDir.getParent() == null ? repositoryDir : repositoryDir.getParent();
				Label name = new Label(folder.getFileName() == null ? folder.toString() : folder.getFileName().toString());
				name.setStyle("-fx-font-weight: bold;");
				if (repositoryDir.equals(StartView.this.openRepository()))
					name.setText(name.getText() + "  (open)");
				Label where = new Label(folder.toString());
				where.setStyle("-fx-text-fill: #6b6a65; -fx-font-size: 11px;");
				this.setGraphic(new VBox(2, name, where));
				this.setText(null);
			}
		});
		this.recent.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2)
				this.openSelected();
		});
		this.recent.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ENTER)
				this.openSelected();
		});
		VBox.setVgrow(this.recent, Priority.ALWAYS);

		this.openButton.setDefaultButton(true);
		this.openButton.setOnAction(event -> this.openSelected());
		this.openButton.disableProperty().bind(this.recent.getSelectionModel().selectedItemProperty().isNull());
		this.openButton.setTooltip(new Tooltip("Open the selected repository - or double-click it"));
		Button newButton = new Button("New...");
		newButton.setOnAction(event -> newRepository.run());
		newButton.setTooltip(new Tooltip("Create a repository in a folder"));
		Button openOtherButton = new Button("Open Other...");
		openOtherButton.setOnAction(event -> openOther.run());
		openOtherButton.setTooltip(new Tooltip("Open a repository not in the list"));
		Button forkButton = new Button("Fork...");
		forkButton.setOnAction(event -> fork.run());
		forkButton.setTooltip(new Tooltip("Copy a repository, or a part of it, into a new one"));
		Button removeButton = new Button("Remove from List");
		removeButton.setOnAction(event -> this.removeSelected());
		removeButton.setTooltip(new Tooltip("Take it off this list - the repository stays as it is"));
		removeButton.disableProperty().bind(this.recent.getSelectionModel().selectedItemProperty().isNull());
		Button deleteButton = new Button("Delete Repository...");
		deleteButton.setOnAction(event -> this.deleteSelected());
		deleteButton.setTooltip(new Tooltip("Delete the repository - its .ecco folder; the files you work on are kept"));
		deleteButton.disableProperty().bind(this.recent.getSelectionModel().selectedItemProperty().isNull());
		javafx.scene.control.MenuItem openItem = new javafx.scene.control.MenuItem("Open");
		openItem.setOnAction(event -> this.openSelected());
		javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("Remove from List");
		removeItem.setOnAction(event -> this.removeSelected());
		javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("Delete Repository...");
		deleteItem.setOnAction(event -> this.deleteSelected());
		this.recent.setContextMenu(new javafx.scene.control.ContextMenu(openItem, removeItem, deleteItem));
		this.recent.setOnKeyReleased(event -> {
			if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)
				this.removeSelected();
		});
		this.withoutOpenRepository.addAll(List.of(newButton, openOtherButton, forkButton));
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox buttons = new HBox(8, this.openButton, removeButton, deleteButton, spacer, newButton, openOtherButton, forkButton);
		buttons.setAlignment(Pos.CENTER_LEFT);

		this.errorLabel.setWrapText(true);
		this.errorLabel.setStyle("-fx-text-fill: firebrick;");
		this.errorLabel.setVisible(false);
		this.errorLabel.setManaged(false);

		this.getChildren().addAll(title, this.recent, this.errorLabel, buttons);
		this.refresh();
	}

	/** Lists the recent repositories again - those that still exist - and selects the latest. */
	public void refresh() {
		this.recent.getItems().setAll(RecentRepositories.getRecentRepositories());
		this.recent.refresh(); // the (open) mark
		for (Button button : this.withoutOpenRepository)
			button.setDisable(this.service.isInitialized());
		if (!this.recent.getItems().isEmpty())
			this.recent.getSelectionModel().select(0);
		this.errorLabel.setVisible(false);
		this.errorLabel.setManaged(false);
	}

	/** The repository open now, null if none. Package-visible for testing. */
	Path openRepository() {
		return this.service.isInitialized() ? this.service.getRepositoryDir() : null;
	}

	/**
	 * Opens the selected repository, in the background - the open one, if it is another, is closed
	 * first; a failure is said on the page. Package-visible for testing.
	 */
	void openSelected() {
		Path repositoryDir = this.recent.getSelectionModel().getSelectedItem();
		if (repositoryDir == null)
			return;
		if (repositoryDir.equals(this.openRepository())) {
			this.showOpen.run();
			return;
		}
		this.errorLabel.setVisible(false);
		this.errorLabel.setManaged(false);
		this.setDisable(true);
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() {
				if (StartView.this.service.isInitialized())
					StartView.this.service.close();
				StartView.this.service.setRepositoryDir(repositoryDir);
				StartView.this.service.setBaseDir(repositoryDir.getParent());
				StartView.this.service.open();
				return null;
			}
		};
		task.setOnSucceeded(event -> {
			this.setDisable(false);
			RecentRepositories.addRecentRepository(repositoryDir);
		});
		task.setOnFailed(event -> {
			this.setDisable(false);
			this.showError("Could not open " + folderOf(repositoryDir) + ": " + ExceptionAlert.describe(task.getException()));
		});
		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();
	}

	/** Takes the selected repository off the list; the repository stays as it is. */
	private void removeSelected() {
		Path repositoryDir = this.recent.getSelectionModel().getSelectedItem();
		if (repositoryDir == null)
			return;
		RecentRepositories.removeRecentRepository(repositoryDir);
		this.refresh();
	}

	/**
	 * Deletes the selected repository - its .ecco folder, nothing else - after asking; an open one is
	 * closed first. In the background; a failure is said on the page.
	 */
	private void deleteSelected() {
		Path repositoryDir = this.recent.getSelectionModel().getSelectedItem();
		if (repositoryDir == null || !this.confirmDelete(repositoryDir))
			return;
		this.setDisable(true);
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() throws java.io.IOException {
				if (repositoryDir.equals(StartView.this.openRepository()))
					StartView.this.service.close();
				deleteRepository(repositoryDir);
				return null;
			}
		};
		task.setOnSucceeded(event -> {
			this.setDisable(false);
			RecentRepositories.removeRecentRepository(repositoryDir);
			this.refresh();
			at.jku.isse.ecco.gui.Notifications.success("Deleted the repository of " + folderOf(repositoryDir) + " - its files are kept.");
		});
		task.setOnFailed(event -> {
			this.setDisable(false);
			this.showError("Could not delete the repository of " + folderOf(repositoryDir) + ": " + ExceptionAlert.describe(task.getException()));
		});
		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();
	}

	/** Whether to delete [repositoryDir]: asked, Cancel the default. Package-visible for testing. */
	boolean confirmDelete(Path repositoryDir) {
		javafx.scene.control.ButtonType delete = new javafx.scene.control.ButtonType("Delete Repository", javafx.scene.control.ButtonBar.ButtonData.OTHER);
		at.jku.isse.ecco.gui.OwnedAlert alert = new at.jku.isse.ecco.gui.OwnedAlert(javafx.scene.control.Alert.AlertType.WARNING,
				repositoryDir + " - its commits, features, associations and variants - is deleted. This cannot be undone.\n\n"
						+ "The files you work on in " + folderOf(repositoryDir) + " are kept.",
				delete, javafx.scene.control.ButtonType.CANCEL);
		alert.setTitle("Delete Repository");
		alert.setHeaderText("Delete the repository of " + folderOf(repositoryDir) + "?");
		((Button) alert.getDialogPane().lookupButton(delete)).setDefaultButton(false);
		((Button) alert.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL)).setDefaultButton(true);
		return alert.showAndWait().filter(answer -> answer == delete).isPresent();
	}

	/** Deletes [repositoryDir] and everything in it - only a .ecco folder: the files worked on are never touched. */
	static void deleteRepository(Path repositoryDir) throws java.io.IOException {
		if (!EccoService.REPOSITORY_DIR_NAME.equals(repositoryDir.getFileName()) || !java.nio.file.Files.isDirectory(repositoryDir))
			throw new java.io.IOException("Not a repository (.ecco) folder: " + repositoryDir);
		try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(repositoryDir)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
				java.nio.file.Files.delete(path);
		}
	}

	private static Path folderOf(Path repositoryDir) {
		return repositoryDir.getParent() == null ? repositoryDir : repositoryDir.getParent();
	}

	private void showError(String text) {
		this.errorLabel.setText(text);
		this.errorLabel.setVisible(true);
		this.errorLabel.setManaged(true);
	}

	/** The error said on the page, null if none. Package-visible for testing. */
	String error() {
		return this.errorLabel.isVisible() ? this.errorLabel.getText() : null;
	}
}
