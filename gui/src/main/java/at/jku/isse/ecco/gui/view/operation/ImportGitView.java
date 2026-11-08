package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.gui.PluginNames;
import at.jku.isse.ecco.gui.ConstraintWarnings;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.gitimport.GitImport;
import at.jku.isse.ecco.service.EccoListener;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import at.jku.isse.ecco.service.llm.SuggestionConfidence;
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
import javafx.scene.control.Tooltip;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Imports a contiguous range of commits from a LOCAL git clone as a sequence of ecco commits -
 * the "Import from Git" dialog. Deliberately interactive rather than a batch pipeline: by default
 * the range is imported one commit at a time, pausing for review before EVERY commit, since a
 * wrong automatic label silently corrupts every later analysis of the repository and a batch of
 * dozens/hundreds of commits reviewed all at once (the previous design) makes that easy to
 * rubber-stamp past.
 * <p>
 * The import itself - what is done with each commit, its LLM feature suggestion, its commit and
 * the state carried from one to the next - is a {@link GitImport}, free of any user interface;
 * this dialog shows it:
 * <ol>
 *     <li>{@link #step1()} - pick the local repo and a commit range from a table of its history,
 *     optionally opt out of LLM suggestions via a checkbox (disabled when no model is configured,
 *     checked by default otherwise), and whether each commit is reviewed.</li>
 *     <li>{@link #startImport(List, boolean, boolean)} starts a {@link GitImport} and
 *     {@link #processNextCommit(int)} asks it what to do with each commit in turn
 *     ({@link GitImport#next}):
 *     <ul>
 *         <li>{@link #suggestOneCommit(int)} - on a review commit, if suggestions weren't opted
 *         out of, asks the LLM for just that commit's suggested features in the background, one
 *         request per commit, so the user reviews each suggestion before the next one is asked for.</li>
 *         <li>{@link #showCommitReview(int, String, String, String)} - shows that single commit's
 *         message, the files it changed and an editable configuration, pre-filled with the
 *         suggestion merged onto whatever was imported so far, with live constraint-violation
 *         feedback. The user picks Import, Skip (leave this commit out, move on), or Stop (end the
 *         import here, keeping whatever was already imported). If importing the commit fails, the
 *         same screen comes back with the error and the configuration that was tried.</li>
 *         <li>{@link #autoImportOneCommit(int)} - on a commit not reviewed, does the same lookup
 *         unattended and imports with what it suggests, stopping for review only when it fails or
 *         is uncertain.</li>
 *         <li>{@link #importOneCommit(int, String)} - the actual import ({@link GitImport#commit}),
 *         in the background, reached from either path above, then on to the next commit.</li>
 *     </ul>
 *     </li>
 * </ol>
 */
public class ImportGitView extends OperationView implements EccoListener {

	// matches CommitView's own log/detail split - see that class's identical splitPane setup
	private static final double LOG_DIVIDER_POSITION = 0.65;

	// lets tests find the failure shown on a commit's review screen
	static final String IMPORT_FAILURE_LABEL_ID = "importFailure";
	static final String SUGGESTION_FAILURE_LABEL_ID = "suggestionFailure";
	/** What importing the tagged commits only does. */
	static final String TAGGED_ONLY_HINT = "Fewer and bigger steps: each release is one commit. Every commit is still suggested, so a feature "
			+ "any commit since the last release changed gets a new revision in the next one; the commits before the first tag are part of "
			+ LlmFeatureSuggestionClient.BASE + ". Commits after the last tag are left out.";
	/** What the "'" of a suggested feature means, in the review's configuration field. */
	static final String NEW_REVISION_HINT = "\nA feature with ' (A') gets a new revision: this commit changes it, so its code can be told from earlier versions.";
	// lets tests find the confidence shown on a commit's review screen
	static final String CONFIDENCE_LABEL_ID = "suggestionConfidence";

	private final EccoService service;
	private final GitHistoryReader gitHistoryReader = new GitHistoryReader();

	// what step1() chose: the clone, its tags, and whether only the tagged commits are imported
	private Path repoDir;
	private java.util.Map<String, List<String>> tags = java.util.Map.of();
	private boolean taggedOnly;
	// whether an unattended commit whose suggestion is uncertain (see SuggestionConfidence) stops for review
	boolean reviewUncertain = true;

	/** The import running, or last run - null before the first. Package-visible for testing. */
	GitImport run;
	// the suggestion request, off the FX thread - replaced by tests, which have no LLM
	Function<GitCommitInfo, GitImport.Suggested> suggester = commit -> this.run.requestSuggestion(commit);
	// set the moment this run ends (Back, Stop, finish, or a real failure) - a background
	// suggestion request (suggestOneCommit/autoImportOneCommit) started before that moment can
	// still be in flight, and this flag stops its late-arriving succeeded()/failed() from
	// repainting a screen the user has already left. FX-thread-only (Task callbacks run there),
	// so no synchronization needed - see navButtons(), finishImport(), reportImportFailure().
	boolean runEnded; // package-visible for testing
	// set true the first time step1()'s brief post-open "Browse..." disable finishes - see there.
	// step1() re-runs on every "Back" navigation within the SAME already-open, already-settled dialog
	// Stage, which doesn't need (or want) that one-time settling wait repeated each time.
	private boolean chooseRepoButtonSettled = false;

	private SplitPane splitPane;
	private CommitDetailView commitDetailView;
	TextArea logArea;


	public ImportGitView(EccoService service) {
		super();
		this.service = service;

		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);

		this.commitDetailView = new CommitDetailView(service);

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

		Button chooseRepoButton = new Button("Browse...");
		ProgressIndicator repoLoadingIndicator = new ProgressIndicator();
		repoLoadingIndicator.setMaxSize(20, 20);
		repoLoadingIndicator.setVisible(false);
		HBox chooseRepoBox = new HBox(6, chooseRepoButton, repoLoadingIndicator);
		chooseRepoBox.setAlignment(Pos.CENTER_LEFT);
		gridPane.add(chooseRepoBox, 2, row, 1, 1);
		row++;

		// this dialog's own Stage can still be settling into becoming the OS's key window right after
		// it's first shown - clicking "Browse..." during that window was observed (via a live native
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
					"Configure a model in Preferences → LLM to enable this."));
		}
		gridPane.add(suggestFeaturesCheckBox, 0, row, 3, 1);
		row++;

		// on or off: "review every N commits" imported the first N-1 unreviewed before the first review
		CheckBox reviewEachCheckBox = new CheckBox("Review each commit before it is imported - otherwise they are imported unattended, stopping only for a failure");
		reviewEachCheckBox.setSelected(true);
		gridPane.add(reviewEachCheckBox, 0, row, 3, 1);
		row++;

		CheckBox reviewUncertainCheckBox = new CheckBox("Also stop for review when a suggestion is uncertain (it disagrees with where the commit changed things)");
		reviewUncertainCheckBox.setSelected(true);
		reviewUncertainCheckBox.disableProperty().bind(suggestFeaturesCheckBox.selectedProperty().not());
		gridPane.add(reviewUncertainCheckBox, 0, row, 3, 1);
		row++;

		CheckBox taggedOnlyCheckBox = new CheckBox("Import the tagged commits only (releases) - the commits between are suggested unattended, and their features go into the next tagged one");
		taggedOnlyCheckBox.setTooltip(new Tooltip(TAGGED_ONLY_HINT));
		gridPane.add(taggedOnlyCheckBox, 0, row, 3, 1);
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
				Alert alert = new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.WARNING, "Not a git repository (no .git found):\n" + selectedPath);
				alert.showAndWait();
				return;
			}

			// listCommits() walks the whole first-parent history via JGit - synchronous on the FX
			// thread here used to freeze the entire app (no spinner, no way to tell it was even doing
			// anything) for any repo with a large-enough history or a slow disk; a background Task
			// keeps the window responsive and shows the small spinner next to the button instead.
			chooseRepoButton.setDisable(true);
			nextButton.setDisable(true);
			repoLoadingIndicator.setVisible(true);

			Task<List<GitCommitInfo>> loadCommitsTask = new Task<>() {
				@Override
				protected List<GitCommitInfo> call() {
					// listCommits() returns newest-first (as "git log --first-parent"); shown oldest-first
					// here instead, since that's both the order the user reads a history top-to-bottom
					// as "what happened" and the exact order the import itself has to process in
					List<GitCommitInfo> commits = ImportGitView.this.gitHistoryReader.listCommits(selectedPath);
					Collections.reverse(commits);
					ImportGitView.this.tags = ImportGitView.this.gitHistoryReader.listTags(selectedPath);
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
			String rangeProblem = rangeProblem(selectedIndices);
			if (rangeProblem != null) {
				this.showInputError(rangeProblem);
				return;
			}
			this.clearInputError();
			int minIndex = Collections.min(selectedIndices);
			int maxIndex = Collections.max(selectedIndices);
			// table is already oldest-first, matching the order the import itself needs
			List<GitCommitInfo> oldestFirst = new ArrayList<>(commitsTable.getItems().subList(minIndex, maxIndex + 1));
			this.taggedOnly = taggedOnlyCheckBox.isSelected();
			if (this.taggedOnly) {
				int last = GitImport.lastTagged(oldestFirst, this.tags);
				if (last < 0) {
					this.showInputError("The commits selected have no tag: select a range with at least one tagged commit, or import all commits.");
					return;
				}
				// those after the last tag would only be suggested, for no commit
				oldestFirst = new ArrayList<>(oldestFirst.subList(0, last + 1));
			}

			boolean useLlm = suggestFeaturesCheckBox.isSelected();
			if (useLlm && LlmPreferences.getModelName().isBlank()) {
				Alert alert = new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.WARNING,
						"No LLM model name is configured (Preferences → LLM), so feature suggestions " +
								"would fail for every commit. Skipping suggestions - you can still fill in each " +
								"commit's configuration by hand on the next screen.");
				alert.showAndWait();
				useLlm = false;
			}
			this.reviewUncertain = reviewUncertainCheckBox.isSelected();
			this.startImport(oldestFirst, useLlm, reviewEachCheckBox.isSelected());
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

		TableColumn<GitCommitInfo, String> tagCol = new TableColumn<>("Tags");
		tagCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.join(", ", this.tags.getOrDefault(param.getValue().getId(), List.of()))));

		commitsTable.getColumns().setAll(idCol, tagCol, messageCol, dateCol);
		TableColumns.defaultWidth(tagCol, 90);

		TableColumns.defaultWidth(idCol, 90);
		TableColumns.defaultWidth(dateCol, 150);
		TableColumns.growToFill(commitsTable, messageCol);

		return commitsTable;
	}

	/** A commit is being suggested or imported: closing the window asks whether to stop the run. */
	private void importRunning(int index) {
		int left = this.run.size() - index;
		this.running("the import", (left == 1 ? "1 commit is left" : left + " commits are left") + "; one already being written still finishes.", this::endRun);
	}

	/** Imports nothing more: what is being suggested or imported finishes, and goes no further. */
	private void endRun() {
		this.runEnded = true;
		this.service.removeListener(this);
	}

	/** The window was closed: the run ends with it - it went on importing with no window. */
	@Override
	protected void closed() {
		if (!this.runEnded)
			this.endRun();
	}

	/**
	 * Why [selectedIndices] (rows of the commit table) are no range to import, or null if they are
	 * one: everything from the first to the last selected was imported, also commits left out
	 * between them. Package-visible for testing.
	 */
	static String rangeProblem(List<Integer> selectedIndices) {
		if (selectedIndices.isEmpty())
			return "Select the commits to import.";
		int min = Collections.min(selectedIndices), max = Collections.max(selectedIndices);
		if (new java.util.HashSet<>(selectedIndices).size() == max - min + 1)
			return null;
		return "Select one continuous range of commits (Shift-click its last one): the commits between would be imported too.";
	}

	/**
	 * Starts the loop on a clone chosen without {@link #step1()}'s directory chooser. Package-visible for testing.
	 */
	void startImport(Path repoDir, List<GitCommitInfo> commitsOldestFirst, boolean useLlm, boolean reviewEach) {
		this.repoDir = repoDir;
		this.taggedOnly = false;
		this.startImport(commitsOldestFirst, useLlm, reviewEach);
	}

	/** As above, importing only the commits with one of [tags] (commit id to tag names). Package-visible for testing. */
	void startTaggedImport(Path repoDir, java.util.Map<String, List<String>> tags, List<GitCommitInfo> commitsOldestFirst, boolean useLlm, boolean reviewEach) {
		this.repoDir = repoDir;
		this.tags = tags;
		this.taggedOnly = true;
		this.startImport(commitsOldestFirst, useLlm, reviewEach);
	}

	/**
	 * Starts a new run over {@code commitsOldestFirst} - also after a "Back" from the first commit's
	 * screen (see {@link #navButtons()}), which leaves nothing of the run before.
	 *
	 * @param reviewEach whether each commit is reviewed before it is imported; otherwise they are
	 *                   imported unattended, stopping only for a failure or an uncertain suggestion
	 *                   (see {@link #autoImportOneCommit}). "Review every N commits" reviewed the
	 *                   Nth commit first - after importing the ones before it unreviewed.
	 */
	private void startImport(List<GitCommitInfo> commitsOldestFirst, boolean useLlm, boolean reviewEach) {
		this.run = new GitImport(this.service, this.gitHistoryReader, this.repoDir, this.tags, this.taggedOnly,
				commitsOldestFirst, useLlm, reviewEach, this.reviewUncertain, this::log);
		this.run.start();
		this.runEnded = false;

		this.logArea.clear();
		this.service.addListener(this);

		this.processNextCommit(0);
	}

	/** Appends [text] to the log - on the FX thread, from any thread. */
	private void log(String text) {
		if (Platform.isFxApplicationThread())
			this.logArea.appendText(text);
		else
			Platform.runLater(() -> this.logArea.appendText(text));
	}

	/**
	 * Advances the per-commit loop: aborts if this run has already ended (see {@link #runEnded}),
	 * otherwise does what the run says for the next commit ({@link GitImport#next}) - finishes,
	 * shows a review, imports, or asks for a suggestion first.
	 */
	private void processNextCommit(int index) {
		if (this.runEnded) {
			return;
		}
		this.act(this.run.next(index));
	}

	private void act(GitImport.Step step) {
		switch (step) {
			case GitImport.Finished finished -> this.finishImport(false);
			case GitImport.Review review -> this.showCommitReview(review.index(), review.configuration(), review.suggestionFailure(), null);
			case GitImport.Import anImport -> this.importOneCommit(anImport.index(), anImport.configuration());
			case GitImport.Unattended unattended -> this.autoImportOneCommit(unattended.index());
			case GitImport.SuggestForReview suggest -> this.suggestOneCommit(suggest.index());
		}
	}

	/** Shows that [text] is being done for a commit, while it is - with the log below. */
	private void showProgress(String text) {
		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setMaxSize(60, 60);
		Label progressLabel = new Label(text);
		progressLabel.setWrapText(true);
		VBox progressBox = new VBox(10, progressIndicator, progressLabel);
		progressBox.setAlignment(Pos.CENTER);
		progressBox.setPadding(new Insets(30));
		this.showStepContent(progressBox);
	}

	/**
	 * Asks the LLM for just ONE commit's suggested configuration (a background {@link Task}, since
	 * the network call must never block the FX thread) before showing that commit's review screen -
	 * unlike the previous batch design, later commits aren't even asked about until this one has
	 * been reviewed.
	 */
	private void suggestOneCommit(int index) {
		this.importRunning(index);
		GitCommitInfo commit = this.run.commit(index);

		this.navButtons();
		this.headerLabel.setText("Suggesting Feature (commit " + (index + 1) + " of " + this.run.size() + ")");
		this.rightButtons.getChildren().clear();
		this.showProgress("Asking the local LLM to suggest a feature configuration for commit " + commit.getShortId() + "...");
		this.growToFit(); // a size the user chose stays - fit() resized the window for every commit

		Task<GitImport.Suggested> suggestTask = new Task<>() {
			@Override
			protected GitImport.Suggested call() {
				return ImportGitView.this.suggester.apply(commit);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.act(ImportGitView.this.run.afterSuggestionForReview(index, this.getValue()));
			}

			@Override
			public void failed() {
				super.failed();
				if (ImportGitView.this.runEnded) {
					return;
				}
				// surfaced instead of silently landing on a review screen that looks like the LLM
				// just had no suggestion - the configuration can still be filled in by hand
				new ExceptionAlert(this.getException()).show();
				ImportGitView.this.act(ImportGitView.this.run.afterSuggestionForReviewFailed(index, this.getException()));
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Imports ONE commit WITHOUT stopping for review, unless its suggestion fails or is uncertain -
	 * still asking the LLM first when suggestions are on (so the running feature hierarchy stays
	 * accurate for later commits), but never showing an editable form or a constraint-violation
	 * confirmation - those only make sense with a human looking at the screen; a constraint
	 * violation is still logged by the import.
	 */
	private void autoImportOneCommit(int index) {
		this.importRunning(index);
		GitCommitInfo commit = this.run.commit(index);
		boolean folds = this.run.folds(commit);

		this.navButtons();
		this.headerLabel.setText((folds ? "Suggesting, for the next tagged commit" : "Auto-Importing") + " (commit " + (index + 1) + " of " + this.run.size() + ")");
		this.rightButtons.getChildren().clear();
		this.showProgress((folds ? "Suggesting features of commit " : "Importing commit ") + commit.getShortId() + " unattended...");
		this.growToFit(); // a size the user chose stays - fit() resized the window for every commit

		if (!this.run.useLlm()) {
			this.importOneCommit(index, this.run.unattendedConfiguration(index));
			return;
		}

		Task<GitImport.Suggested> suggestTask = new Task<>() {
			@Override
			protected GitImport.Suggested call() {
				return ImportGitView.this.suggester.apply(commit);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.act(ImportGitView.this.run.afterUnattendedSuggestion(index, this.getValue()));
			}

			@Override
			public void failed() {
				super.failed();
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.act(ImportGitView.this.run.afterUnattendedSuggestionFailed(index, this.getException()));
			}
		};
		new Thread(suggestTask).start();
	}

	/**
	 * Editable review of ONE commit's feature configuration string, pre-filled with
	 * {@code defaultConfig} - nothing is committed into ecco until this screen's Import button is
	 * clicked, and only for this one commit.
	 *
	 * @param suggestionFailureReason why the commit has no suggestion, if one was asked for and failed
	 * @param importFailure           why the last attempt to import this commit failed, or null on a first attempt
	 */
	private void showCommitReview(int index, String defaultConfig, String suggestionFailureReason, String importFailure) {
		// waiting for the user: closing the window just ends the run (see closed())
		this.notRunning();
		GitCommitInfo commit = this.run.commit(index);

		this.navButtons();
		boolean folds = this.run.folds(commit);
		this.headerLabel.setText("Review Commit (" + (index + 1) + " of " + this.run.size() + ")");

		Button skipButton = new Button("Skip");
		// a commit between tagged ones is not imported: its features go into the next tagged one
		Button importButton = new Button(folds ? "Use for Next Tag" : "Import");
		if (folds)
			skipButton.setTooltip(new Tooltip("Leave this commit's features out of the next tagged commit."));
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

		if (importFailure != null) {
			Label failureLabel = new Label("Importing this commit failed, so nothing of it was committed: " + importFailure +
					" - correct the configuration and Import again, or Skip the commit.");
			failureLabel.setId(IMPORT_FAILURE_LABEL_ID);
			failureLabel.setWrapText(true);
			failureLabel.setStyle("-fx-text-fill: firebrick;");
			gridPane.add(failureLabel, 0, row, 1, 1);
			row++;
		}

		if (suggestionFailureReason != null) {
			Label warningLabel = new Label("LLM feature suggestion failed for this commit, so Configuration is " +
					"blank/unchanged below - fill it in by hand. Reason: " + suggestionFailureReason);
			warningLabel.setId(SUGGESTION_FAILURE_LABEL_ID);
			warningLabel.setWrapText(true);
			warningLabel.setStyle("-fx-text-fill: #cc6600;");
			gridPane.add(warningLabel, 0, row, 1, 1);
			row++;
		}

		if (this.run.repositoryEmpty() && importFailure == null) {
			Label baseLabel = new Label("The first commit is " + LlmFeatureSuggestionClient.BASE + ": the code base as first imported, the root "
					+ "of the feature model. Later commits tell features apart in it - the LLM is not asked about this one.");
			baseLabel.setWrapText(true);
			baseLabel.setStyle("-fx-text-fill: #666666;");
			gridPane.add(baseLabel, 0, row, 1, 1);
			row++;
		}

		SuggestionConfidence confidence = this.run.pendingConfidence(commit);
		if (confidence != null) {
			boolean stopped = !this.run.reviewEach() && importFailure == null;
			Label confidenceLabel = new Label((stopped ? "Stopped for review - the suggestion is uncertain" : "Suggestion confidence " + Math.round(confidence.value() * 100) + "%")
					+ ": " + confidence.reason() + ".");
			confidenceLabel.setId(CONFIDENCE_LABEL_ID);
			confidenceLabel.setWrapText(true);
			confidenceLabel.setStyle(confidence.uncertain() ? "-fx-text-fill: #cc6600;" : "-fx-text-fill: #666666;");
			gridPane.add(confidenceLabel, 0, row, 1, 1);
			row++;
		}

		Label commitLabel = new Label(commit.getShortId() + " — " + commit.getMessage());
		commitLabel.setWrapText(true);
		commitLabel.setStyle("-fx-font-weight: bold;");
		gridPane.add(commitLabel, 0, row, 1, 1);
		row++;

		if (folds) {
			Label foldsLabel = new Label("Not imported: only the tagged commits are. The features of this one go into the next tagged commit, each it changes with a new revision.");
			foldsLabel.setWrapText(true);
			foldsLabel.setStyle("-fx-text-fill: #666666;");
			gridPane.add(foldsLabel, 0, row, 1, 1);
			row++;
		} else if (this.run.taggedOnly() && !this.run.tagsOf(commit).isEmpty()) {
			Label tagLabel = new Label("Tagged " + String.join(", ", this.run.tagsOf(commit))
					+ (this.run.revisedSinceImport().isEmpty() ? "" : " - revised since the last tagged commit: " + String.join(", ", this.run.revisedSinceImport())));
			tagLabel.setWrapText(true);
			tagLabel.setStyle("-fx-text-fill: #666666;");
			gridPane.add(tagLabel, 0, row, 1, 1);
			row++;
		}

		// what the commit changed, to judge the configuration by - the screen had only its message;
		// read in the background, as the diff is
		Label changedLabel = new Label("Changed files:");
		gridPane.add(changedLabel, 0, row, 1, 1);
		row++;
		this.changedFiles.getItems().clear();
		this.changedFiles.setPlaceholder(new Label("..."));
		this.changedFiles.setPrefHeight(110);
		gridPane.add(this.changedFiles, 0, row, 1, 1);
		row++;
		GitImport run = this.run;
		Task<List<String>> changedTask = new Task<>() {
			@Override
			protected List<String> call() {
				return run.changedFiles(commit);
			}
		};
		changedTask.setOnSucceeded(event -> {
			this.changedFiles.getItems().setAll(changedTask.getValue());
			changedLabel.setText("Changed files (" + changedTask.getValue().size() + "):");
			this.changedFiles.setPlaceholder(new Label("none"));
		});
		changedTask.setOnFailed(event -> this.changedFiles.setPlaceholder(new Label("could not be read: " + changedTask.getException().getMessage())));
		Thread changedThread = new Thread(changedTask);
		changedThread.setDaemon(true);
		changedThread.start();

		Label configLabel = new Label("Configuration:");
		gridPane.add(configLabel, 0, row, 1, 1);
		row++;

		TextField configField = new TextField(defaultConfig == null ? "" : defaultConfig);
		configField.setPromptText(COMMIT_CONFIGURATION_HINT + BLANK_READS_CONFIG_FILE);
		configField.setTooltip(new Tooltip(COMMIT_CONFIGURATION_HINT + BLANK_READS_CONFIG_FILE + NEW_REVISION_HINT));
		focusWhenShown(configField);
		gridPane.add(at.jku.isse.ecco.gui.io.ConfigurationPickerDialog.withPicker(configField, this.service), 0, row, 1, 1);
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
						? "" : ConstraintWarnings.describe(this.service, configurationText);
				Platform.runLater(() -> constraintWarningLabel.setText(description));
			}).start();
		});
		configField.textProperty().addListener((observable, oldValue, newValue) -> debounce.playFromStart());
		debounce.playFromStart();

		skipButton.setOnAction(event -> {
			this.run.skip(index);
			this.processNextCommit(index + 1);
		});

		importButton.setOnAction(event -> {
			String warning = constraintWarningLabel.getText();
			if (!folds && warning != null && !warning.isEmpty()) {
				Alert alert = new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.CONFIRMATION, warning + "\n\nDo you want to import anyway?");
				alert.setHeaderText("Constraint violation");
				Optional<ButtonType> result = alert.showAndWait();
				if (result.isEmpty() || result.get() != ButtonType.OK) return;
			}
			this.importOneCommit(index, configField.getText());
		});

		this.growToFit(); // a size the user chose stays - fit() resized the window for every commit
	}

	/**
	 * The actual import of ONE commit ({@link GitImport#commit}), in the background, then on to
	 * the next one on success; a failure brings the commit's review back with it, as the commit
	 * rolled back and can be corrected. Reached from both {@link #showCommitReview} (Import button)
	 * and {@link #autoImportOneCommit} (unattended).
	 */
	private void importOneCommit(int index, String configurationText) {
		this.importRunning(index);
		GitCommitInfo commit = this.run.commit(index);
		if (this.run.folds(commit)) {
			this.foldOneCommit(index, configurationText);
			return;
		}

		this.leftButtons.getChildren().clear();
		this.rightButtons.getChildren().clear();
		this.headerLabel.setText("Importing (commit " + (index + 1) + " of " + this.run.size() + ")");
		this.showProgress("Importing commit " + commit.getShortId() + "...");
		this.growToFit(); // a size the user chose stays - fit() resized the window for every commit

		GitImport run = this.run;
		Task<GitImport.Imported> importTask = new Task<>() {
			@Override
			public GitImport.Imported call() throws IOException {
				return run.commit(index, configurationText);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				run.imported(index, configurationText, this.getValue());
				if (ImportGitView.this.runEnded) {
					return;
				}
				ImportGitView.this.processNextCommit(index + 1);
			}

			@Override
			public void cancelled() {
				super.cancelled();
				ImportGitView.this.reportImportFailure(this.getException());
			}

			@Override
			public void failed() {
				super.failed();
				// commit() rolls back on failure, so the commit can be corrected and tried again
				String reason = GitImport.describeFailure(this.getException());
				ImportGitView.this.logArea.appendText("*** IMPORT FAILED for " + commit.getShortId() + ": " + reason + System.lineSeparator());
				ImportGitView.this.showCommitReview(index, configurationText, null, reason);
			}
		};
		new Thread(importTask).start();
	}

	/**
	 * A commit between tagged ones, in an import of tagged commits only: not imported, but its
	 * [configurationText] is kept for the next tagged commit ({@link GitImport#foldChanges}, then
	 * {@link GitImport#folded}).
	 */
	private void foldOneCommit(int index, String configurationText) {
		GitImport run = this.run;
		Task<ImportedFeatureTraces.Changes> changesTask = new Task<>() {
			@Override
			protected ImportedFeatureTraces.Changes call() {
				return run.foldChanges(index, configurationText);
			}

			@Override
			protected void succeeded() {
				ImportGitView.this.folded(index, configurationText, this.getValue());
			}

			@Override
			protected void failed() {
				ImportGitView.this.folded(index, configurationText, null);
			}
		};
		Thread thread = new Thread(changesTask);
		thread.setDaemon(true);
		thread.start();
	}

	private void folded(int index, String configurationText, ImportedFeatureTraces.Changes changes) {
		if (this.runEnded)
			return;
		this.run.folded(index, configurationText, changes);
		this.processNextCommit(index + 1);
	}

	/**
	 * Left-hand nav button for the currently-shown screen: "Back" to {@link #step1()} while nothing
	 * has been imported yet in this run (nothing to lose), or "Stop" once at least one commit may
	 * already be imported (those stay imported either way - see {@link #finishImport(boolean)}
	 * - so going back to repository selection would be misleading). Both set {@link #runEnded}
	 * immediately, even though a background suggestion request ({@link #suggestOneCommit}/
	 * {@link #autoImportOneCommit}) may still be in flight for the screen being left - that
	 * request's late-arriving {@code succeeded()}/{@code failed()} checks the same flag and no-ops
	 * instead of repainting a screen the user has already left.
	 */
	private void navButtons() {
		if (this.run.importedCount() == 0) {
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
			stopButton.setOnAction(event -> this.finishImport(true));
			this.leftButtons.getChildren().setAll(stopButton);
		}
	}

	/**
	 * Shows {@code stepContent} (the current step's form/progress UI) in the top half of
	 * {@link #splitPane} with the running import log always visible below it, instead of the log
	 * only appearing once the whole run ends - lets the user watch each commit (including its
	 * suggested/applied configuration string) go by live, which matters most during an unattended
	 * auto-import stretch (see {@link #autoImportOneCommit}) where nothing else on screen shows
	 * what's happening commit by commit.
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
	private void finishImport(boolean stoppedEarly) {
		this.runEnded = true;
		this.service.removeListener(this);

		if (stoppedEarly) {
			this.logArea.appendText(String.format("Stopped: imported %d of %d commit(s).%n", this.run.importedCount(), this.run.toImport()));
		}

		this.notRunning();
		this.commitDetailView.showCommit(this.run.lastCommit());
		this.splitPane.getItems().setAll(this.logArea, this.commitDetailView);
		this.splitPane.setDividerPositions(LOG_DIVIDER_POSITION);
		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		// one summary above the log - there were up to two message boxes, possibly taller than the screen
		this.summaryArea.setText(this.run.summary(stoppedEarly));
		this.summaryArea.setEditable(false);
		this.summaryArea.setWrapText(true);
		this.summaryArea.setPrefRowCount(Math.min(8, (int) this.summaryArea.getText().lines().count() + 1));
		VBox.setVgrow(this.splitPane, javafx.scene.layout.Priority.ALWAYS);
		VBox result = new VBox(8, this.summaryArea, this.splitPane);
		result.setPadding(new Insets(10, 0, 0, 0));
		VBox.setMargin(this.summaryArea, new Insets(0, 10, 0, 10));
		this.setCenter(result);
		this.showSuccessHeader();
		this.growToFit();
	}

	private final javafx.scene.control.TextArea summaryArea = new javafx.scene.control.TextArea();

	/** The files the reviewed commit changed. */
	private final javafx.scene.control.ListView<String> changedFiles = new javafx.scene.control.ListView<>();

	/** The changed files the review screen lists. Package-visible for testing. */
	List<String> changedFilesShown() {
		return List.copyOf(this.changedFiles.getItems());
	}

	/** The summary a finished run shows; empty before. Package-visible for testing. */
	String summaryText() {
		return this.summaryArea.getText();
	}

	/**
	 * The repository closed - also to open another one - stops the run: it went on, committing the
	 * remaining commits into whatever repository was open by then.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		GitImport run = this.run;
		if (this.runEnded || run == null || run.runRepositoryDir() == null)
			return;
		if (!service.isInitialized() || !run.runRepositoryDir().equals(service.getRepositoryDir()))
			Platform.runLater(() -> {
				if (this.runEnded)
					return;
				String message = "The repository was closed during the import: it stopped after " + run.importedCount()
						+ (run.importedCount() == 1 ? " commit." : " commits.");
				this.logArea.appendText("*** " + message + System.lineSeparator());
				this.reportImportFailure(new at.jku.isse.ecco.EccoException(message));
			});
	}

	/** Ends the per-commit loop on a real failure (as opposed to {@link #finishImport}'s success/stop). */
	private void reportImportFailure(Throwable exception) {
		this.runEnded = true;
		this.service.removeListener(this);
		this.commitDetailView.showCommit(null);
		this.splitPane.getItems().setAll(this.logArea, new at.jku.isse.ecco.gui.ErrorDetails(exception));
		this.splitPane.setDividerPositions(LOG_DIVIDER_POSITION);
		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);
		this.showErrorHeader();
		this.fit();
	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		String plugin = PluginNames.shortName(reader.getPluginId());
		Platform.runLater(() -> this.logArea.appendText(String.format("Read %s using (%s)%n", file, plugin)));
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		String plugin = PluginNames.shortName(writer.getPluginId());
		Platform.runLater(() -> this.logArea.appendText(String.format("Wrote %s using (%s)%n", file, plugin)));
	}
}
