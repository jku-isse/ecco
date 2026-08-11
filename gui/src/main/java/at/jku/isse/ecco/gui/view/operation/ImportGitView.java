package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Imports a contiguous range of commits from a LOCAL git clone as a sequence of ecco commits -
 * the "Import from Git" dialog. Same overall wizard shape as {@link CommitView}, reusing that
 * class's exact patterns (editable {@code TableView} row model, background {@link Task} +
 * {@code Platform.runLater} log lines, {@code succeeded()/cancelled()/failed()} wiring into
 * {@link OperationView}'s success/error header helpers) rather than inventing a new one:
 * <ol>
 *     <li>{@link #step1()} - pick the local repo and a commit range from a table of its history,
 *     and optionally opt out of LLM suggestions via a checkbox (disabled when no model is
 *     configured, checked by default otherwise).</li>
 *     <li>{@link #suggestFeatures(List)} - if suggestions weren't opted out of, a background
 *     {@link Task} asks a local LLM (via {@link LlmFeatureSuggestionClient}, configured through
 *     {@link LlmPreferences}) to suggest a feature configuration for each selected commit, given
 *     its message/diff and the features already known in the target repository.</li>
 *     <li>{@link #step2(List, List)} - review/edit each commit's feature configuration string,
 *     pre-filled with the LLM's suggestion (or blank if suggestion failed/was skipped) - nothing
 *     is committed into ecco until this step is confirmed, since a wrong automatic label would
 *     silently corrupt every later analysis of the repository.</li>
 *     <li>{@link #step3(List)} - the actual import: each commit's tree is extracted to a fresh
 *     temp directory (never the clone's own working directory, which may be in active use) and
 *     committed via the same {@code service.setBaseDir(...)}/{@code service.commit(...)} calls
 *     {@link CommitView} uses, oldest commit first (later ecco commits must build on earlier
 *     ones - see this repository's Feature Model tab).</li>
 * </ol>
 */
public class ImportGitView extends OperationView implements EccoListener {

	private final EccoService service;
	private final GitHistoryReader gitHistoryReader = new GitHistoryReader();

	private Path repoDir;

	private SplitPane splitPane;
	private CommitDetailView commitDetailView;
	private TextArea logArea;

	public ImportGitView(EccoService service) {
		super();
		this.service = service;

		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);

		this.commitDetailView = new CommitDetailView();

		this.logArea = new TextArea();
		this.logArea.setEditable(false);
		this.logArea.setWrapText(false);

		this.splitPane.getItems().add(this.logArea);

		this.step1();
	}

	/**
	 * Pick a local git clone and a contiguous range of commits (oldest first, the order the import
	 * itself has to process them in) to import.
	 */
	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Select Repository and Commit Range");

		Button nextButton = new Button("Next");
		nextButton.setDisable(true);
		this.rightButtons.getChildren().setAll(nextButton);


		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setMinWidth(GridPane.USE_PREF_SIZE);
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(col1constraint, col2constraint, new ColumnConstraints());

		this.setCenter(gridPane);

		int row = 0;

		Label repoLabel = new Label("Git Repository: ");
		gridPane.add(repoLabel, 0, row, 1, 1);

		TextField repoPathField = new TextField();
		repoPathField.setEditable(false);
		gridPane.add(repoPathField, 1, row, 1, 1);

		Button chooseRepoButton = new Button("Choose...");
		gridPane.add(chooseRepoButton, 2, row, 1, 1);
		row++;

		Label commitsLabel = new Label("Commits to import (click, or shift-click for a range):");
		gridPane.add(commitsLabel, 0, row, 3, 1);
		row++;

		TableView<GitCommitInfo> commitsTable = this.buildCommitsTable();
		gridPane.add(commitsTable, 0, row, 3, 1);
		GridPane.setVgrow(commitsTable, Priority.ALWAYS);
		row++;

		boolean llmConfigured = !LlmPreferences.getModelName().isBlank();
		CheckBox suggestFeaturesCheckBox = new CheckBox("Suggest features using a local LLM");
		suggestFeaturesCheckBox.setSelected(llmConfigured);
		suggestFeaturesCheckBox.setDisable(!llmConfigured);
		if (!llmConfigured) {
			suggestFeaturesCheckBox.setTooltip(new javafx.scene.control.Tooltip(
					"Configure a model in Preferences → LLM Settings to enable this."));
		}
		gridPane.add(suggestFeaturesCheckBox, 0, row, 3, 1);
		row++;

		chooseRepoButton.setOnAction(event -> {
			DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select a Local Git Repository");
			File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
			if (selectedDirectory == null) {
				return;
			}

			Path selectedPath = selectedDirectory.toPath();
			if (!Files.exists(selectedPath.resolve(".git"))) {
				Alert alert = new Alert(Alert.AlertType.WARNING, "Not a git repository (no .git found):\n" + selectedPath);
				alert.showAndWait();
				return;
			}

			try {
				// listCommits() returns newest-first (matching plain "git log"); shown oldest-first
				// here instead, since that's both the order the user reads a history top-to-bottom
				// as "what happened" and the exact order the import itself has to process in
				List<GitCommitInfo> commits = this.gitHistoryReader.listCommits(selectedPath);
				Collections.reverse(commits);
				this.repoDir = selectedPath;
				repoPathField.setText(selectedPath.toString());
				commitsTable.setItems(FXCollections.observableArrayList(commits));
			} catch (EccoException e) {
				new ExceptionAlert(e).show();
			}
		});

		commitsTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<GitCommitInfo>) change ->
				nextButton.setDisable(commitsTable.getSelectionModel().getSelectedItems().isEmpty()));

		nextButton.setOnAction(event -> {
			List<Integer> selectedIndices = new ArrayList<>(commitsTable.getSelectionModel().getSelectedIndices());
			int minIndex = Collections.min(selectedIndices);
			int maxIndex = Collections.max(selectedIndices);
			// table is already oldest-first, matching the order the import itself needs
			List<GitCommitInfo> oldestFirst = new ArrayList<>(commitsTable.getItems().subList(minIndex, maxIndex + 1));
			if (suggestFeaturesCheckBox.isSelected()) {
				this.suggestFeatures(oldestFirst);
			} else {
				this.step2(oldestFirst, Collections.nCopies(oldestFirst.size(), ""), null);
			}
		});

		this.fit();
	}

	/**
	 * Builds the commit-history table (id/message/date columns) shown in {@link #step1()} for picking
	 * the commit range to import. Split out of {@link #step1()} purely for readability -- no behavior
	 * change from the previous single-method version. Item population happens later, in
	 * {@code chooseRepoButton}'s handler, once a repository has actually been chosen -- matches the
	 * previous version, where the table's items were likewise never set here.
	 */
	private TableView<GitCommitInfo> buildCommitsTable() {
		TableView<GitCommitInfo> commitsTable = new TableView<>();
		commitsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		commitsTable.setPrefHeight(300);

		TableColumn<GitCommitInfo, String> idCol = new TableColumn<>("Commit");
		idCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getShortId()));

		TableColumn<GitCommitInfo, String> messageCol = new TableColumn<>("Message");
		messageCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getMessage()));

		TableColumn<GitCommitInfo, String> dateCol = new TableColumn<>("Date");
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
		dateCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(dateFormatter.format(param.getValue().getDate())));

		commitsTable.getColumns().setAll(idCol, messageCol, dateCol);

		TableColumns.defaultWidth(idCol, 90);
		TableColumns.defaultWidth(dateCol, 150);
		TableColumns.growToFill(commitsTable, messageCol);

		return commitsTable;
	}

	/**
	 * Asks a local LLM to suggest a feature configuration per commit before showing the editable
	 * review table - a background {@link Task} so the (network) call never blocks the FX thread,
	 * with a minimal "please wait" UI of its own since this can take a while for a large range and
	 * there's nothing more specific to show progress against (unlike {@link #step3}'s per-commit
	 * log, this is genuinely one request for the whole batch).
	 * <p>
	 * Only called when {@link #step1()}'s "Suggest features using a local LLM" checkbox is checked
	 * (unchecked skips straight to {@link #step2} - see {@code nextButton}'s handler); a user can
	 * always opt out per-import, even with a model configured. The blank-model-name guard below is
	 * now just a defensive fallback, since the checkbox is disabled whenever no model is configured.
	 */
	private void suggestFeatures(List<GitCommitInfo> commitsOldestFirst) {
		if (LlmPreferences.getModelName().isBlank()) {
			Alert alert = new Alert(Alert.AlertType.WARNING,
					"No LLM model name is configured (Preferences → LLM Settings), so feature suggestions " +
							"would fail for every commit. Skipping suggestions - you can still fill in each " +
							"commit's configuration by hand on the next screen.");
			alert.showAndWait();
			this.step2(commitsOldestFirst, Collections.nCopies(commitsOldestFirst.size(), ""), null);
			return;
		}

		Button backButton = new Button("Back");
		backButton.setOnAction(event -> this.step1());
		this.leftButtons.getChildren().setAll(backButton);

		this.headerLabel.setText("Suggesting Features...");
		this.rightButtons.getChildren().clear();

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		VBox progressBox = new VBox(10, progressIndicator, new Label("Asking the local LLM to suggest feature configurations..."));
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.setCenter(progressBox);

		this.fit();

		Task<LlmFeatureSuggestionClient.SuggestionBatch> suggestTask = new Task<>() {
			@Override
			protected LlmFeatureSuggestionClient.SuggestionBatch call() {
				List<LlmFeatureSuggestionClient.CommitForSuggestion> commitsForSuggestion = commitsOldestFirst.stream()
						.map(commit -> new LlmFeatureSuggestionClient.CommitForSuggestion(
								commit.getShortId(), commit.getMessage(),
								ImportGitView.this.gitHistoryReader.getDiff(ImportGitView.this.repoDir, commit.getId())))
						.collect(Collectors.toList());

				List<String> knownFeatureNames = ImportGitView.this.service.getRepository().getFeatures().stream()
						.map(Feature::getName)
						.collect(Collectors.toList());

				LlmFeatureSuggestionClient client = new LlmFeatureSuggestionClient(LlmPreferences.getEndpointUrl(), LlmPreferences.getModelName());
				return client.suggestConfigurations(commitsForSuggestion, knownFeatureNames);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				ImportGitView.this.step2(commitsOldestFirst, this.getValue().configurations(), this.getValue().failureReason());
			}

			@Override
			public void failed() {
				super.failed();
				// LlmFeatureSuggestionClient itself never throws (see its javadoc), but gathering
				// its inputs above (reading each commit's diff, listing known features) can - e.g. a
				// commit JGit can't diff cleanly, or a repository access problem. Surface it instead
				// of silently landing on a review table that looks like the LLM just had no
				// suggestions, then still let the user fill configurations in by hand.
				new ExceptionAlert(this.getException()).show();
				ImportGitView.this.step2(commitsOldestFirst, Collections.nCopies(commitsOldestFirst.size(), ""), null);
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Editable review of the feature configuration string that will be used for each selected
	 * commit, oldest first, pre-filled from {@code suggestedConfigurations} (same order/size as
	 * {@code commitsOldestFirst}) - nothing is committed into ecco until "Import" is clicked here.
	 */
	private void step2(List<GitCommitInfo> commitsOldestFirst, List<String> suggestedConfigurations, String suggestionFailureReason) {
		Button backButton = new Button("Back");
		backButton.setOnAction(event -> this.step1());
		this.leftButtons.getChildren().setAll(backButton);

		this.headerLabel.setText("Review Feature Configuration");

		Button importButton = new Button("Import");
		this.rightButtons.getChildren().setAll(importButton);


		ObservableList<CommitEntry> commitData = FXCollections.observableArrayList();
		for (int i = 0; i < commitsOldestFirst.size(); i++) {
			String suggestion = i < suggestedConfigurations.size() ? suggestedConfigurations.get(i) : "";
			CommitEntry commitEntry = new CommitEntry(commitsOldestFirst.get(i), suggestion);
			commitData.add(commitEntry);
			refreshConstraintWarning(commitEntry);
		}

		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setFillWidth(true);
		col1constraint.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(col1constraint);

		this.setCenter(gridPane);

		int row = 0;

		if (suggestionFailureReason != null) {
			Label warningLabel = new Label("LLM feature suggestions failed, so Configuration is blank below - " +
					"fill it in by hand, or go Back and retry once this is fixed. Reason: " + suggestionFailureReason);
			warningLabel.setWrapText(true);
			warningLabel.setStyle("-fx-text-fill: #cc6600;");
			gridPane.add(warningLabel, 0, row, 1, 1);
			row++;
		}

		Label label = new Label("Commits to import, oldest first - edit Configuration before importing:");
		gridPane.add(label, 0, row, 1, 1);
		row++;

		TableView<CommitEntry> reviewTable = this.buildReviewTable(commitData);
		gridPane.add(reviewTable, 0, row, 1, 1);
		GridPane.setVgrow(reviewTable, Priority.ALWAYS);
		row++;

		importButton.setOnAction(event -> {
			if (!confirmProceedDespiteViolations(commitData)) return;
			this.step3(new ArrayList<>(commitData));
		});

		this.fit();
	}

	/**
	 * Builds the editable review table (id/message/configuration/warning columns) shown in
	 * {@link #step2}. Split out of {@link #step2} purely for readability -- no behavior change from
	 * the previous single-method version.
	 */
	private TableView<CommitEntry> buildReviewTable(ObservableList<CommitEntry> commitData) {
		TableView<CommitEntry> reviewTable = new TableView<>();
		reviewTable.setEditable(true);
		reviewTable.setItems(commitData);
		reviewTable.setPrefHeight(300);

		TableColumn<CommitEntry, String> idCol = new TableColumn<>("Commit");
		idCol.setCellValueFactory(param -> param.getValue().shortIdProperty());
		idCol.setEditable(false);

		TableColumn<CommitEntry, String> messageCol = new TableColumn<>("Message");
		messageCol.setCellValueFactory(param -> param.getValue().messageProperty());
		messageCol.setEditable(false);

		TableColumn<CommitEntry, String> configCol = new TableColumn<>("Configuration");
		configCol.setCellValueFactory(param -> param.getValue().configurationProperty());
		configCol.setCellFactory(OperationView.editableStringCellFactory());
		configCol.setOnEditCommit(event -> {
			event.getRowValue().setConfiguration(event.getNewValue());
			refreshConstraintWarning(event.getRowValue());
		});

		// live constraint-violation feedback as a configuration is entered/edited -- see
		// EccoService.checkConstraintViolations; refreshed above and on initial population.
		TableColumn<CommitEntry, String> warningCol = new TableColumn<>("Constraint Warning");
		warningCol.setCellValueFactory(param -> param.getValue().warningProperty());
		warningCol.setCellFactory(col -> new javafx.scene.control.TableCell<CommitEntry, String>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || item.isEmpty()) {
					setText(null);
					setStyle("");
				} else {
					setText(item);
					setStyle("-fx-text-fill: firebrick; -fx-wrap-text: true;");
				}
			}
		});

		reviewTable.getColumns().setAll(idCol, messageCol, configCol, warningCol);

		TableColumns.defaultWidth(idCol, 90);
		TableColumns.fitToContent(configCol, commitData);
		TableColumns.fitToContent(warningCol, commitData);
		TableColumns.growToFill(reviewTable, messageCol);

		return reviewTable;
	}

	/**
	 * The actual import: extract, {@code setBaseDir}, {@code commit} - one iteration per reviewed
	 * commit, oldest first - exactly mirroring {@link CommitView}'s commit-loop {@link Task}.
	 */
	private void step3(List<CommitEntry> reviewedEntries) {
		Button cancelButton = new Button("Cancel");
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Importing ...");

		this.rightButtons.getChildren().clear();


		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);

		this.fit();

		this.logArea.clear();
		this.service.addListener(this);

		// Batch import can span many commits; a modal Alert per violating commit would be
		// disruptive, so violations are logged inline (like the "Imported ..." lines) and only
		// summarized in one Alert after the whole import finishes.
		List<String> allConstraintWarnings = new ArrayList<>();

		Task<Commit> importTask = new Task<Commit>() {
			@Override
			public Commit call() throws IOException {
				Commit lastCommit = null;
				for (CommitEntry entry : reviewedEntries) {
					Path tempDir = Files.createTempDirectory("ecco-git-import");
					try {
						ImportGitView.this.gitHistoryReader.extractCommitTree(ImportGitView.this.repoDir, entry.getCommitId(), tempDir);
						ImportGitView.this.service.setBaseDir(tempDir);
						String configurationString = entry.getConfiguration();
						long startMillis = System.currentTimeMillis();
						lastCommit = (configurationString != null && !configurationString.isEmpty())
								? ImportGitView.this.service.commit(entry.getMessage(), configurationString)
								: ImportGitView.this.service.commit(entry.getMessage());
						double durationSeconds = (System.currentTimeMillis() - startMillis) / 1000.0;
						Platform.runLater(() -> ImportGitView.this.logArea.appendText(
								String.format("Imported %s (%s) in %.2f seconds.%n", entry.getShortId(), entry.getMessage(), durationSeconds)));

						Commit committedEntry = lastCommit;
						List<String> violations = ImportGitView.this.service.checkConstraintViolations(committedEntry.getConfiguration());
						if (!violations.isEmpty()) {
							allConstraintWarnings.addAll(violations);
							Platform.runLater(() -> {
								for (String violation : violations)
									ImportGitView.this.logArea.appendText("CONSTRAINT: " + entry.getShortId() + ": " + violation + System.lineSeparator());
							});
						}
					} finally {
						deleteRecursively(tempDir);
					}
				}
				return lastCommit;
			}

			@Override
			public void succeeded() {
				super.succeeded();
				ImportGitView.this.service.removeListener(ImportGitView.this);
				ImportGitView.this.commitDetailView.showCommit(this.getValue());
				ImportGitView.this.splitPane.getItems().setAll(ImportGitView.this.logArea, ImportGitView.this.commitDetailView);
				ImportGitView.this.showSuccessHeader();
				if (!allConstraintWarnings.isEmpty()) {
					Alert alert = new Alert(Alert.AlertType.WARNING,
							"Imported commits violate accepted constraint(s):\n" + String.join("\n", allConstraintWarnings));
					alert.showAndWait();
				}
			}

			@Override
			public void cancelled() {
				super.cancelled();
				ImportGitView.this.service.removeListener(ImportGitView.this);
				ImportGitView.this.commitDetailView.showCommit(null);
				ImportGitView.this.splitPane.getItems().setAll(ImportGitView.this.logArea, new ExceptionTextArea(this.getException()));
				ImportGitView.this.showErrorHeader();
			}

			@Override
			public void failed() {
				super.failed();
				ImportGitView.this.service.removeListener(ImportGitView.this);
				ImportGitView.this.commitDetailView.showCommit(null);
				ImportGitView.this.splitPane.getItems().setAll(ImportGitView.this.logArea, new ExceptionTextArea(this.getException()));
				ImportGitView.this.showErrorHeader();
			}
		};
		new Thread(importTask).start();
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


	/**
	 * Live constraint-violation feedback (see {@code EccoService#checkConstraintViolations}) for one
	 * row's configuration, computed off the FX thread and written back into {@code entry}'s
	 * {@code warningProperty()} -- the review table's cell factory renders it in place.
	 */
	private void refreshConstraintWarning(CommitEntry entry) {
		String configurationString = entry.getConfiguration();
		if (configurationString == null || configurationString.isBlank() || !this.service.isInitialized()
				|| this.service.isWriteInProgress()) {
			entry.setWarning("");
			return;
		}
		new Thread(() -> {
			String description = describeConstraintViolations(configurationString);
			Platform.runLater(() -> entry.setWarning(description));
		}).start();
	}

	/** Empty string if no violations (or the configuration can't be parsed, e.g. mid-typing). */
	private String describeConstraintViolations(String configurationString) {
		try {
			Configuration configuration = this.service.parseConfigurationString(configurationString);
			List<String> violations = this.service.checkConstraintViolations(configuration);
			return violations.isEmpty() ? "" : "Violates accepted constraint(s): " + String.join("; ", violations);
		} catch (RuntimeException e) {
			return "";
		}
	}

	/**
	 * Aggregates each commit's already-computed warning (kept live by {@link #refreshConstraintWarning})
	 * and, if any violates an accepted constraint, asks the user to confirm before importing -- advisory
	 * only (see {@code EccoService#checkConstraintViolations}), never a hard block.
	 *
	 * @return true if there were no violations, or the user confirmed anyway; false to abort.
	 */
	private boolean confirmProceedDespiteViolations(List<CommitEntry> entries) {
		List<String> lines = new ArrayList<>();
		for (CommitEntry entry : entries) {
			String warning = entry.getWarning();
			if (warning != null && !warning.isEmpty()) {
				lines.add(entry.getShortId() + ": " + warning);
			}
		}
		if (lines.isEmpty()) return true;
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
				String.join("\n", lines) + "\n\nDo you want to import anyway?");
		alert.setHeaderText("Constraint violation");
		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
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

	/** getPluginId() returns a fully-qualified class name - just the simple class name is enough to display. */
	private static String shortPluginName(String pluginId) {
		if (pluginId == null) return null;
		int lastDot = pluginId.lastIndexOf('.');
		return lastDot < 0 ? pluginId : pluginId.substring(lastDot + 1);
	}


	/**
	 * One commit awaiting import, and its (editable) configuration string, as shown in the review
	 * table.
	 */
	public static class CommitEntry {
		private final String commitId;
		private final SimpleStringProperty shortId;
		private final SimpleStringProperty message;
		private final SimpleStringProperty configuration;
		private final SimpleStringProperty warning = new SimpleStringProperty("");

		private CommitEntry(GitCommitInfo commit, String configuration) {
			this.commitId = commit.getId();
			this.shortId = new SimpleStringProperty(commit.getShortId());
			this.message = new SimpleStringProperty(commit.getMessage());
			this.configuration = new SimpleStringProperty(configuration == null ? "" : configuration);
		}

		public String getWarning() {
			return this.warning.get();
		}

		public void setWarning(String warning) {
			this.warning.set(warning == null ? "" : warning);
		}

		public SimpleStringProperty warningProperty() {
			return this.warning;
		}

		public String getCommitId() {
			return this.commitId;
		}

		public String getShortId() {
			return this.shortId.get();
		}

		public SimpleStringProperty shortIdProperty() {
			return this.shortId;
		}

		public String getMessage() {
			return this.message.get();
		}

		public SimpleStringProperty messageProperty() {
			return this.message;
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
