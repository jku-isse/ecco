package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PullView extends OperationView {

	private EccoService service;


	private Button pullButton;
	private ComboBox<Remote> remoteComboBox;
	private TextField deselectionTextField;
	private ProgressBar progressBar;


	public PullView(EccoService service) {
		super();
		this.service = service;

		this.pullButton = new Button("Pull All");
		this.remoteComboBox = new ComboBox<>();
		this.deselectionTextField = new TextField();
		this.progressBar = new ProgressBar();

		this.pullButton.setOnAction(event -> {
			Remote remote = this.remoteComboBox.getValue();
			// read on the FX thread, not in call()
			String deselection = deselectionTextField.getText();

			Task pullTask = new Task<Void>() {
				@Override
				public Void call() {
					updateProgress(Double.NaN, 1.0);
					PullView.this.service.fetch(remote.getName());
					updateProgress(0.0, 1.0);
					PullView.this.service.pull(remote.getName(), deselection);
					updateProgress(1.0, 1.0);
					return null;
				}

				@Override
				public void succeeded() {
					super.succeeded();
					PullView.this.stepSuccess("Pull operation was successful.");
					PullView.this.progressBar.progressProperty().unbind();
				}

				@Override
				public void cancelled() {
					super.cancelled();
					PullView.this.stepError("Pull operation was cancelled.", this.getException());
					PullView.this.progressBar.progressProperty().unbind();
				}

				@Override
				public void failed() {
					super.failed();
					PullView.this.stepError("Error during pull operation.", this.getException());
					PullView.this.progressBar.progressProperty().unbind();
				}
			};
			PullView.this.progressBar.progressProperty().bind(pullTask.progressProperty());
			this.stepProgress();
			new Thread(pullTask).start();
		});

		pullButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());

		// filled once here, not in stepRemote(): going back to that step appended every remote again
		fillRemotes(this.remoteComboBox, this.service.getRemotes());

		this.stepRemote();
	}


	private void stepRemote() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Remote");

		Button selectButton = new Button("Choose Features...");
		selectButton.setTooltip(new Tooltip("Leave features out of the pull"));
		selectButton.setOnAction(event -> {
			this.stepDeselect();
		});
		// everything, or (after choosing) what is left: it said "Pull |"
		this.pullButton.setText("Pull All");
		this.rightButtons.getChildren().setAll(selectButton, pullButton);


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

		Label remoteLabel = new Label("Remote: ");
		gridPane.add(remoteLabel, 0, row, 1, 1);

		remoteLabel.setLabelFor(remoteComboBox);
		gridPane.add(remoteComboBox, 1, row, 1, 1);

		row++;


		selectButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());


		this.fit();
	}


	private void stepDeselect() {
		Button remoteButton = new Button("Back");
		remoteButton.setOnAction(event -> this.stepRemote());
		this.leftButtons.getChildren().setAll(remoteButton);

		this.headerLabel.setText("Selection");

		this.pullButton.setText("Pull");
		this.rightButtons.getChildren().setAll(pullButton);


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

		RowConstraints row1constraint = new RowConstraints();
		RowConstraints row2constraint = new RowConstraints();
		row2constraint.setVgrow(Priority.ALWAYS);
		row2constraint.setFillHeight(true);
		gridPane.getRowConstraints().addAll(row1constraint, row2constraint);

		this.setCenter(gridPane);

		int row = 0;


		Label selectionLabel = new Label("Deselection: ");
		gridPane.add(selectionLabel, 0, row, 1, 1);
		deselectionTextField.setDisable(false);
		selectionLabel.setLabelFor(deselectionTextField);
		gridPane.add(deselectionTextField, 1, row, 1, 1);
		row++;


		TreeView<FeatureInfo> featureSelectionTreeView = new TreeView<>();
		featureSelectionTreeView.setCellFactory(CheckBoxTreeCell.<FeatureInfo>forTreeView());
		featureSelectionTreeView.setShowRoot(true);
		featureSelectionTreeView.setEditable(false);

		TitledPane titledPane = new TitledPane();
		titledPane.setText("Features");
		titledPane.setCollapsible(false);
		titledPane.setContent(featureSelectionTreeView);
		titledPane.setMaxWidth(Double.MAX_VALUE);
		titledPane.setMaxHeight(Double.MAX_VALUE);
		gridPane.add(titledPane, 0, row, 2, 1);
		row++;


		// the remote's features as last fetched, at once; fetched again in the background, since a remote
		// that was never fetched has none yet
		Remote remote = this.remoteComboBox.getValue();
		this.fillFeatureTree(featureSelectionTreeView, this.service.getRemote(remote.getName()).getFeatures());
		titledPane.setText("Features (fetching ...)");
		Task<Collection<Feature>> fetchTask = new Task<>() {
			@Override
			public Collection<Feature> call() {
				PullView.this.service.fetch(remote.getName());
				return PullView.this.service.getRemote(remote.getName()).getFeatures();
			}

			@Override
			public void succeeded() {
				super.succeeded();
				titledPane.setText("Features");
				PullView.this.fillFeatureTree(featureSelectionTreeView, this.getValue());
			}

			@Override
			public void failed() {
				super.failed();
				titledPane.setText("Features (as last fetched; fetching failed: " + this.getException().getMessage() + ")");
			}
		};
		Thread fetchThread = new Thread(fetchTask);
		fetchThread.setDaemon(true);
		fetchThread.start();


		this.fit();
	}


	/**
	 * Shows {@code features} with their revisions, each ticked unless the deselection field names it -
	 * so the tree can be refilled after a fetch without losing what was unticked. Unticking and ticking
	 * writes the deselection field.
	 */
	private void fillFeatureTree(TreeView<FeatureInfo> featureSelectionTreeView, Collection<Feature> features) {
		Set<String> deselected = Arrays.stream(this.deselectionTextField.getText().split(","))
				.map(String::trim).filter(text -> !text.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));

		CheckBoxTreeItem<FeatureInfo> rootTreeItem = new CheckBoxTreeItem<>();
		Collection<CheckBoxTreeItem<FeatureInfo>> revisionTreeItems = new ArrayList<>();
		for (Feature feature : features) {
			CheckBoxTreeItem<FeatureInfo> featureTreeItem = new CheckBoxTreeItem<>();
			featureTreeItem.setValue(new FeatureInfo(feature, null));
			for (FeatureRevision featureVersion : feature.getRevisions()) {
				CheckBoxTreeItem<FeatureInfo> featureVersionTreeItem = new CheckBoxTreeItem<>();
				featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
				featureTreeItem.getChildren().add(featureVersionTreeItem);
				revisionTreeItems.add(featureVersionTreeItem);
			}
			rootTreeItem.getChildren().add(featureTreeItem);
		}
		rootTreeItem.setSelected(true);
		for (CheckBoxTreeItem<FeatureInfo> item : revisionTreeItems) {
			if (deselected.contains(item.getValue().getFeatureVersion().toString()))
				item.setSelected(false);
			item.selectedProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue)
					deselected.remove(item.getValue().getFeatureVersion().toString());
				else
					deselected.add(item.getValue().getFeatureVersion().toString());
				this.deselectionTextField.setText(String.join(", ", deselected));
			});
		}
		featureSelectionTreeView.setRoot(rootTreeItem);
		rootTreeItem.setExpanded(true);
	}


	private void stepProgress() {
		this.leftButtons.getChildren().clear();

		this.headerLabel.setText("Progress");

		this.rightButtons.getChildren().clear();


		// main content

		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setFillWidth(true);
		gridPane.getColumnConstraints().addAll(col1constraint);

		this.setCenter(gridPane);

		int row = 0;

		// progress bar
		progressBar.setMaxWidth(Double.MAX_VALUE);
		progressBar.prefWidthProperty().bind(gridPane.widthProperty().subtract(20));
		gridPane.add(progressBar, 0, row, 1, 1);
		row++;


		this.fit();
	}


	public static class FeatureInfo {
		private Feature feature;
		private FeatureRevision featureVersion;
		private SimpleBooleanProperty sbp;

		private FeatureInfo(Feature feature, FeatureRevision featureVersion) {
			this.feature = feature;
			this.featureVersion = featureVersion;
			this.sbp = new SimpleBooleanProperty(true);
		}

		public Feature getFeature() {
			return this.feature;
		}

		public FeatureRevision getFeatureVersion() {
			return this.featureVersion;
		}

		public SimpleBooleanProperty isSelectedProperty() {
			return this.sbp;
		}

		@Override
		public String toString() {
			if (this.feature == null && this.featureVersion == null) {
				return "FEATURE";
			} else if (this.feature != null && this.featureVersion == null)
				return this.feature.getId() + " - " + this.feature.getName();
			else if (this.featureVersion != null) {
				return this.featureVersion.getId();
			}
			return "";
		}
	}


}
