package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.EditableSpinner;
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
import javafx.scene.layout.HBox;
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
 * the "Import from Git" dialog. Deliberately interactive rather than a batch pipeline: by default
 * the range is imported one commit at a time, pausing for review before EVERY commit, since a
 * wrong automatic label silently corrupts every later analysis of the repository and a batch of
 * dozens/hundreds of commits reviewed all at once (the previous design) makes that easy to
 * rubber-stamp past. {@link #step1()}'s "Review every N commits" spinner can widen that cadence
 * for a long, low-risk range - see {@link #processNextCommit} and {@link #autoImportOneCommit}.
 * <ol>
 *     <li>{@link #step1()} - pick the local repo and a commit range from a table of its history,
 *     optionally opt out of LLM suggestions via a checkbox (disabled when no model is configured,
 *     checked by default otherwise), and pick the review cadence (default 1 = every commit).</li>
 *     <li>{@link #startImport(List, boolean, int)} kicks off a loop, driven by
 *     {@link #processNextCommit(List, int, boolean, int)}, that for each commit in turn either
 *     shows it for review (see {@link #isReviewCommit}) or auto-imports it unattended:
 *     <ul>
 *         <li>{@link #suggestOneCommit(List, int, boolean, int)} - on a review commit, if
 *         suggestions weren't opted out of, asks a local LLM (via {@link LlmFeatureSuggestionClient},
 *         configured through {@link LlmPreferences}) for JUST that one commit's suggested
 *         feature(s), given its message/diff and the feature hierarchy inferred so far from
 *         commits already imported in this run ({@link FeatureModelTree}, the same computation
 *         behind the Feature Model tab - a growing knowledge graph, not just a flat name list, so
 *         the LLM can tell a genuinely new capability built on an existing feature from a change
 *         to that feature itself) - one request per commit rather than one batched request up
 *         front, so the user reviews each suggestion before the next one is even asked for.</li>
 *         <li>{@link #showCommitReview(List, int, boolean, int, String, String)} - shows that
 *         single commit's message and an editable configuration field, pre-filled with the LLM's
 *         suggestion merged onto whatever was actually imported so far (or blank if suggestions
 *         were skipped/failed), with live constraint-violation feedback. The user picks Import,
 *         Skip (leave this commit out, move on), or Stop (end the import here, keeping whatever
 *         was already imported).</li>
 *         <li>{@link #autoImportOneCommit(List, int, boolean, int)} - on a non-review commit
 *         (review interval &gt; 1), does the same LLM lookup unattended and imports immediately
 *         with whatever it suggests (or the unchanged running configuration if suggestions are
 *         off or fail) - no editable form, no constraint-violation confirmation, since nobody is
 *         necessarily watching. Still shows a Stop button, since this can run for a while
 *         unattended.</li>
 *         <li>{@link #importOneCommit(List, int, boolean, int, String)} - the actual import,
 *         reached from either path above: the commit's tree is extracted to a fresh temp
 *         directory (never the clone's own working directory, which may be in active use) and
 *         committed via the same {@code service.setBaseDir(...)}/{@code service.commit(...)}
 *         calls {@link CommitView} uses, then the loop advances to the next commit.</li>
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
	// populated only by autoImportOneCommit (never suggestOneCommit - a review screen already shows
	// its own suggestion failure prominently, with a human right there reading it before Import/Skip)
	// so an unattended auto-import stretch's LLM failures aren't just a single line easy to miss
	// among a fast-scrolling log - see finishImport()'s summary alert, mirroring how constraint
	// violations are already summarized.
	private List<String> llmSuggestionFailures = new ArrayList<>();
	private Commit lastCommit;
	private int importedCount;
	// set the moment this run ends (Back, Stop, finish, or a real failure) - a background
	// suggestion request (suggestOneCommit/autoImportOneCommit) started before that moment can
	// still be in flight, and this flag stops its late-arriving succeeded()/failed() from
	// repainting a screen the user has already left. FX-thread-only (Task callbacks run there),
	// so no synchronization needed - see navButtons(), finishImport(), reportImportFailure().
	private boolean runEnded;
	// set true the first time step1()'s brief post-open "Choose..." disable finishes - see there.
	// step1() re-runs on every "Back" navigation within the SAME already-open, already-settled dialog
	// Stage, which doesn't need (or want) that one-time settling wait repeated each time.
	private boolean chooseRepoButtonSettled = false;

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
		ProgressIndicator repoLoadingIndicator = new ProgressIndicator();
		repoLoadingIndicator.setMaxSize(20, 20);
		repoLoadingIndicator.setVisible(false);
		HBox chooseRepoBox = new HBox(6, chooseRepoButton, repoLoadingIndicator);
		chooseRepoBox.setAlignment(Pos.CENTER_LEFT);
		gridPane.add(chooseRepoBox, 2, row, 1, 1);
		row++;

		// this dialog's own Stage can still be settling into becoming the OS's key window right after
		// it's first shown - clicking "Choose..." during that window was observed (via a live native
		// sample, not guessed) to race macOS's own NSOpenPanel init against that settling process and
		// hang the ENTIRE app inside AppKit's native code (-[NSSavePanel _initBridgeAndStuff] stuck in
		// -[HIRunLoopSemaphore wait:], unrecoverable, well before this class's own code regains
		// control) - confirmed fixed by simply waiting a couple of seconds before clicking. Briefly
		// disabling the button covers that window automatically instead of requiring the user to
		// remember to wait themselves. Only needed once per dialog Stage - step1() re-runs on every
		// "Back" navigation within the same already-settled window, see chooseRepoButtonSettled.
		if (this.chooseRepoButtonSettled) {
			chooseRepoButton.setDisable(false);
		} else {
			chooseRepoButton.setDisable(true);
			PauseTransition chooseRepoSettleDelay = new PauseTransition(Duration.seconds(2));
			chooseRepoSettleDelay.setOnFinished(event -> {
				chooseRepoButton.setDisable(false);
				this.chooseRepoButtonSettled = true;
			});
			chooseRepoSettleDelay.play();
		}

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

		Label reviewIntervalLabel = new Label("Review every");
		EditableSpinner reviewIntervalSpinner = new EditableSpinner(1, 1000, 1);
		reviewIntervalSpinner.setPrefWidth(80);
		Label reviewIntervalSuffixLabel = new Label("commit(s) - commits in between are auto-imported unattended (1 = review every commit)");
		HBox reviewIntervalBox = new HBox(8, reviewIntervalLabel, reviewIntervalSpinner, reviewIntervalSuffixLabel);
		reviewIntervalBox.setAlignment(Pos.CENTER_LEFT);
		gridPane.add(reviewIntervalBox, 0, row, 3, 1);
		row++;

		chooseRepoButton.setOnAction(event -> {
			DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select a Local Git Repository");
			// without an initial directory, the native macOS picker falls back to whatever location
			// it considers its own default - observed directly (via a live thread dump) to hang
			// indefinitely inside the native call itself on this machine when left unset. OpenView/
			// InitView's own DirectoryChoosers never hit this because they always set one first (to
			// the already-configured repository path); mirror that same defensive pattern here -
			// this dialog's own last-picked repo if there was one, else the user's home directory
			// (always exists, unlike e.g. a stale/removed previous pick).
			Path initialDirectory = this.repoDir != null ? this.repoDir : Path.of(System.getProperty("user.home"));
			if (Files.exists(initialDirectory) && Files.isDirectory(initialDirectory)) {
				directoryChooser.setInitialDirectory(initialDirectory.toFile());
			}
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

			// listCommits() walks the WHOLE reachable git history via JGit - synchronous on the FX
			// thread here used to freeze the entire app (no spinner, no way to tell it was even doing
			// anything) for any repo with a large-enough history or a slow disk; a background Task
			// keeps the window responsive and shows the small spinner next to the button instead.
			chooseRepoButton.setDisable(true);
			nextButton.setDisable(true);
			repoLoadingIndicator.setVisible(true);

			Task<List<GitCommitInfo>> loadCommitsTask = new Task<>() {
				@Override
				protected List<GitCommitInfo> call() {
					// listCommits() returns newest-first (matching plain "git log"); shown oldest-first
					// here instead, since that's both the order the user reads a history top-to-bottom
					// as "what happened" and the exact order the import itself has to process in
					List<GitCommitInfo> commits = ImportGitView.this.gitHistoryReader.listCommits(selectedPath);
					Collections.reverse(commits);
					return commits;
				}

				@Override
				public void succeeded() {
					super.succeeded();
					chooseRepoButton.setDisable(false);
					repoLoadingIndicator.setVisible(false);
					ImportGitView.this.repoDir = selectedPath;
					repoPathField.setText(selectedPath.toString());
					commitsTable.setItems(FXCollections.observableArrayList(this.getValue()));
					// fresh items, nothing selected yet - matches nextButton's initial disabled state
					nextButton.setDisable(true);
				}

				@Override
				public void failed() {
					super.failed();
					chooseRepoButton.setDisable(false);
					repoLoadingIndicator.setVisible(false);
					// the table (and whatever was selected in it, if this wasn't the first load) is
					// untouched by a failed load - restore nextButton to match that unchanged selection
					// instead of leaving it stuck disabled from the loading state above.
					nextButton.setDisable(commitsTable.getSelectionModel().getSelectedItems().isEmpty());
					new ExceptionAlert(this.getException()).show();
				}
			};
			new Thread(loadCommitsTask).start();
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
			this.startImport(oldestFirst, useLlm, reviewIntervalSpinner.getValue());
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
	 * commit's screen, see {@link #navButtons(List)}).
	 *
	 * @param reviewInterval how often to pause for review - 1 reviews every commit; N reviews
	 *                       every Nth commit and auto-imports the rest unattended (see
	 *                       {@link #isReviewCommit} and {@link #autoImportOneCommit}).
	 */
	private void startImport(List<GitCommitInfo> commitsOldestFirst, boolean useLlm, int reviewInterval) {
		this.runningFeatures = new LinkedHashSet<>();
		this.allConstraintWarnings = new ArrayList<>();
		this.llmSuggestionFailures = new ArrayList<>();
		this.lastCommit = null;
		this.importedCount = 0;
		this.runEnded = false;

		this.logArea.clear();
		this.service.addListener(this);

		this.processNextCommit(commitsOldestFirst, 0, useLlm, reviewInterval);
	}

	/**
	 * Advances the per-commit loop: finishes if all commits are processed, aborts if this run has
	 * already ended (see {@link #runEnded}), auto-imports a non-review commit unattended, or -
	 * on a review commit - asks the LLM for a suggestion, or if suggestions are off goes straight
	 * to that commit's review screen with a blank/carried-forward default.
	 */
	private void processNextCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, int reviewInterval) {
		if (this.runEnded) {
			return;
		}
		if (index >= commitsOldestFirst.size()) {
			this.finishImport(commitsOldestFirst.size(), false);
			return;
		}
		if (!isReviewCommit(index, reviewInterval)) {
			this.autoImportOneCommit(commitsOldestFirst, index, useLlm, reviewInterval);
		} else if (useLlm) {
			this.suggestOneCommit(commitsOldestFirst, index, useLlm, reviewInterval);
		} else {
			this.showCommitReview(commitsOldestFirst, index, useLlm, reviewInterval, defaultConfigurationText(this.runningFeatures, ""), null);
		}
	}

	/**
	 * Whether the commit at {@code index} (0-based, within the selected range) is a review
	 * boundary under {@code reviewInterval} - e.g. reviewInterval 5 reviews the 5th, 10th, 15th...
	 * commit and auto-imports the rest. reviewInterval 1 reviews every commit (always true here).
	 * Pure and package-visible for testing.
	 */
	static boolean isReviewCommit(int index, int reviewInterval) {
		return (index + 1) % reviewInterval == 0;
	}

	/**
	 * Asks the LLM for just ONE commit's suggested configuration (a background {@link Task}, since
	 * the network call must never block the FX thread) before showing that commit's review screen -
	 * unlike the previous batch design, later commits aren't even asked about until this one has
	 * been reviewed.
	 */
	private void suggestOneCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, int reviewInterval) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.navButtons(commitsOldestFirst);
		this.headerLabel.setText("Suggesting Feature (commit " + (index + 1) + " of " + commitsOldestFirst.size() + ")");
		this.rightButtons.getChildren().clear();

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		Label progressLabel = new Label("Asking the local LLM to suggest a feature configuration for commit " + commit.getShortId() + "...");
		progressLabel.setWrapText(true);
		VBox progressBox = new VBox(10, progressIndicator, progressLabel);
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.showStepContent(progressBox);

		this.fit();

		Task<LlmFeatureSuggestionClient.SuggestionBatch> suggestTask = new Task<>() {
			@Override
			protected LlmFeatureSuggestionClient.SuggestionBatch call() {
				return ImportGitView.this.requestSuggestion(commit);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				if (ImportGitView.this.runEnded) {
					return;
				}
				LlmFeatureSuggestionClient.SuggestionBatch batch = this.getValue();
				String defaultConfig = defaultConfigurationText(ImportGitView.this.runningFeatures, batch.configurations().get(0));
				ImportGitView.this.showCommitReview(commitsOldestFirst, index, useLlm, reviewInterval, defaultConfig, batch.failureReason());
			}

			@Override
			public void failed() {
				super.failed();
				if (ImportGitView.this.runEnded) {
					return;
				}
				// LlmFeatureSuggestionClient itself never throws (see its javadoc), but gathering
				// its inputs above (reading this commit's diff, listing known features) can - e.g. a
				// commit JGit can't diff cleanly, or a repository access problem. Surface it instead
				// of silently landing on a review screen that looks like the LLM just had no
				// suggestion, then still let the user fill the configuration in by hand.
				new ExceptionAlert(this.getException()).show();
				String defaultConfig = defaultConfigurationText(ImportGitView.this.runningFeatures, "");
				ImportGitView.this.showCommitReview(commitsOldestFirst, index, useLlm, reviewInterval, defaultConfig, String.valueOf(this.getException()));
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Auto-imports ONE commit WITHOUT stopping for review - used for every commit that isn't a
	 * review boundary (see {@link #isReviewCommit}) when {@code reviewInterval} &gt; 1. Still asks
	 * the LLM for a suggestion first when {@code useLlm} is set (so the running feature hierarchy
	 * stays accurate for later commits, reviewed or not), but never shows an editable form or a
	 * constraint-violation confirmation - those only make sense with a human looking at the screen;
	 * any constraint violation is still logged after the fact by {@link #importOneCommit}, same as
	 * a reviewed commit. A suggestion failure here is logged rather than shown as a blocking
	 * {@link ExceptionAlert}, since nobody is necessarily watching an unattended auto-import
	 * stretch - the commit is still imported, just with its configuration carried forward
	 * unchanged.
	 */
	private void autoImportOneCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, int reviewInterval) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.navButtons(commitsOldestFirst);
		this.headerLabel.setText("Auto-Importing (commit " + (index + 1) + " of " + commitsOldestFirst.size() + ")");
		this.rightButtons.getChildren().clear();

		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		Label progressLabel = new Label("Auto-importing commit " + commit.getShortId() + " (reviewing every " + reviewInterval + " commit(s))...");
		progressLabel.setWrapText(true);
		VBox progressBox = new VBox(10, progressIndicator, progressLabel);
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.showStepContent(progressBox);

		this.fit();

		if (!useLlm) {
			this.importOneCommit(commitsOldestFirst, index, useLlm, reviewInterval,
					defaultConfigurationText(this.runningFeatures, ""));
			return;
		}

		Task<LlmFeatureSuggestionClient.SuggestionBatch> suggestTask = new Task<>() {
			@Override
			protected LlmFeatureSuggestionClient.SuggestionBatch call() {
				return ImportGitView.this.requestSuggestion(commit);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				if (ImportGitView.this.runEnded) {
					return;
				}
				LlmFeatureSuggestionClient.SuggestionBatch batch = this.getValue();
				if (batch.failureReason() != null) {
					ImportGitView.this.recordAutoImportSuggestionFailure(commit, batch.failureReason());
				}
				String config = defaultConfigurationText(ImportGitView.this.runningFeatures, batch.configurations().get(0));
				ImportGitView.this.importOneCommit(commitsOldestFirst, index, useLlm, reviewInterval, config);
			}

			@Override
			public void failed() {
				super.failed();
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.recordAutoImportSuggestionFailure(commit, String.valueOf(this.getException()));
				String config = defaultConfigurationText(ImportGitView.this.runningFeatures, "");
				ImportGitView.this.importOneCommit(commitsOldestFirst, index, useLlm, reviewInterval, config);
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Logs an auto-import LLM suggestion failure with a marker that stands out from the
	 * surrounding "Imported ..." lines when skimming a long log, AND records it for
	 * {@link #finishImport}'s end-of-run summary alert - a single log line during an unattended
	 * auto-import stretch (nobody necessarily watching in real time) is too easy to miss otherwise.
	 */
	private void recordAutoImportSuggestionFailure(GitCommitInfo commit, String reason) {
		this.llmSuggestionFailures.add(commit.getShortId() + ": " + reason);
		this.logArea.appendText("*** LLM SUGGESTION FAILED for " + commit.getShortId() +
				" (auto-imported with unchanged configuration): " + reason + System.lineSeparator());
	}

	/**
	 * The one-commit LLM suggestion request shared by {@link #suggestOneCommit} and
	 * {@link #autoImportOneCommit} - must be called off the FX thread (blocks on the HTTP call).
	 */
	private LlmFeatureSuggestionClient.SuggestionBatch requestSuggestion(GitCommitInfo commit) {
		String diff = this.gitHistoryReader.getDiff(this.repoDir, commit.getId());
		List<LlmFeatureSuggestionClient.CommitForSuggestion> commitForSuggestion = List.of(
				new LlmFeatureSuggestionClient.CommitForSuggestion(commit.getShortId(), commit.getMessage(), diff));

		// the feature hierarchy inferred so far from the commits actually imported in this run
		// (see FeatureModelTree, also what drives the Feature Model tab's tree) - not just a flat
		// list of names, so the model can tell a genuinely new, narrower capability built on an
		// existing feature from a change to that feature itself
		List<LlmFeatureSuggestionClient.KnownFeature> knownFeatures = FeatureModelTree.compute(this.service.getRepository()).stream()
				.map(placement -> new LlmFeatureSuggestionClient.KnownFeature(
						placement.feature.getName(),
						placement.parent == null ? null : placement.parent.getName()))
				.collect(Collectors.toList());

		LlmFeatureSuggestionClient client = new LlmFeatureSuggestionClient(LlmPreferences.getEndpointUrl(), LlmPreferences.getModelName());
		return client.suggestConfigurations(commitForSuggestion, knownFeatures, null);
	}

	/**
	 * Editable review of ONE commit's feature configuration string, pre-filled with
	 * {@code defaultConfig} - nothing is committed into ecco until this screen's Import button is
	 * clicked, and only for this one commit.
	 */
	private void showCommitReview(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, int reviewInterval, String defaultConfig, String suggestionFailureReason) {
		GitCommitInfo commit = commitsOldestFirst.get(index);

		this.navButtons(commitsOldestFirst);
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

		this.showStepContent(gridPane);

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

		skipButton.setOnAction(event -> this.processNextCommit(commitsOldestFirst, index + 1, useLlm, reviewInterval));

		importButton.setOnAction(event -> {
			String warning = constraintWarningLabel.getText();
			if (warning != null && !warning.isEmpty()) {
				Alert alert = new Alert(Alert.AlertType.CONFIRMATION, warning + "\n\nDo you want to import anyway?");
				alert.setHeaderText("Constraint violation");
				Optional<ButtonType> result = alert.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.OK) return;
			}
			this.importOneCommit(commitsOldestFirst, index, useLlm, reviewInterval, configField.getText());
		});

		this.fit();
	}

	/**
	 * The actual import of ONE commit - extract, {@code setBaseDir}, {@code commit} - then advances
	 * {@link #processNextCommit(List, int, boolean, int)} to the next one on success. Reached from
	 * both {@link #showCommitReview} (Import button) and {@link #autoImportOneCommit} (unattended).
	 */
	private void importOneCommit(List<GitCommitInfo> commitsOldestFirst, int index, boolean useLlm, int reviewInterval, String configurationText) {
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
		this.showStepContent(progressBox);

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
					String configLabel = (configurationText == null || configurationText.isEmpty()) ? "(no configuration)" : configurationText;
					Platform.runLater(() -> ImportGitView.this.logArea.appendText(
							String.format("Imported %s (%s) -> [%s] in %.2f seconds.%n", commit.getShortId(), commit.getMessage(), configLabel, durationSeconds)));

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
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.processNextCommit(commitsOldestFirst, index + 1, useLlm, reviewInterval);
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
	 * Left-hand nav button for the currently-shown screen: "Back" to {@link #step1()} while nothing
	 * has been imported yet in this run (nothing to lose), or "Stop" once at least one commit may
	 * already be imported (those stay imported either way - see {@link #finishImport(int, boolean)}
	 * - so going back to repository selection would be misleading). Both set {@link #runEnded}
	 * immediately, even though a background suggestion request ({@link #suggestOneCommit}/
	 * {@link #autoImportOneCommit}) may still be in flight for the screen being left - that
	 * request's late-arriving {@code succeeded()}/{@code failed()} checks the same flag and no-ops
	 * instead of repainting a screen the user has already left.
	 */
	private void navButtons(List<GitCommitInfo> commitsOldestFirst) {
		if (this.importedCount == 0) {
			Button backButton = new Button("Back");
			backButton.setOnAction(event -> {
				this.runEnded = true;
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
	 * Shows {@code stepContent} (the current step's form/progress UI) in the top half of
	 * {@link #splitPane} with the running import log always visible below it, instead of the log
	 * only appearing once the whole run ends - lets the user watch each commit (including its
	 * suggested/applied configuration string - see {@link #importOneCommit}'s log line) go by live,
	 * which matters most during an unattended auto-import stretch (see {@link #autoImportOneCommit})
	 * where nothing else on screen shows what's happening commit by commit.
	 */
	private void showStepContent(javafx.scene.Node stepContent) {
		this.splitPane.getItems().setAll(stepContent, this.logArea);
		this.splitPane.setDividerPositions(0.55);
		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);
	}

	/**
	 * Ends the per-commit loop, successfully - either all commits were processed, or the user
	 * clicked "Stop" partway through. Whatever was already imported (via {@link #importOneCommit})
	 * is already persisted in ecco either way; this just shows it.
	 */
	private void finishImport(int totalCommits, boolean stoppedEarly) {
		this.runEnded = true;
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

		if (!this.llmSuggestionFailures.isEmpty()) {
			Alert alert = new Alert(Alert.AlertType.WARNING,
					this.llmSuggestionFailures.size() + " auto-imported commit(s) had no fresh LLM suggestion " +
							"(kept the prior configuration unchanged instead) because the request failed:\n" +
							String.join("\n", this.llmSuggestionFailures));
			alert.showAndWait();
		}

		if (!this.allConstraintWarnings.isEmpty()) {
			Alert alert = new Alert(Alert.AlertType.WARNING,
					"Imported commits violate accepted constraint(s):\n" + String.join("\n", this.allConstraintWarnings));
			alert.showAndWait();
		}
	}

	/** Ends the per-commit loop on a real failure (as opposed to {@link #finishImport}'s success/stop). */
	private void reportImportFailure(Throwable exception) {
		this.runEnded = true;
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
	 * actually been imported so far ({@code runningFeatures}), plus/minus whatever {@code suggestion}
	 * says - accumulated forward rather than replaced, since ECCO's Configuration represents the full
	 * variant at that point, not a delta, so "no new feature detected" should never look like "no
	 * features at all". A plain token in {@code suggestion} is an addition; a "-name" token (see
	 * {@link LlmFeatureSuggestionClient}'s SYSTEM_PROMPT) is the LLM judging that KNOWN feature's own
	 * implementation to be clearly, unambiguously removed by this commit's diff, and drops it from
	 * the accumulated set - the one place a real removal has ANY chance of happening automatically,
	 * since without it every feature ever introduced stayed in every later commit's configuration
	 * forever (confirmed directly against a real 62-commit import: 0 of 62 configurations ever
	 * dropped a feature, which is what produced a near-complete, pathologically slow constraint
	 * graph downstream - see FeaturesView's force-directed-freeze fix for the symptom this caused).
	 * Removal is necessarily best-effort (an LLM can only catch a diff that visibly deletes a
	 * feature's own code, not slower obsolescence) - a human can still notice and edit anything this
	 * misses by hand on the review screen, same as before. Removing a feature name not currently in
	 * {@code runningFeatures} is a safe no-op, never an error. Pure and package-visible for testing.
	 */
	static String defaultConfigurationText(LinkedHashSet<String> runningFeatures, String suggestion) {
		LinkedHashSet<String> combined = new LinkedHashSet<>(runningFeatures);
		if (suggestion != null) {
			for (String token : suggestion.split(",")) {
				String trimmed = token.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (trimmed.startsWith("-") && trimmed.length() > 1) {
					combined.remove(trimmed.substring(1).trim());
				} else {
					combined.add(trimmed);
				}
			}
		}
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
