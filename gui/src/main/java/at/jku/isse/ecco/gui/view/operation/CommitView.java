package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommitView extends OperationView implements EccoListener {

	private EccoService service;

	private final ObservableList<FolderEntry> folderData = FXCollections.observableArrayList();


	private SplitPane splitPane;
	private CommitDetailView commitDetailView;
	private TextArea logArea;


	public CommitView(EccoService service) {
		super();
		this.service = service;


		// split pane
		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);

		// commit detail view
		this.commitDetailView = new CommitDetailView();

		// plain scrolling text log (one line per read/write/commit)
		this.logArea = new TextArea();
		logArea.setEditable(false);
		logArea.setWrapText(false);

		splitPane.getItems().add(logArea);


		this.step1();
	}


	/**
	 * Folders (in commit order), their configuration, and the commit message.
	 */
	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Folders and Configuration");

		Button commitButton = new Button("Commit");
		this.rightButtons.getChildren().setAll(commitButton);


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


		Label foldersLabel = new Label("Folders to commit (in order): ");
		gridPane.add(foldersLabel, 0, row, 2, 1);
		row++;

		// folders table: preview of the order folders will be committed in, and their configuration
		TableView<FolderEntry> foldersTable = new TableView<>();
		foldersTable.setEditable(true);
		foldersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		foldersTable.setItems(this.folderData);
		foldersTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		foldersTable.setPrefHeight(200);

		TableColumn<FolderEntry, Integer> orderCol = new TableColumn<>("#");
		orderCol.setSortable(false);
		orderCol.setReorderable(false);
		orderCol.setMinWidth(30);
		orderCol.setMaxWidth(40);
		orderCol.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : String.valueOf(getIndex() + 1));
			}
		});

		TableColumn<FolderEntry, String> folderCol = new TableColumn<>("Folder");
		folderCol.setCellValueFactory(param -> param.getValue().folderProperty());

		TableColumn<FolderEntry, String> configCol = new TableColumn<>("Configuration");
		configCol.setCellValueFactory(param -> param.getValue().configurationProperty());
		configCol.setCellFactory(OperationView.editableStringCellFactory());
		configCol.setOnEditCommit(event -> event.getRowValue().setConfiguration(event.getNewValue()));

		foldersTable.getColumns().setAll(orderCol, folderCol, configCol);

		Button addFolderButton = new Button("Add Folder...");
		Button addMultipleButton = new Button("Add Multiple from Parent...");
		Button removeFolderButton = new Button("Remove");
		Button moveUpButton = new Button("Move Up");
		Button moveDownButton = new Button("Move Down");
		removeFolderButton.setDisable(true);
		moveUpButton.setDisable(true);
		moveDownButton.setDisable(true);

		VBox folderButtons = new VBox(10, addFolderButton, addMultipleButton, removeFolderButton, moveUpButton, moveDownButton);
		folderButtons.setAlignment(Pos.TOP_CENTER);

		HBox foldersBox = new HBox(10, foldersTable, folderButtons);
		HBox.setHgrow(foldersTable, Priority.ALWAYS);
		gridPane.add(foldersBox, 0, row, 2, 1);
		row++;

		foldersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
			boolean hasSelection = newValue != null;
			removeFolderButton.setDisable(!hasSelection);
			moveUpButton.setDisable(!hasSelection || foldersTable.getSelectionModel().getSelectedIndex() <= 0);
			moveDownButton.setDisable(!hasSelection || foldersTable.getSelectionModel().getSelectedIndex() >= folderData.size() - 1);
		});
		folderData.addListener((ListChangeListener<FolderEntry>) change -> {
			int idx = foldersTable.getSelectionModel().getSelectedIndex();
			moveUpButton.setDisable(idx <= 0);
			moveDownButton.setDisable(idx < 0 || idx >= folderData.size() - 1);
		});

		addFolderButton.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select a Variant Folder to Add");
			Path initialDir = folderData.isEmpty() ? service.getBaseDir() : Paths.get(folderData.get(folderData.size() - 1).getFolder()).getParent();
			try {
				if (initialDir != null && Files.exists(initialDir) && Files.isDirectory(initialDir))
					directoryChooser.setInitialDirectory(initialDir.toFile());
			} catch (Exception ignored) {
			}
			final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory == null) {
				return;
			}

			Path selectedPath = selectedDirectory.toPath();
			String configurationString = this.service.getConfigStringFromFile(selectedPath);
			folderData.add(new FolderEntry(selectedPath, configurationString));
		});

		addMultipleButton.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select the Parent Folder Containing the Variant Folders to Add");
			Path initialDir = folderData.isEmpty() ? service.getBaseDir() : Paths.get(folderData.get(folderData.size() - 1).getFolder()).getParent();
			try {
				if (initialDir != null && Files.exists(initialDir) && Files.isDirectory(initialDir))
					directoryChooser.setInitialDirectory(initialDir.toFile());
			} catch (Exception ignored) {
			}
			final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory == null) {
				return;
			}

			Path selectedPath = selectedDirectory.toPath();
			List<Path> subfolders = listSubfolders(selectedPath);
			if (subfolders.isEmpty()) {
				Alert alert = new Alert(Alert.AlertType.WARNING, "The selected folder has no subfolders:\n" + selectedPath);
				alert.showAndWait();
				return;
			}

			for (Path folder : chooseSubfolders(selectedPath, subfolders)) {
				String configurationString = this.service.getConfigStringFromFile(folder);
				folderData.add(new FolderEntry(folder, configurationString));
			}
		});

		removeFolderButton.setOnAction(event -> {
			List<FolderEntry> selected = new ArrayList<>(foldersTable.getSelectionModel().getSelectedItems());
			folderData.removeAll(selected);
		});

		moveUpButton.setOnAction(event -> {
			int idx = foldersTable.getSelectionModel().getSelectedIndex();
			if (idx > 0) {
				Collections.swap(folderData, idx, idx - 1);
				foldersTable.getSelectionModel().select(idx - 1);
			}
		});

		moveDownButton.setOnAction(event -> {
			int idx = foldersTable.getSelectionModel().getSelectedIndex();
			if (idx >= 0 && idx < folderData.size() - 1) {
				Collections.swap(folderData, idx, idx + 1);
				foldersTable.getSelectionModel().select(idx + 1);
			}
		});


		Label commitMessageLabel = new Label("Commit Message: ");
		gridPane.add(commitMessageLabel, 0, row, 1, 1);

		TextField commitMessageStringTextField = new TextField();
		commitMessageStringTextField.setDisable(false);
		commitMessageLabel.setLabelFor(commitMessageStringTextField);
		gridPane.add(commitMessageStringTextField, 1, row, 1, 1);
		row++;


		commitButton.setOnAction(event -> {
			if (folderData.isEmpty()) {
				Alert alert = new Alert(Alert.AlertType.WARNING, "Add at least one folder to commit.");
				alert.showAndWait();
				return;
			}

			this.step2();

			String commitMessage = commitMessageStringTextField.getText();
			List<FolderEntry> foldersToCommit = new ArrayList<>(folderData);

			this.logArea.clear();
			this.service.addListener(this);

			Task<Commit> commitTask = new Task<Commit>() {
				@Override
				public Commit call() {
					Commit lastCommit = null;
					for (FolderEntry entry : foldersToCommit) {
						CommitView.this.service.setBaseDir(Paths.get(entry.getFolder()));
						String configurationString = entry.getConfiguration();
						long startMillis = System.currentTimeMillis();
						if (configurationString != null && !configurationString.isEmpty())
							lastCommit = CommitView.this.service.commit(commitMessage, configurationString);
						else
							lastCommit = CommitView.this.service.commit(commitMessage);
						double durationSeconds = (System.currentTimeMillis() - startMillis) / 1000.0;
						Platform.runLater(() -> CommitView.this.logArea.appendText(
								String.format("Committed %s in %.2f seconds.%n", entry.getFolder(), durationSeconds)));
					}
					return lastCommit;
				}

				@Override
				public void succeeded() {
					super.succeeded();
					CommitView.this.service.removeListener(CommitView.this);
					// show value in commit detail view
					CommitView.this.commitDetailView.showCommit(this.getValue());
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logArea, CommitView.this.commitDetailView);
					CommitView.this.showSuccessHeader();
				}

				@Override
				public void cancelled() {
					super.cancelled();
					CommitView.this.service.removeListener(CommitView.this);
					// show exception textarea instead of commit detail view
					CommitView.this.commitDetailView.showCommit(null);
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logArea, new ExceptionTextArea(this.getException()));
					CommitView.this.showErrorHeader();
				}

				@Override
				public void failed() {
					super.failed();
					CommitView.this.service.removeListener(CommitView.this);
					// show exception textarea instead of commit detail view
					CommitView.this.commitDetailView.showCommit(null);
					CommitView.this.splitPane.getItems().setAll(CommitView.this.logArea, new ExceptionTextArea(this.getException()));
					CommitView.this.showErrorHeader();
				}
			};
			new Thread(commitTask).start();
		});


		this.fit();

		Platform.runLater(commitMessageStringTextField::requestFocus);
	}

	private static List<Path> listSubfolders(Path parent) {
		try (Stream<Path> stream = Files.list(parent)) {
			return stream.filter(Files::isDirectory)
					.filter(p -> !p.getFileName().toString().startsWith("."))
					.sorted()
					.collect(Collectors.toList());
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * Shows a checklist of the given subfolders (of parent) and returns the ones the user checked,
	 * in the same order, or an empty list if the dialog was cancelled.
	 */
	private List<Path> chooseSubfolders(Path parent, List<Path> subfolders) {
		Dialog<List<Path>> dialog = new Dialog<>();
		dialog.setTitle("Select Folders");
		dialog.setHeaderText("Select which subfolders of\n" + parent + "\nto add:");
		dialog.initOwner(this.getScene().getWindow());

		ButtonType addButtonType = new ButtonType("Add Selected", ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

		Map<Path, BooleanProperty> selected = new LinkedHashMap<>();
		for (Path p : subfolders) {
			selected.put(p, new SimpleBooleanProperty(true));
		}

		ListView<Path> listView = new ListView<>(FXCollections.observableArrayList(subfolders));
		listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		listView.setCellFactory(CheckBoxListCell.forListView(selected::get,
				new StringConverter<Path>() {
					@Override
					public String toString(Path path) {
						return path.getFileName().toString();
					}

					@Override
					public Path fromString(String string) {
						return null;
					}
				}));
		listView.setPrefHeight(300);

		Label hintLabel = new Label("Tip: click a checkbox to toggle a folder, or Cmd/Shift-click to highlight several rows and use the buttons below.");
		hintLabel.setWrapText(true);

		Button checkAllButton = new Button("Check All");
		Button uncheckAllButton = new Button("Uncheck All");
		Button checkHighlightedButton = new Button("Check Highlighted");
		Button uncheckHighlightedButton = new Button("Uncheck Highlighted");
		checkAllButton.setOnAction(e -> selected.values().forEach(p -> p.set(true)));
		uncheckAllButton.setOnAction(e -> selected.values().forEach(p -> p.set(false)));
		checkHighlightedButton.setOnAction(e -> listView.getSelectionModel().getSelectedItems().forEach(p -> selected.get(p).set(true)));
		uncheckHighlightedButton.setOnAction(e -> listView.getSelectionModel().getSelectedItems().forEach(p -> selected.get(p).set(false)));
		HBox selectButtons = new HBox(10, checkAllButton, uncheckAllButton, checkHighlightedButton, uncheckHighlightedButton);

		VBox content = new VBox(10, hintLabel, selectButtons, listView);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		dialog.setResultConverter(buttonType -> {
			if (buttonType == addButtonType) {
				return subfolders.stream().filter(p -> selected.get(p).get()).collect(Collectors.toList());
			}
			return null;
		});

		return dialog.showAndWait().orElse(List.of());
	}

	/**
	 * Log table and success or error.
	 */
	private void step2() {
		Button cancelButton = new Button("Cancel");
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Committing ...");

		this.rightButtons.getChildren().clear();


		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);


		this.fit();
	}


	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		String plugin = shortPluginName(reader.getPluginId());
		Platform.runLater(() -> this.logArea.appendText(String.format("Read %s using (%s)%n", file, plugin)));
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		String plugin = shortPluginName(writer.getPluginId());
		Platform.runLater(() -> this.logArea.appendText(String.format("Wrote %s using (%s)%n", file, plugin)));
	}

	/** getPluginId() returns a fully-qualified class name (e.g. "at.jku.isse.ecco.adapter.lilypond.LilypondPlugin") - just the simple class name is enough to display. */
	private static String shortPluginName(String pluginId) {
		if (pluginId == null) return null;
		int lastDot = pluginId.lastIndexOf('.');
		return lastDot < 0 ? pluginId : pluginId.substring(lastDot + 1);
	}


	/**
	 * A folder to be committed, and its (editable) configuration string, as shown in the commit
	 * order preview table.
	 */
	public static class FolderEntry {
		private final SimpleStringProperty folder;
		private final SimpleStringProperty configuration;

		private FolderEntry(Path folder, String configuration) {
			this.folder = new SimpleStringProperty(folder.toString());
			this.configuration = new SimpleStringProperty(configuration == null ? "" : configuration);
		}

		public String getFolder() {
			return this.folder.get();
		}

		public SimpleStringProperty folderProperty() {
			return this.folder;
		}

		public String getConfiguration() {
			return this.configuration.get();
		}

		public void setConfiguration(String configuration) {
			this.configuration.set(configuration);
		}

		public SimpleStringProperty configurationProperty() {
			return this.configuration;
		}
	}

}
