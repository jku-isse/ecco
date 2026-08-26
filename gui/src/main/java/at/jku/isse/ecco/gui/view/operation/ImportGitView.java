package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.FeatureModelTree;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
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
import javafx.util.Duration;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Imports a contiguous range of commits from a LOCAL git clone as a sequence of ecco commits -
 * the "Import from Git" dialog. Deliberately interactive rather than a batch pipeline: a large
 * range is imported one commit at a time, pausing for the user before EVERY commit, since a
 * wrong automatic label silently corrupts every later analysis of the repository and a batch of
 * dozens/hundreds of commits reviewed all at once (the previous design) makes that easy to
 * rubber-stamp past.
 * <ol>
 *     <li>{@link #step1()} - pick the local repo and a commit range from a table of its history,
 *     and optionally opt out of LLM suggestions via a checkbox (disabled when no model is
 *     configured, checked by default otherwise).</li>
 *     <li>{@link #startImport(List, boolean)} kicks off a loop, driven by
 *     {@link #processNextCommit(List, int, boolean)}, that for each commit in turn:
 *     <ul>
 *         <li>{@link #suggestOneCommit(List, int, boolean)} - if suggestions weren't opted out
 *         of, asks a local LLM (via {@link LlmFeatureSuggestionClient}, configured through
 *         {@link LlmPreferences}) for JUST that one commit's suggested feature(s), given its
 *         message/diff and the feature hierarchy inferred so far from commits already imported in
 *         this run ({@link FeatureModelTree}, the same computation behind the Feature Model tab -
 *         a growing knowledge graph, not just a flat name list, so the LLM can tell a genuinely new
 *         capability built on an existing feature from a change to that feature itself) - one
 *         request per commit rather than one batched request up front, so the user reviews each
 *         suggestion before the next one is even asked for.</li>
 *         <li>{@link #showCommitReview(List, int, boolean, String, String)} - shows that single
 *         commit's message and an editable configuration field, pre-filled with the LLM's
 *         suggestion merged onto whatever was actually imported so far (or blank if suggestions
 *         were skipped/failed), with live constraint-violation feedback. The user picks Import,
 *         Skip (leave this commit out, move on), or Stop (end the import here, keeping whatever
 *         was already imported).</li>
 *         <li>{@link #importOneCommit(List, int, boolean, String)} - only on Import: the commit's
 *         tree is extracted to a fresh temp directory (never the clone's own working directory,
 *         which may be in active use) and committed via the same
 *         {@code service.setBaseDir(...)}/{@code service.commit(...)} calls {@link CommitView}
 *         uses, then the loop advances to the next commit.</li>
 *     </ul>
 *     </li>
 * </ol>
 */
public class ImportGitView extends OperationView implements EccoListener {

	// matches CommitView's own log/detail split - see that class's identical splitPane setup
	private static final double LOG_DIVIDER_POSITION = 0.65;

	private final EccoService service;
	private final GitHistoryReader gitHistoryReader = new GitHistoryReader();

	private Path repoDir;

	// state accumulated across the per-commit loop started by startImport() - reset there on
	// every run (a "Back" from the first commit's screen returns to step1(), which can start a
	// fresh run)
	private LinkedHashSet<String> runningFeatures = new LinkedHashSet<>();
	private List<String> allConstraintWarnings = new ArrayList<>();
	private Commit lastCommit;
	private int importedCount;

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
		this.logArea.setPrefRowCount(20);
		this.logArea.setPrefColumnCount(80);
		this.logArea.setMinHeight(220);
		this.logArea.setMinWidth(500);

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

			boolean useLlm = suggestFeaturesCheckBox.isSelected();
			if (useLlm && LlmPreferences.getModelName().isBlank()) {
				Alert alert = new Alert(Alert.AlertType.WARNING,
						"No LLM model name is configured (Preferences → LLM Settings), so feature suggestions " +
								"would fail for every commit. Skipping suggestions - you can still fill in each " +
								"commit's configuration by hand on the next screen.");
				alert.showAndWait();
				useLlm = false;
			}
			this.startImport(oldestFirst, useLlm);
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
	 * Kicks off the interactive per-commit loop over {@code commitsOldestFirst}, resetting all
	 * state accumulated by a previous run of this same dialog (reachable via "Back" from the first
	 * commit's screen, see {@link #navButtons(int, List)}).
	 */
	private void startImport(List<GitCommitInfo> commitsOldestFirst, boolean useLlm) {
		this.runningFeatures = new LinkedHashSet<>();
		this.allConstraintWarnings = new ArrayList<>();
		this.lastCommit = null;
		this.importedCount = 0;

		this.logArea.clear();
		this.service.addListener(this);

		this.processNextCommit(commitsOldestFirst, 0, useLlm);
	}

	/**
	 * Advances the per-commit loop: either finishes (all commits processed), asks the LLM for the
	 * next commit's suggestion, or - if suggestions are off - goes straight to that commit's review
	 * screen with a blank/carried-forward default.
	 */
	private void processNextCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm) {
		if (index >= commitsOldestFirst.size()) {
			this.finishImport(commitsOldestFirst.size(), false);
			return;
		}
		if (useLlm) {
			this.suggestOneCommit(commitsOldestFirst, index, useLlm);
		} else {
			this.showCommitReview(commitsOldestFirst, index, useLlm, defaultConfigurationText(this.runningFeatures, ""), null);
		}
	}

	/**
	 * Asks the LLM for just ONE commit's suggested configuration (a background {@link Task}, since
	 * the network call must never block the FX thread) before showing that commit's review screen -
	 * unlike the previous batch design, later commits aren't even asked about until this one has
	 * been reviewed.
	 */
	private void suggestOneCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.navButtons(index, commitsOldestFirst);
		this.headerLabel.setText("Suggesting Feature (commit " + (index + 1) + " of " + commitsOldestFirst.size() + ")");
		this.rightButtons.getChildren().clear();

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		Label progressLabel = new Label("Asking the local LLM to suggest a feature configuration for commit " + commit.getShortId() + "...");
		progressLabel.setWrapText(true);
		VBox progressBox = new VBox(10, progressIndicator, progressLabel);
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.setCenter(progressBox);

		this.fit();

		Task<LlmFeatureSuggestionClient.SuggestionBatch> suggestTask = new Task<>() {
			@Override
			protected LlmFeatureSuggestionClient.SuggestionBatch call() {
				String diff = ImportGitView.this.gitHistoryReader.getDiff(ImportGitView.this.repoDir, commit.getId());
				List<LlmFeatureSuggestionClient.CommitForSuggestion> commitForSuggestion = List.of(
						new LlmFeatureSuggestionClient.CommitForSuggestion(commit.getShortId(), commit.getMessage(), diff));

				// the feature hierarchy inferred so far from the commits actually imported in this
				// run (see FeatureModelTree, also what drives the Feature Model tab's tree) - not
				// just a flat list of names, so the model can tell a genuinely new, narrower
				// capability built on an existing feature from a change to that feature itself
				List<LlmFeatureSuggestionClient.KnownFeature> knownFeatures = FeatureModelTree.compute(ImportGitView.this.service.getRepository()).stream()
						.map(placement -> new LlmFeatureSuggestionClient.KnownFeature(
								placement.feature.getName(),
								placement.parent == null ? null : placement.parent.getName()))
						.collect(Collectors.toList());

				LlmFeatureSuggestionClient client = new LlmFeatureSuggestionClient(LlmPreferences.getEndpointUrl(), LlmPreferences.getModelName());
				return client.suggestConfigurations(commitForSuggestion, knownFeatures, null);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				LlmFeatureSuggestionClient.SuggestionBatch batch = this.getValue();
				String defaultConfig = defaultConfigurationText(ImportGitView.this.runningFeatures, batch.configurations().get(0));
				ImportGitView.this.showCommitReview(commitsOldestFirst, index, useLlm, defaultConfig, batch.failureReason());
			}

			@Override
			public void failed() {
				super.failed();
				// LlmFeatureSuggestionClient itself never throws (see its javadoc), but gathering
				// its inputs above (reading this commit's diff, listing known features) can - e.g. a
				// commit JGit can't diff cleanly, or a repository access problem. Surface it instead
				// of silently landing on a review screen that looks like the LLM just had no
				// suggestion, then still let the user fill the configuration in by hand.
				new ExceptionAlert(this.getException()).show();
				String defaultConfig = defaultConfigurationText(ImportGitView.this.runningFeatures, "");
				ImportGitView.this.showCommitReview(commitsOldestFirst, index, useLlm, defaultConfig, String.valueOf(this.getException()));
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Editable review of ONE commit's feature configuration string, pre-filled with
	 * {@code defaultConfig} - nothing is committed into ecco until this screen's Import button is
	 * clicked, and only for this one commit.
	 */
	private void showCommitReview(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, String defaultConfig, String suggestionFailureReason) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.navButtons(index, commitsOldestFirst);
		this.headerLabel.setText("Review Commit (" + (index + 1) + " of " + commitsOldestFirst.size() + ")");

		Button skipButton = new Button("Skip");
		Button importButton = new Button("Import");
		importButton.setDefaultButton(true);
		this.rightButtons.getChildren().setAll(skipButton, importButton);

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
			Label warningLabel = new Label("LLM feature suggestion failed for this commit, so Configuration is " +
					"blank/unchanged below - fill it in by hand. Reason: " + suggestionFailureReason);
			warningLabel.setWrapText(true);
			warningLabel.setStyle("-fx-text-fill: #cc6600;");
			gridPane.add(warningLabel, 0, row, 1, 1);
			row++;
		}

		Label commitLabel = new Label(commit.getShortId() + " — " + commit.getMessage());
		commitLabel.setWrapText(true);
		commitLabel.setStyle("-fx-font-weight: bold;");
		gridPane.add(commitLabel, 0, row, 1, 1);
		row++;

		Label configLabel = new Label("Configuration:");
		gridPane.add(configLabel, 0, row, 1, 1);
		row++;

		TextField configField = new TextField(defaultConfig == null ? "" : defaultConfig);
		gridPane.add(configField, 0, row, 1, 1);
		row++;

		Label constraintWarningLabel = new Label();
		constraintWarningLabel.setWrapText(true);
		constraintWarningLabel.setStyle("-fx-text-fill: firebrick;");
		gridPane.add(constraintWarningLabel, 0, row, 1, 1);
		row++;

		// live constraint-violation feedback as the configuration is edited, debounced so typing
		// doesn't spawn a background check per keystroke -- only one commit's field is ever live at
		// once here, unlike the previous batch review table, so a short debounce is enough (no need
		// for that table's single-background-thread queueing).
		PauseTransition debounce = new PauseTransition(Duration.millis(400));
		debounce.setOnFinished(event -> {
			String configurationText = configField.getText();
			new Thread(() -> {
				String description = (configurationText == null || configurationText.isBlank()
						|| !this.service.isInitialized() || this.service.isWriteInProgress())
						? "" : this.describeConstraintViolations(configurationText);
				Platform.runLater(() -> constraintWarningLabel.setText(description));
			}).start();
		});
		configField.textProperty().addListener((observable, oldValue, newValue) -> debounce.playFromStart());
		debounce.playFromStart();

		skipButton.setOnAction(event -> this.processNextCommit(commitsOldestFirst, index + 1, useLlm));

		importButton.setOnAction(event -> {
			String warning = constraintWarningLabel.getText();
			if (warning != null && !warning.isEmpty()) {
				Alert alert = new Alert(Alert.AlertType.CONFIRMATION, warning + "\n\nDo you want to import anyway?");
				alert.setHeaderText("Constraint violation");
				Optional<ButtonType> result = alert.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.OK) return;
			}
			this.importOneCommit(commitsOldestFirst, index, useLlm, configField.getText());
		});

		this.fit();
	}

	/**
	 * The actual import of ONE commit - extract, {@code setBaseDir}, {@code commit} - then advances
	 * {@link #processNextCommit(List, int, boolean)} to the next one on success.
	 */
	private void importOneCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, String configurationText) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.leftButtons.getChildren().clear();
		this.rightButtons.getChildren().clear();
		this.headerLabel.setText("Importing (commit " + (index + 1) + " of " + commitsOldestFirst.size() + ")");

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		Label progressLabel = new Label("Importing commit " + commit.getShortId() + "...");
		VBox progressBox = new VBox(10, progressIndicator, progressLabel);
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.setCenter(progressBox);

		this.fit();

		Task<Commit> importTask = new Task<Commit>() {
			@Override
			public Commit call() throws IOException {
				Path tempDir = Files.createTempDirectory("ecco-git-import");
				try {
					ImportGitView.this.gitHistoryReader.extractCommitTree(ImportGitView.this.repoDir, commit.getId(), tempDir);
					ImportGitView.this.service.setBaseDir(tempDir);
					long startMillis = System.currentTimeMillis();
					Commit result = (configurationText != null && !configurationText.isEmpty())
							? ImportGitView.this.service.commit(commit.getMessage(), configurationText)
							: ImportGitView.this.service.commit(commit.getMessage());
					double durationSeconds = (System.currentTimeMillis() - startMillis) / 1000.0;
					Platform.runLater(() -> ImportGitView.this.logArea.appendText(
							String.format("Imported %s (%s) in %.2f seconds.%n", commit.getShortId(), commit.getMessage(), durationSeconds)));

					List<String> violations = ImportGitView.this.service.checkConstraintViolations(result.getConfiguration());
					if (!violations.isEmpty()) {
						ImportGitView.this.allConstraintWarnings.addAll(violations);
						Platform.runLater(() -> {
							for (String violation : violations)
								ImportGitView.this.logArea.appendText("CONSTRAINT: " + commit.getShortId() + ": " + violation + System.lineSeparator());
						});
					}
					return result;
				} finally {
					deleteRecursively(tempDir);
				}
			}

			@Override
			public void succeeded() {
				super.succeeded();
				ImportGitView.this.lastCommit = this.getValue();
				ImportGitView.this.importedCount++;
				applyConfigurationToRunning(ImportGitView.this.runningFeatures, configurationText);
				ImportGitView.this.processNextCommit(commitsOldestFirst, index + 1, useLlm);
			}

			@Override
			public void cancelled() {
				super.cancelled();
				ImportGitView.this.reportImportFailure(this.getException());
			}

			@Override
			public void failed() {
				super.failed();
				ImportGitView.this.reportImportFailure(this.getException());
			}
		};
		new Thread(importTask).start();
	}

	/**
	 * Left-hand nav button for the currently-shown commit's suggest/review screen: "Back" to
	 * {@link #step1()} while the very first commit hasn't been imported yet (nothing to lose), or
	 * "Stop" once at least one commit may already be imported (those stay imported either way - see
	 * {@link #finishImport(int, boolean)} - so going back to repository selection would be
	 * misleading).
	 */
	private void navButtons(int index, List<GitCommitInfo> commitsOldestFirst) {
		if (index == 0) {
			Button backButton = new Button("Back");
			backButton.setOnAction(event -> {
				// startImport() already registered this listener; undo that here since this run is
				// being abandoned before importOneCommit()/finishImport() would otherwise remove it,
				// so a later run doesn't end up double-registered.
				this.service.removeListener(this);
				this.step1();
			});
			this.leftButtons.getChildren().setAll(backButton);
		} else {
			Button stopButton = new Button("Stop");
			stopButton.setOnAction(event -> this.finishImport(commitsOldestFirst.size(), true));
			this.leftButtons.getChildren().setAll(stopButton);
		}
	}

	/**
	 * Ends the per-commit loop, successfully - either all commits were processed, or the user
	 * clicked "Stop" partway through. Whatever was already imported (via {@link #importOneCommit})
	 * is already persisted in ecco either way; this just shows it.
	 */
	private void finishImport(int totalCommits, boolean stoppedEarly) {
		this.service.removeListener(this);

		if (stoppedEarly) {
			this.logArea.appendText(String.format("Stopped: imported %d of %d commit(s).%n", this.importedCount, totalCommits));
		}

		this.commitDetailView.showCommit(this.lastCommit);
		this.splitPane.getItems().setAll(this.logArea, this.commitDetailView);
		this.splitPane.setDividerPositions(LOG_DIVIDER_POSITION);
		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);
		this.showSuccessHeader();
		this.fit();

		if (!this.allConstraintWarnings.isEmpty()) {
			Alert alert = new Alert(Alert.AlertType.WARNING,
					"Imported commits violate accepted constraint(s):\n" + String.join("\n", this.allConstraintWarnings));
			alert.showAndWait();
		}
	}

	/** Ends the per-commit loop on a real failure (as opposed to {@link #finishImport}'s success/stop). */
	private void reportImportFailure(Throwable exception) {
		this.service.removeListener(this);
		this.commitDetailView.showCommit(null);
		this.splitPane.getItems().setAll(this.logArea, new ExceptionTextArea(exception));
		this.splitPane.setDividerPositions(LOG_DIVIDER_POSITION);
		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);
		this.showErrorHeader();
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
	 * Parses a comma-separated configuration string into its feature-name tokens, trimmed and with
	 * blanks dropped, preserving first-seen order. Pure and package-visible for testing.
	 */
	static LinkedHashSet<String> parseFeatureTokens(String configurationString) {
		LinkedHashSet<String> tokens = new LinkedHashSet<>();
		if (configurationString != null) {
			for (String token : configurationString.split(",")) {
				String trimmed = token.trim();
				if (!trimmed.isEmpty()) {
					tokens.add(trimmed);
				}
			}
		}
		return tokens;
	}

	/**
	 * The default configuration text to pre-fill a commit's review field with: whatever has
	 * actually been imported so far ({@code runningFeatures}), plus any new feature(s) named in
	 * {@code suggestion} - accumulated forward rather than replaced, since ECCO's Configuration
	 * represents the full variant at that point, not a delta, so "no new feature detected" should
	 * never look like "no features at all". A genuine feature removal is something a human still has
	 * to notice and edit out by hand on the review screen. Pure and package-visible for testing.
	 */
	static String defaultConfigurationText(LinkedHashSet<String> runningFeatures, String suggestion) {
		LinkedHashSet<String> combined = new LinkedHashSet<>(runningFeatures);
		combined.addAll(parseFeatureTokens(suggestion));
		return String.join(", ", combined);
	}

	/**
	 * Updates {@code runningFeatures} to reflect a commit that was actually imported with
	 * {@code configurationString} (the user's final, possibly hand-edited text) - called only from
	 * {@link #importOneCommit}'s {@code succeeded()}, never for a skipped commit, so later commits'
	 * defaults build on what's actually in the repository rather than what was merely suggested.
	 */
	static void applyConfigurationToRunning(LinkedHashSet<String> runningFeatures, String configurationString) {
		runningFeatures.clear();
		runningFeatures.addAll(parseFeatureTokens(configurationString));
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

}
