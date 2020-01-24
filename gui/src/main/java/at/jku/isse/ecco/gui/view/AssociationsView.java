package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.view.detail.AssociationDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.util.Collection;

public class AssociationsView extends BorderPane implements EccoListener {

	private EccoService service;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();


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

		toolBar.getItems().add(new Separator());

		CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Use Simplified Labels");
		toolBar.getItems().add(useSimplifiedLabelsCheckBox);


		toolBar.getItems().add(new Separator());


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(false);
		associationsTable.setTableMenuButtonVisible(true);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		TableColumn<AssociationInfo, Integer> numArtifactsAssociationsCol = new TableColumn<>("NumArtifacts");
		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty()).then(param.getValue().getAssociation().computeCondition().getSimpleModuleRevisionConditionString()).otherwise(param.getValue().getAssociation().computeCondition().getModuleRevisionConditionString()));
		numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));


		SortedList<AssociationInfo> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

		associationsTable.setItems(sortedData);


		useSimplifiedLabelsCheckBox.setSelected(true);


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
		useSimplifiedLabelsCheckBox.setSelected(true);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	private void updateAssociations(Collection<? extends Association> associations) {
		AssociationsView.this.associationsData.clear();
		for (Association association : associations) {
			this.associationsData.add(new AssociationInfo(association));
		}
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			Collection<? extends Association> associations = service.getRepository().getAssociations();
			Platform.runLater(() -> this.updateAssociations(associations));
		} else {
			Platform.runLater(() -> this.setDisable(true));
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
