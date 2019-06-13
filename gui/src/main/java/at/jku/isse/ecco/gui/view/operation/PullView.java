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
import java.util.Collection;
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

		this.pullButton = new Button("Pull |");
		this.remoteComboBox = new ComboBox<>();
		this.deselectionTextField = new TextField();
		this.progressBar = new ProgressBar();

		this.pullButton.setOnAction(event -> {
			Remote remote = this.remoteComboBox.getValue();

			Task pullTask = new Task<Void>() {
				@Override
				public Void call() {
					updateProgress(Double.NaN, 1.0);
					PullView.this.service.fetch(remote.getName());
					updateProgress(0.0, 1.0);
					PullView.this.service.pull(remote.getName(), deselectionTextField.getText());
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

		this.stepRemote();
	}


	private void stepRemote() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Remote");

		Button selectButton = new Button("Select >");
		selectButton.setOnAction(event -> {
			this.stepDeselect();
		});
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


		this.remoteComboBox.getItems().add(null);
		for (Remote remote : this.service.getRemotes()) {
			this.remoteComboBox.getItems().add(remote);
		}


		selectButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());


		this.fit();
	}


	private void stepDeselect() {
		Button remoteButton = new Button("< Remote");
		remoteButton.setOnAction(event -> this.stepRemote());
		this.leftButtons.getChildren().setAll(remoteButton);

		this.headerLabel.setText("Selection");

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


		Collection<CheckBoxTreeItem<FeatureInfo>> deselectedFeatureVersionTreeItems = new ArrayList<>();
		CheckBoxTreeItem<FeatureInfo> rootTreeItem = new CheckBoxTreeItem<>();
		for (Feature feature : this.service.getRemote(this.remoteComboBox.getValue().getName()).getFeatures()) {
			CheckBoxTreeItem<FeatureInfo> featureTreeItem = new CheckBoxTreeItem<>();
			featureTreeItem.setValue(new FeatureInfo(feature, null));
			for (FeatureRevision featureVersion : feature.getRevisions()) {
				CheckBoxTreeItem<FeatureInfo> featureVersionTreeItem = new CheckBoxTreeItem<>();
				featureVersionTreeItem.setValue(new FeatureInfo(feature, featureVersion));
				//featureVersionTreeItem.setSelected(true);
				featureTreeItem.getChildren().add(featureVersionTreeItem);


				featureVersionTreeItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
					if (!newValue) {
						deselectedFeatureVersionTreeItems.add(featureVersionTreeItem);
					} else {
						deselectedFeatureVersionTreeItems.remove(featureVersionTreeItem);
					}

					String deselectionString = deselectedFeatureVersionTreeItems.stream().map(featureInfoCheckBoxTreeItem -> featureInfoCheckBoxTreeItem.getValue().getFeatureVersion().toString()).collect(Collectors.joining(", "));
					deselectionTextField.setText(deselectionString);

//					StringBuilder sb = new StringBuilder();
//					for (CheckBoxTreeItem<FeatureInfo> featureInfoCheckBoxTreeItem : deselectedFeatureVersionTreeItems) {
//						if (!featureInfoCheckBoxTreeItem.isSelected()) {
//							sb.append(featureInfoCheckBoxTreeItem.getValue().getFeatureVersion().toString());
//						}
//					}
//					selectionTextField.setText(sb.toString());
				});
			}
			rootTreeItem.getChildren().add(featureTreeItem);
		}
		rootTreeItem.setSelected(true);
		featureSelectionTreeView.setRoot(rootTreeItem);
		rootTreeItem.setExpanded(true);


		this.fit();
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
