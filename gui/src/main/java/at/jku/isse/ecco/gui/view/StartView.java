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

/**
 * What ECCO shows while no repository is open: the repositories opened before, to open with a
 * double-click (or Enter), and New, Open and Fork for others - the window was empty until a
 * repository was opened through the ribbon.
 */
public class StartView extends VBox {

	private final EccoService service;
	private final ListView<Path> recent = new ListView<>();
	private final Button openButton = new Button("Open");
	private final Label errorLabel = new Label();

	/**
	 * @param newRepository opens the New dialog
	 * @param openOther     opens the Open dialog, for a repository not in the list
	 * @param fork          opens the Fork dialog
	 */
	public StartView(EccoService service, Runnable newRepository, Runnable openOther, Runnable fork) {
		super(10);
		this.service = service;
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
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox buttons = new HBox(8, this.openButton, spacer, newButton, openOtherButton, forkButton);
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
		if (!this.recent.getItems().isEmpty())
			this.recent.getSelectionModel().select(0);
		this.errorLabel.setVisible(false);
		this.errorLabel.setManaged(false);
	}

	/** Opens the selected repository, in the background; a failure is said on the page. Package-visible for testing. */
	void openSelected() {
		Path repositoryDir = this.recent.getSelectionModel().getSelectedItem();
		if (repositoryDir == null || this.service.isInitialized())
			return;
		this.errorLabel.setVisible(false);
		this.errorLabel.setManaged(false);
		this.setDisable(true);
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() {
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
			Path folder = repositoryDir.getParent() == null ? repositoryDir : repositoryDir.getParent();
			this.errorLabel.setText("Could not open " + folder + ": " + ExceptionAlert.describe(task.getException()));
			this.errorLabel.setVisible(true);
			this.errorLabel.setManaged(true);
		});
		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();
	}

	/** The error said on the page, null if none. Package-visible for testing. */
	String error() {
		return this.errorLabel.isVisible() ? this.errorLabel.getText() : null;
	}
}
