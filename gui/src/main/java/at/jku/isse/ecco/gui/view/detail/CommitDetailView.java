package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class CommitDetailView extends BorderPane {

	private EccoService service;

	private Commit currentCommit;

	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();


	private Pane centerPane;
	private ToolBar toolBar;


	private TextField commitId;
	private TextField commitConfiguration;


	public CommitDetailView(EccoService service) {
		this.service = service;

		this.currentCommit = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);


		// details
		GridPane detailsPane = new GridPane();
		this.centerPane = detailsPane;
		detailsPane.setHgap(10);
		detailsPane.setVgap(10);
		detailsPane.setPadding(new Insets(10, 10, 10, 10));
		this.setCenter(this.centerPane);

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		detailsPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.commitId = new TextField();
		this.commitId.setEditable(false);
		this.commitConfiguration = new TextField();
		this.commitConfiguration.setEditable(false);

		int row = 0;
		detailsPane.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.commitId, 1, row, 1, 1);
		row++;

		detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.commitConfiguration, 1, row, 1, 1);
		row++;


		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(false);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, conditionAssociationsCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().computeCondition().toString()));

		associationsTable.setItems(this.associationsData);

		detailsPane.add(associationsTable, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showCommit(null);
	}


	public void showCommit(Commit commit) {
		this.currentCommit = commit;

		this.associationsData.clear();

		if (commit != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			this.commitId.setText(String.valueOf(commit.getId()));
			this.commitConfiguration.setText(commit.getConfiguration() == null ? "" : commit.getConfiguration().toString());

//			// show associations
//			for (Association association : commit.getAssociations()) {
//				CommitDetailView.this.associationsData.add(new AssociationInfo(association));
//			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.commitId.setText("");
			this.commitConfiguration.setText("");
		}
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

		public void setNumArtifacts(int numArtifacts) {
			this.numArtifacts.set(numArtifacts);
		}

		public IntegerProperty numArtifactsProperty() {
			return this.numArtifacts;
		}
	}

}
