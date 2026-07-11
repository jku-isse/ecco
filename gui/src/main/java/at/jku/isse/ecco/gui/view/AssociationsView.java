package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.mining.ParallelMinimization;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.view.detail.AssociationDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class AssociationsView extends BorderPane implements EccoListener {

	private static final double BAR_WIDTH = 60;
	private static final double BAR_HEIGHT = 10;

	// same defaults as SuggestConstraintsCommand/ConstraintSuggestionsView; no extra UI for these
	// here since this button is a one-click "preview with today's accepted constraints" action.
	private static final int MINIMIZE_MIN_WITNESS = 4;
	private static final double MINIMIZE_CONFIDENCE = 0.9;

	private EccoService service;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

	/** Association id -> minimized condition text, from the last "Minimize Presence Conditions" run. */
	private final Map<String, String> minimizedByAssociationId = new HashMap<>();

	private final ProgressBar minimizeProgressBar = new ProgressBar(0);

	private int maxNumArtifacts = 1;


	public AssociationsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		this.minimizeProgressBar.setMaxWidth(Double.MAX_VALUE);
		this.minimizeProgressBar.setVisible(false);
		this.setBottom(this.minimizeProgressBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);
		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);

			Task refreshTask = new Task<Void>() {
				@Override
				public Void call() throws EccoException {
					Collection<? extends Association> associations = AssociationsView.this.service.getRepository().getAssociations();
					Platform.runLater(() -> AssociationsView.this.updateAssociations(associations));
					Platform.runLater(() -> toolBar.setDisable(false));
					return null;
				}
			};

			new Thread(refreshTask).start();
		});

		Button minimizeButton = new Button("Minimize Presence Conditions");
		toolBar.getItems().add(minimizeButton);
		// setOnAction is wired further down, once associationsTable/associationDetailView exist --
		// this button itself has to be created here so it lands next to Refresh in the toolbar.

		toolBar.getItems().add(new Separator());


		FilteredList<AssociationInfo> filteredData = new FilteredList<>(this.associationsData, p -> true);

		CheckBox showEmptyAssociationsCheckBox = new CheckBox("Show Associations Without Artifacts");
		toolBar.getItems().add(showEmptyAssociationsCheckBox);
		showEmptyAssociationsCheckBox.selectedProperty().addListener((ov, oldValue, newValue) -> {
			filteredData.setPredicate(associationInfo -> newValue || (associationInfo.getNumArtifacts() > 0));
		});

		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(false);
		associationsTable.setTableMenuButtonVisible(true);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Simplified Condition");
		TableColumn<AssociationInfo, Integer> numArtifactsAssociationsCol = new TableColumn<>("NumArtifacts");
		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		// a live property, not a one-shot computed string: cells update on their own as
		// "Minimize Presence Conditions" fills each association in, no manual table refresh needed.
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> param.getValue().simplifiedConditionProperty());
		numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));
		numArtifactsAssociationsCol.setCellFactory(col -> new TableCell<AssociationInfo, Integer>() {
			private final Region track = new Region();
			private final Region fill = new Region();
			private final Label valueLabel = new Label();
			private final StackPane bar = new StackPane(track, fill);
			private final HBox content = new HBox(6, bar, valueLabel);

			{
				track.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
				track.setMaxSize(BAR_WIDTH, BAR_HEIGHT);
				track.setStyle("-fx-background-color: #e1e0d9; -fx-background-radius: 2;");

				fill.setPrefHeight(BAR_HEIGHT);
				fill.setMaxHeight(BAR_HEIGHT);
				fill.setStyle("-fx-background-color: #2a78d6; -fx-background-radius: 2;");

				bar.setAlignment(Pos.CENTER_LEFT);
				content.setAlignment(Pos.CENTER_LEFT);
			}

			@Override
			protected void updateItem(Integer value, boolean empty) {
				super.updateItem(value, empty);

				if (empty || value == null) {
					setGraphic(null);
				} else {
					double ratio = AssociationsView.this.maxNumArtifacts <= 0 ? 0 : Math.min(1.0, value / (double) AssociationsView.this.maxNumArtifacts);
					fill.setPrefWidth(BAR_WIDTH * ratio);
					fill.setMaxWidth(BAR_WIDTH * ratio);
					valueLabel.setText(String.valueOf(value));
					setGraphic(content);
				}
			}
		});

		numArtifactsAssociationsCol.setSortType(TableColumn.SortType.DESCENDING);
		associationsTable.getSortOrder().add(numArtifactsAssociationsCol);


		SortedList<AssociationInfo> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

		associationsTable.setItems(sortedData);


		// details view
		AssociationDetailView associationDetailView = new AssociationDetailView(service);


		associationsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				associationDetailView.showAssociation(newValue.getAssociation());
			} else {
				associationDetailView.showAssociation(null);
			}
		});

		minimizeButton.setOnAction(e -> {
			toolBar.setDisable(true);

			// snapshot id -> row now, on the FX thread, so the background task can push each
			// association's result into its table row as soon as it's computed, incrementally --
			// rather than only updating the table once at the very end.
			Map<String, AssociationInfo> infoByAssociationId = new HashMap<>();
			for (AssociationInfo info : AssociationsView.this.associationsData) {
				infoByAssociationId.put(info.getAssociation().getId(), info);
			}

			Task<Map<String, String>> minimizeTask = new Task<Map<String, String>>() {
				@Override
				public Map<String, String> call() throws EccoException {
					// same pipeline as the CLI's MinimizePreviewCommand: re-mine fresh (never trust
					// cached accept-time hardness -- a later commit may have added a counterexample
					// since), filter to accepted signatures, compile once, then reuse that one
					// feature-model formula across every association rather than re-mining per row.
					updateProgress(0, 1);
					List<Set<String>> configs = ConfigurationBridge.readConfigurations(AssociationsView.this.service);
					List<ConstraintMiner.Suggestion> mined =
							new ConstraintMiner(MINIMIZE_MIN_WITNESS, MINIMIZE_CONFIDENCE, null).mine(configs);
					Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(AssociationsView.this.service.getRepositoryDir());

					List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
					for (ConstraintMiner.Suggestion suggestion : mined) {
						if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
							acceptedSuggestions.add(suggestion);
						}
					}
					// mining above is one pass over history and comparatively fast; the SAT work in
					// minimize() below is the actual per-association cost, so that's what the progress
					// bar tracks. Associations are independent of each other, so ParallelMinimization
					// runs them concurrently (one worker thread per core) rather than one at a time --
					// on a large repository the sequential version could take tens of minutes.
					List<Association> associations = new ArrayList<>(AssociationsView.this.service.getRepository().getAssociations());
					AtomicInteger completedCount = new AtomicInteger(0);
					return ParallelMinimization.minimizeAll(associations, acceptedSuggestions, (association, minimizedText) -> {
						updateProgress(completedCount.incrementAndGet(), associations.size());

						AssociationInfo info = infoByAssociationId.get(association.getId());
						if (info != null) {
							Platform.runLater(() -> info.setSimplifiedCondition(minimizedText));
						}
					});
				}

				// hide/unbind the progress bar and re-enable the toolbar however the task ends --
				// succeeded() and failed() both run on the FX thread regardless of which branch of
				// call() was taken, unlike code placed only at the end of call() itself, which a
				// thrown exception would skip entirely.
				private void finished() {
					AssociationsView.this.minimizeProgressBar.progressProperty().unbind();
					AssociationsView.this.minimizeProgressBar.setVisible(false);
					toolBar.setDisable(false);
				}

				@Override
				public void succeeded() {
					super.succeeded();

					// kept around so a subsequent Refresh can restore each row's column instead of
					// resetting it to empty; the column itself was already updated incrementally,
					// per-row, during call() above.
					AssociationsView.this.minimizedByAssociationId.clear();
					AssociationsView.this.minimizedByAssociationId.putAll(this.getValue());

					this.finished();
				}

				@Override
				public void failed() {
					super.failed();
					this.finished();

					ExceptionAlert alert = new ExceptionAlert(this.getException());
					alert.setTitle("Minimize Presence Conditions Error");
					alert.setHeaderText("Minimize Presence Conditions Error");
					alert.showAndWait();
				}
			};

			this.minimizeProgressBar.progressProperty().bind(minimizeTask.progressProperty());
			this.minimizeProgressBar.setVisible(true);

			new Thread(minimizeTask).start();
		});


		// add to split pane
		splitPane.getItems().addAll(associationsTable, associationDetailView);


		showEmptyAssociationsCheckBox.setSelected(false);

		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}


	private void updateAssociations(Collection<? extends Association> associations) {
		List<AssociationInfo> associationInfos = new ArrayList<>();
		int max = 1;
		for (Association association : associations) {
			// restore from the last minimize run if we have it, rather than resetting the column to
			// empty on every refresh (a plain Refresh doesn't necessarily mean the feature model or
			// conditions actually changed)
			AssociationInfo associationInfo = new AssociationInfo(association, this.minimizedByAssociationId.get(association.getId()));
			max = Math.max(max, associationInfo.getNumArtifacts());
			associationInfos.add(associationInfo);
		}
		// set before mutating the observable list so cells never render against a stale max
		this.maxNumArtifacts = max;

		this.associationsData.setAll(associationInfos);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			Collection<? extends Association> associations = service.getRepository().getAssociations();
			Platform.runLater(() -> this.updateAssociations(associations));
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
				this.updateAssociations(Collections.emptyList());
				this.minimizedByAssociationId.clear();
			});
		}
	}

	// TODO: add new associations
	public void associationsChangedEvent(Collection<Association> associations) {
		Platform.runLater(() -> this.updateAssociations(associations));
	}


	public static class AssociationInfo {
		private Association association;

		private IntegerProperty numArtifacts;

		/**
		 * Empty until a "Minimize Presence Conditions" run fills it in (see
		 * {@link AssociationsView}'s minimize button) -- unlike the old
		 * {@code getSimpleModuleRevisionConditionString()}-backed column, this is a real,
		 * SAT-verified minimization under the accepted feature model, not a truncation to the
		 * lowest-order module(s).
		 */
		private final StringProperty simplifiedCondition;

		public AssociationInfo(Association association) {
			this(association, null);
		}

		public AssociationInfo(Association association, String simplifiedCondition) {
			this.association = association;
			this.numArtifacts = new SimpleIntegerProperty(association.getRootNode().countArtifacts());
			this.simplifiedCondition = new SimpleStringProperty(simplifiedCondition == null ? "" : simplifiedCondition);
		}

		public Association getAssociation() {
			return this.association;
		}

		public int getNumArtifacts() {
			return this.numArtifacts.get();
		}

		public String getSimplifiedCondition() {
			return this.simplifiedCondition.get();
		}

		public void setSimplifiedCondition(String simplifiedCondition) {
			this.simplifiedCondition.set(simplifiedCondition == null ? "" : simplifiedCondition);
		}

		public StringProperty simplifiedConditionProperty() {
			return this.simplifiedCondition;
		}
	}

}
