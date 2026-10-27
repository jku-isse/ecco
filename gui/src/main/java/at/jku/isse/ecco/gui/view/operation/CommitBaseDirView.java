package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.gui.PluginNames;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ConstraintWarnings;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.CommitCancelledException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Commits the current base directory ({@link EccoService#getBaseDir()}) with a commit message and
 * an (optional) configuration -- the single-directory counterpart to {@link CommitView}, which
 * instead lets the user assemble and commit several folders (picked individually or bulk-added from
 * a parent folder) in one operation. Split out of what used to be one "Commit..." action so the
 * common case (commit what's already checked out here) doesn't require building and clearing a
 * one-row folder table first.
 */
public class CommitBaseDirView extends OperationView implements EccoListener {

	private final EccoService service;

	private final SplitPane splitPane;
	private final CommitDetailView commitDetailView;
	private final TextArea logArea;

	public CommitBaseDirView(EccoService service) {
		super();
		this.service = service;

		this.splitPane = new SplitPane();
		this.splitPane.setOrientation(Orientation.VERTICAL);

		this.commitDetailView = new CommitDetailView(service);

		this.logArea = new TextArea();
		this.logArea.setEditable(false);
		this.logArea.setWrapText(false);

		this.splitPane.getItems().add(this.logArea);

		this.step1();
	}

	/**
	 * Folder (fixed to the current base directory), commit message, and configuration.
	 */
	private void step1() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Commit");

		Button commitButton = new Button("Commit");
		this.rightButtons.getChildren().setAll(commitButton);

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

		Path baseDir = this.service.getBaseDir();

		Label folderLabel = new Label("Variant Folder: ");
		gridPane.add(folderLabel, 0, row, 1, 1);
		TextField folderField = new TextField(String.valueOf(baseDir));
		folderField.setEditable(false);
		gridPane.add(folderField, 1, row, 1, 1);
		row++;

		Label commitMessageLabel = new Label("Commit Message: ");
		gridPane.add(commitMessageLabel, 0, row, 1, 1);
		TextField commitMessageField = new TextField();
		gridPane.add(commitMessageField, 1, row, 1, 1);
		row++;

		Label configurationLabel = new Label("Configuration: ");
		gridPane.add(configurationLabel, 0, row, 1, 1);
		TextField configurationField = new TextField();
		// read in the background, holding the service's lock: on the FX thread (Cmd+K, the ribbon) the
		// window froze behind a commit or checkout running meanwhile; filled in unless typed in by then
		at.jku.isse.ecco.gui.RepositoryAccess.read(this.service, "Last commit's configuration", CommitBaseDirView::lastCommitConfigurationString,
				configuration -> {
					if (configurationField.getText().isEmpty())
						configurationField.setText(configuration);
				}, failure -> {
					// only a default: the field stays empty
				});
		configurationField.setPromptText(COMMIT_CONFIGURATION_HINT + BLANK_READS_CONFIG_FILE);
		// it was filled in without a word: check it is this variant's
		configurationField.setTooltip(new Tooltip("Filled with the previous commit's configuration - make it this variant's.\n"
				+ COMMIT_CONFIGURATION_HINT + BLANK_READS_CONFIG_FILE));
		configurationLabel.setLabelFor(configurationField);
		gridPane.add(at.jku.isse.ecco.gui.io.ConfigurationPickerDialog.withPicker(configurationField, this.service), 1, row, 1, 1);
		row++;

		this.wireCommitButton(commitButton, commitMessageField, configurationField);

		this.fit();

		Platform.runLater(commitMessageField::requestFocus);
	}

	/**
	 * The configuration of the most recently made commit, as a sensible default for what's about to
	 * be committed again -- consecutive commits to the same base directory usually reuse the same
	 * configuration. {@link EccoService#getCommits()} is insertion-ordered (oldest first), so the
	 * last element is the most recent one. Falls back to an empty string for a brand-new repository
	 * with no commits yet.
	 *
	 * <p>Built from bare feature names ("bla"), not {@link Configuration#toString()} ("bla.5017283")
	 * -- that form is the git-short-hash-style {@code feature.revisionId} used everywhere a
	 * configuration needs to round-trip an exact revision (see {@code FeatureRevision#
	 * getFeatureRevisionString}), which is needlessly precise for a default suggestion here: per
	 * {@code EccoService#parseConfigurationString}, a bare feature name already resolves to that
	 * feature's latest revision, which is what "the configuration of the last commit" means in
	 * practice for a field the user is about to re-commit from anyway. Holding the service's lock.
	 */
	private static String lastCommitConfigurationString(EccoService service) {
		List<Commit> commits = new ArrayList<>(service.getCommits());
		if (commits.isEmpty()) return "";
		Configuration configuration = commits.get(commits.size() - 1).getConfiguration();
		if (configuration == null) return "";
		return Arrays.stream(configuration.getFeatureRevisions())
				.map(featureRevision -> featureRevision.getFeature().getName())
				.collect(Collectors.joining(", "));
	}

	/**
	 * Wires the Commit button: asks before committing a configuration that violates accepted
	 * constraints, transitions to the "committing" step ({@link #step2}), then runs the actual commit
	 * of the current base directory on a background {@code Task}, logging progress -- the
	 * single-folder analog of {@link CommitView#wireCommitButton}. Cancel asks the service to stop the
	 * commit ({@link EccoService#cancelCommit()}), which it does unless it is already storing.
	 *
	 * <p>Violations are checked before, not after, the commit: only exception-free rules count
	 * ({@code ConstraintViolationChecker}), and the committed configuration is the exception to every
	 * rule it violates - checked afterwards, it never violates anything.
	 */
	private void wireCommitButton(Button commitButton, TextField commitMessageField, TextField configurationField) {
		commitButton.setOnAction(event -> {
			String commitMessage = commitMessageField.getText();
			String configurationString = configurationField.getText();

			// checked in the background: on the FX thread it waited for a commit running meanwhile
			// (an import's), which holds the service's lock - the window froze
			commitButton.setDisable(true);
			Thread check = new Thread(() -> {
				String warning;
				try {
					warning = ConstraintWarnings.describe(this.service, configurationString != null && !configurationString.isBlank()
							? configurationString : this.service.getConfigStringFromFile(this.service.getBaseDir()));
				} catch (RuntimeException e) {
					warning = "";
				}
				String found = warning;
				Platform.runLater(() -> {
					commitButton.setDisable(false);
					if (!found.isEmpty() && !this.confirmCommitAnyway(found))
						return;
					this.startCommit(commitMessage, configurationString);
				});
			}, "constraint check");
			check.setDaemon(true);
			check.start();
		});
	}

	/** Commits, after the constraints were checked. */
	private void startCommit(String commitMessage, String configurationString) {
		{
			this.step2(() -> this.service.cancelCommit());

			this.logArea.clear();
			this.service.addListener(this);

			Task<Commit> commitTask = new Task<Commit>() {
				@Override
				public Commit call() {
					return (configurationString != null && !configurationString.isBlank())
							? CommitBaseDirView.this.service.commit(commitMessage, configurationString)
							: CommitBaseDirView.this.service.commit(commitMessage);
				}

				@Override
				public void succeeded() {
					super.succeeded();
					CommitBaseDirView.this.service.removeListener(CommitBaseDirView.this);
					CommitBaseDirView.this.commitDetailView.showCommit(this.getValue());
					CommitBaseDirView.this.splitPane.getItems().setAll(CommitBaseDirView.this.logArea, CommitBaseDirView.this.commitDetailView);
					CommitBaseDirView.this.showSuccessHeader();
				}

				@Override
				public void failed() {
					super.failed();
					CommitBaseDirView.this.service.removeListener(CommitBaseDirView.this);
					CommitBaseDirView.this.commitDetailView.showCommit(null);
					if (this.getException() instanceof CommitCancelledException) {
						CommitBaseDirView.this.splitPane.getItems().setAll(CommitBaseDirView.this.logArea);
						CommitBaseDirView.this.showCancelledHeader("Cancelled: nothing was committed.");
					} else {
						CommitBaseDirView.this.splitPane.getItems().setAll(CommitBaseDirView.this.logArea, new at.jku.isse.ecco.gui.ErrorDetails(this.getException()));
						CommitBaseDirView.this.showErrorHeader();
					}
				}
			};
			this.track(commitTask, "The commit");
			new Thread(commitTask).start();
		}
	}

	/**
	 * Asks whether to commit despite the given constraint violations. Package-visible for testing (a
	 * test stands in for the user's answer).
	 */
	boolean confirmCommitAnyway(String violations) {
		Alert alert = new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.CONFIRMATION, violations + "\n\nDo you want to commit anyway?");
		alert.setHeaderText("Constraint violation");
		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
	}

	/**
	 * Log table and success or error; Cancel runs {@code cancel} once.
	 */
	private void step2(Runnable cancel) {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> {
			cancelButton.setDisable(true);
			this.headerLabel.setText("Cancelling ...");
			cancel.run();
		});
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Committing ...");

		this.rightButtons.getChildren().clear();

		this.splitPane.setPadding(new Insets(0, 10, 10, 10));
		this.setCenter(this.splitPane);

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
