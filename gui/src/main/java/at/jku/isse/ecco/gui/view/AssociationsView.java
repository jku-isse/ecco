package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.gui.MinimizationResults;
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
import javafx.collections.MapChangeListener;
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
import java.util.List;

public class AssociationsView extends BorderPane implements EccoListener {

	private static final double BAR_WIDTH = 60;
	private static final double BAR_HEIGHT = 10;

	private EccoService service;
	private final MinimizationResults minimizationResults;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

	private final ProgressBar minimizeProgressBar = new ProgressBar(0);

	private int maxNumArtifacts = 1;


	public AssociationsView(EccoService service, MinimizationResults minimizationResults) {
		this.service = service;
		this.minimizationResults = minimizationResults;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		// this view only ever displays minimization results -- triggering a run happens in the
		// Feature Model tab, right after reviewing/accepting suggestions; see MinimizationResults.
		this.minimizeProgressBar.setMaxWidth(Double.MAX_VALUE);
		this.minimizeProgressBar.progressProperty().bind(minimizationResults.progressProperty());
		this.minimizeProgressBar.visibleProperty().bind(minimizationResults.runningProperty());
		this.setBottom(this.minimizeProgressBar);

		// live: as the shared minimize run (triggered elsewhere) fills entries in, or an entry is
		// dropped because its association no longer exists, push the change into whichever
		// currently-displayed row has that association id, without needing a table refresh.
		minimizationResults.getMinimizedByAssociationId().addListener((MapChangeListener<String, String>) change -> {
			for (AssociationInfo info : AssociationsView.this.associationsData) {
				if (info.getAssociation().getId().equals(change.getKey())) {
					info.setSimplifiedCondition(change.wasAdded() ? change.getValueAdded() : null);
					break;
				}
			}
		});

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
			// seed from the shared model's current state, rather than resetting the column to empty
			// on every refresh (a plain Refresh doesn't necessarily mean minimization results are
			// stale) -- kept in sync afterward by the MapChangeListener registered in the constructor
			AssociationInfo associationInfo = new AssociationInfo(association, this.minimizationResults.getMinimizedByAssociationId().get(association.getId()));
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
		 * Empty until a "Minimize Presence Conditions" run (triggered from the Feature Model tab;
		 * see {@link at.jku.isse.ecco.gui.MinimizationResults}) fills it in -- unlike the old
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
