package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pull and Push - the same three steps: the remote, the feature revisions to leave out, the
 * transfer. They were two copies of one class that had drifted apart: in Push the typed
 * deselection did not reach the tree, and going back rebuilt it while keeping the text.
 */
public abstract class TransferView extends OperationView {

	protected final EccoService service;

	private final Button transferButton;
	private final ComboBox<Remote> remoteComboBox = new ComboBox<>();
	private final TextField deselectionTextField = new TextField();
	private final ProgressBar progressBar = new ProgressBar();
	private final Label progressLabel = new Label();

	/** The tree's revision items by their text in the deselection field, for the field to tick and untick them. */
	private final Map<String, CheckBoxTreeItem<FeatureInfo>> revisionItems = new LinkedHashMap<>();
	/** True while the tree and the field update each other, so neither echoes back. */
	private boolean syncing;

	protected TransferView(EccoService service) {
		super();
		this.service = service;
		this.transferButton = new Button(this.verb() + " All");

		this.transferButton.setOnAction(event -> this.transfer());
		this.transferButton.disableProperty().bind(this.remoteComboBox.getSelectionModel().selectedItemProperty().isNull());

		// the field writes the tree too - only the tree wrote the field
		this.deselectionTextField.setPromptText("Feature revisions to leave out, comma-separated - or untick them below");
		this.deselectionTextField.textProperty().addListener((observable, oldValue, newValue) -> this.tickFromField());

		// filled once here, not in stepRemote(): going back to that step appended every remote again
		fillRemotes(this.remoteComboBox, this.service.getRemotes());

		this.stepRemote();
	}

	/** "Pull", "Push". */
	protected abstract String verb();

	/** "from", "to". */
	protected abstract String preposition();

	/** Does it, off the FX thread: [deselection] left out. */
	protected abstract void transfer(String remote, String deselection);

	/** The features to choose from: the remote's (Pull) or this repository's (Push) - off the FX thread. */
	protected abstract Collection<? extends Feature> features(String remote);

	/** The features to show at once, while {@link #features} reads them: Pull's remote as last fetched. */
	protected Collection<? extends Feature> featuresNow(String remote) {
		return List.of();
	}

	/** The feature revisions the transfer adds where they go - read before and after it, off the FX thread. */
	protected abstract Set<String> revisionsAtTarget(String remote);

	private void transfer() {
		this.setDisable(true);
		Remote remote = this.remoteComboBox.getValue();
		// read on the FX thread, not in call()
		String deselection = this.deselectionTextField.getText();

		Task<List<String>> task = new Task<>() {
			@Override
			public List<String> call() {
				updateMessage("Fetching " + remote.getName() + "...");
				updateProgress(-1, 1);
				TransferView.this.service.fetch(remote.getName());
				Set<String> before = TransferView.this.revisionsAtTarget(remote.getName());
				updateMessage(TransferView.this.verb() + "ing " + TransferView.this.preposition() + " " + remote.getName() + "...");
				TransferView.this.transfer(remote.getName(), deselection);
				Set<String> after = TransferView.this.revisionsAtTarget(remote.getName());
				after.removeAll(before);
				return after.stream().sorted().toList();
			}

			@Override
			public void succeeded() {
				super.succeeded();
				TransferView.this.setDisable(false);
				TransferView.this.stepSuccess(transferred(TransferView.this.verb(), TransferView.this.preposition(), remote.getName(), this.getValue()));
			}

			@Override
			public void failed() {
				super.failed();
				TransferView.this.setDisable(false);
				TransferView.this.stepError("Error during " + TransferView.this.verb().toLowerCase() + " operation.", this.getException());
			}
		};
		this.progressBar.progressProperty().bind(task.progressProperty());
		this.progressLabel.textProperty().bind(task.messageProperty());
		this.stepProgress();
		this.track(task, "The " + this.verb().toLowerCase());
		new Thread(task).start();
	}

	/**
	 * "Pulled from origin: 2 new feature revisions (A.3f2a9c1, B.07e1d2a)." - what came over; it
	 * said "Pull operation was successful." Package-visible for testing.
	 */
	static String transferred(String verb, String preposition, String remote, List<String> added) {
		String done = verb + "ed";
		if (added.isEmpty())
			return done + " " + preposition + " " + remote + ": nothing new.";
		List<String> shown = added.subList(0, Math.min(5, added.size()));
		return done + " " + preposition + " " + remote + ": " + added.size() + " new feature revision" + (added.size() == 1 ? "" : "s")
				+ " (" + String.join(", ", shown) + (added.size() > shown.size() ? ", ..." : "") + ").";
	}

	/** The feature revisions of [features], as the deselection field writes them. */
	protected static Set<String> revisions(Collection<? extends Feature> features) {
		return features.stream().flatMap(feature -> feature.getRevisions().stream()).map(FeatureRevision::toString)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private void stepRemote() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Remote");

		Button selectButton = new Button("Choose Features...");
		selectButton.setTooltip(new Tooltip("Leave feature revisions out of the " + this.verb().toLowerCase()));
		selectButton.setOnAction(event -> this.stepDeselect());
		selectButton.disableProperty().bind(this.remoteComboBox.getSelectionModel().selectedItemProperty().isNull());
		// everything, or (after choosing) what is left
		this.transferButton.setText(this.verb() + " All");
		this.rightButtons.getChildren().setAll(selectButton, this.transferButton);

		GridPane gridPane = form();
		this.setCenter(gridPane);
		Label remoteLabel = new Label("Remote: ");
		gridPane.add(remoteLabel, 0, 0, 1, 1);
		remoteLabel.setLabelFor(this.remoteComboBox);
		gridPane.add(this.remoteComboBox, 1, 0, 1, 1);
		focusWhenShown(this.remoteComboBox);

		this.fit();
	}

	private void stepDeselect() {
		Button backButton = new Button("Back");
		backButton.setOnAction(event -> this.stepRemote());
		this.leftButtons.getChildren().setAll(backButton);

		this.headerLabel.setText("Feature Revisions");

		this.transferButton.setText(this.verb());
		this.rightButtons.getChildren().setAll(this.transferButton);

		GridPane gridPane = form();
		RowConstraints fieldRow = new RowConstraints();
		RowConstraints treeRow = new RowConstraints();
		treeRow.setVgrow(Priority.ALWAYS);
		treeRow.setFillHeight(true);
		gridPane.getRowConstraints().addAll(fieldRow, treeRow);
		this.setCenter(gridPane);

		Label selectionLabel = new Label("Leave out: ");
		gridPane.add(selectionLabel, 0, 0, 1, 1);
		selectionLabel.setLabelFor(this.deselectionTextField);
		gridPane.add(this.deselectionTextField, 1, 0, 1, 1);

		TreeView<FeatureInfo> tree = new TreeView<>();
		tree.setCellFactory(CheckBoxTreeCell.forTreeView());
		// no "FEATURE" root: the features are the top
		tree.setShowRoot(false);
		tree.setEditable(false);

		TitledPane titledPane = new TitledPane("Features - ticked ones are " + (this.verb().equals("Pull") ? "pulled" : "pushed"), tree);
		titledPane.setCollapsible(false);
		titledPane.setMaxWidth(Double.MAX_VALUE);
		titledPane.setMaxHeight(Double.MAX_VALUE);
		gridPane.add(titledPane, 0, 1, 2, 1);

		Remote remote = this.remoteComboBox.getValue();
		String title = titledPane.getText();
		this.fillTree(tree, this.featuresNow(remote.getName()));
		titledPane.setText(title + " (reading ...)");
		Task<Collection<? extends Feature>> featuresTask = new Task<>() {
			@Override
			public Collection<? extends Feature> call() {
				return TransferView.this.features(remote.getName());
			}

			@Override
			public void succeeded() {
				super.succeeded();
				titledPane.setText(title);
				TransferView.this.fillTree(tree, this.getValue());
			}

			@Override
			public void failed() {
				super.failed();
				titledPane.setText(title + " (could not be read: " + this.getException().getMessage() + ")");
			}
		};
		Thread thread = new Thread(featuresTask);
		thread.setDaemon(true);
		thread.start();

		this.fit();
	}

	/**
	 * Shows [features] with their revisions, each ticked unless the deselection field names it - so the
	 * tree can be built again without losing what was left out. Unticking and ticking writes the field.
	 */
	private void fillTree(TreeView<FeatureInfo> tree, Collection<? extends Feature> features) {
		this.revisionItems.clear();
		CheckBoxTreeItem<FeatureInfo> root = new CheckBoxTreeItem<>(new FeatureInfo(null, null));
		List<Feature> sorted = new ArrayList<>(features);
		sorted.sort(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER));
		for (Feature feature : sorted) {
			CheckBoxTreeItem<FeatureInfo> featureItem = new CheckBoxTreeItem<>(new FeatureInfo(feature, null));
			for (FeatureRevision revision : feature.getRevisions()) {
				CheckBoxTreeItem<FeatureInfo> revisionItem = new CheckBoxTreeItem<>(new FeatureInfo(feature, revision));
				featureItem.getChildren().add(revisionItem);
				this.revisionItems.put(revision.toString(), revisionItem);
			}
			featureItem.setExpanded(true);
			root.getChildren().add(featureItem);
		}
		this.syncing = true;
		root.setSelected(true);
		this.syncing = false;
		for (CheckBoxTreeItem<FeatureInfo> item : this.revisionItems.values())
			item.selectedProperty().addListener((observable, oldValue, newValue) -> this.writeField());
		tree.setRoot(root);
		root.setExpanded(true);
		this.tickFromField();
	}

	/** The field from the tree: the revisions not ticked. */
	private void writeField() {
		if (this.syncing) return;
		Set<String> deselected = new LinkedHashSet<>(this.typed());
		for (Map.Entry<String, CheckBoxTreeItem<FeatureInfo>> item : this.revisionItems.entrySet()) {
			if (item.getValue().isSelected())
				deselected.remove(item.getKey());
			else
				deselected.add(item.getKey());
		}
		this.syncing = true;
		this.deselectionTextField.setText(String.join(", ", deselected));
		this.syncing = false;
	}

	/** The tree from the field: what it names is unticked, the rest ticked. */
	private void tickFromField() {
		if (this.syncing) return;
		Set<String> deselected = this.typed();
		this.syncing = true;
		for (Map.Entry<String, CheckBoxTreeItem<FeatureInfo>> item : this.revisionItems.entrySet())
			item.getValue().setSelected(!deselected.contains(item.getKey()));
		this.syncing = false;
	}

	private Set<String> typed() {
		return Arrays.stream(this.deselectionTextField.getText().split(",")).map(String::trim).filter(text -> !text.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void stepProgress() {
		this.leftButtons.getChildren().clear();
		this.headerLabel.setText(this.verb());
		this.rightButtons.getChildren().clear();

		this.progressBar.setMaxWidth(Double.MAX_VALUE);
		// what it is doing - the bar alone said nothing
		VBox box = new VBox(8, this.progressLabel, this.progressBar);
		box.setPadding(new Insets(10));
		this.setCenter(box);

		this.fit();
	}

	private static GridPane form() {
		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));
		ColumnConstraints labels = new ColumnConstraints();
		labels.setMinWidth(GridPane.USE_PREF_SIZE);
		ColumnConstraints fields = new ColumnConstraints();
		fields.setFillWidth(true);
		fields.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(labels, fields);
		return gridPane;
	}

	/** A feature (revision null) or one of its revisions in the tree. */
	public static class FeatureInfo {
		private final Feature feature;
		private final FeatureRevision featureVersion;

		private FeatureInfo(Feature feature, FeatureRevision featureVersion) {
			this.feature = feature;
			this.featureVersion = featureVersion;
		}

		public Feature getFeature() {
			return this.feature;
		}

		public FeatureRevision getFeatureVersion() {
			return this.featureVersion;
		}

		/** "A", and "A.3f2a9c1" for a revision - it said "FEATURE", "<id> - A" and the bare revision id. */
		@Override
		public String toString() {
			if (this.feature == null)
				return "";
			return this.featureVersion == null ? this.feature.getName() : this.featureVersion.toString();
		}
	}
}
