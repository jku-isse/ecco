package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.view.detail.AssociationDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
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
import java.util.List;

public class AssociationsView extends BorderPane implements EccoListener {

	private static final double BAR_WIDTH = 60;
	private static final double BAR_HEIGHT = 10;

	private EccoService service;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

	private int maxNumArtifacts = 1;


	public AssociationsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

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
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().computeCondition().getSimpleModuleRevisionConditionString()));
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
			AssociationInfo associationInfo = new AssociationInfo(association);
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

		public AssociationInfo(Association association) {
			this.association = association;
			this.numArtifacts = new SimpleIntegerProperty(association.getRootNode().countArtifacts());
		}

		public Association getAssociation() {
			return this.association;
		}

		public int getNumArtifacts() {
			return this.numArtifacts.get();
		}
	}

}
