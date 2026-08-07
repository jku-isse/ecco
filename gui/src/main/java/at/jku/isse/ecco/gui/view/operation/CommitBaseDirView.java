package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
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

		this.commitDetailView = new CommitDetailView();

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

		Label folderLabel = new Label("Folder: ");
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
		TextField configurationField = new TextField(this.lastCommitConfigurationString());
		gridPane.add(configurationField, 1, row, 1, 1);
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
	 * practice for a field the user is about to re-commit from anyway.
	 */
	private String lastCommitConfigurationString() {
		List<Commit> commits = new ArrayList<>(this.service.getCommits());
		if (commits.isEmpty()) return "";
		Configuration configuration = commits.get(commits.size() - 1).getConfiguration();
		if (configuration == null) return "";
		return Arrays.stream(configuration.getFeatureRevisions())
				.map(featureRevision -> featureRevision.getFeature().getName())
				.collect(Collectors.joining(", "));
	}

	/**
	 * Wires the Commit button: transitions to the "committing" step ({@link #step2()}), then runs the
	 * actual commit of the current base directory on a background {@code Task}, logging progress and
	 * any constraint violations as they happen -- the single-folder analog of
	 * {@link CommitView#wireCommitButton}.
	 */
	private void wireCommitButton(Button commitButton, TextField commitMessageField, TextField configurationField) {
		commitButton.setOnAction(event -> {
			this.step2();

			String commitMessage = commitMessageField.getText();
			String configurationString = configurationField.getText();

			this.logArea.clear();
			this.service.addListener(this);

			Task<Commit> commitTask = new Task<Commit>() {
				@Override
				public Commit call() {
					Commit commit = (configurationString != null && !configurationString.isBlank())
							? CommitBaseDirView.this.service.commit(commitMessage, configurationString)
							: CommitBaseDirView.this.service.commit(commitMessage);

					List<String> constraintViolations = CommitBaseDirView.this.service.checkConstraintViolations(commit.getConfiguration());
					if (!constraintViolations.isEmpty()) {
						Platform.runLater(() -> {
							for (String violation : constraintViolations)
								CommitBaseDirView.this.logArea.appendText("CONSTRAINT: " + violation + System.lineSeparator());
							Alert alert = new Alert(Alert.AlertType.WARNING,
									"The committed configuration violates accepted constraint(s):\n" + String.join("\n", constraintViolations));
							alert.showAndWait();
						});
					}
					return commit;
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
				public void cancelled() {
					super.cancelled();
					CommitBaseDirView.this.service.removeListener(CommitBaseDirView.this);
					CommitBaseDirView.this.commitDetailView.showCommit(null);
					CommitBaseDirView.this.splitPane.getItems().setAll(CommitBaseDirView.this.logArea, new ExceptionTextArea(this.getException()));
					CommitBaseDirView.this.showErrorHeader();
				}

				@Override
				public void failed() {
					super.failed();
					CommitBaseDirView.this.service.removeListener(CommitBaseDirView.this);
					CommitBaseDirView.this.commitDetailView.showCommit(null);
					CommitBaseDirView.this.splitPane.getItems().setAll(CommitBaseDirView.this.logArea, new ExceptionTextArea(this.getException()));
					CommitBaseDirView.this.showErrorHeader();
				}
			};
			new Thread(commitTask).start();
		});
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

}
