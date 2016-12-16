package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class CommitDetailView extends BorderPane {

	private EccoService service;

	private Commit currentCommit;

	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();


	private SplitPane splitPane;
	private ToolBar toolBar;


	private TextField commitId;
	private TextField commitConfiguration;
	private TextField commitCommitter;


	public CommitDetailView(EccoService service) {
		this.service = service;

		this.currentCommit = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);


		// splitpane
		this.splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);


		// details
		GridPane detailsPane = new GridPane();
		detailsPane.setHgap(10);
		detailsPane.setVgap(10);
		detailsPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		detailsPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.commitId = new TextField();
		this.commitId.setEditable(false);
		this.commitConfiguration = new TextField();
		this.commitConfiguration.setEditable(false);
		this.commitCommitter = new TextField();
		this.commitCommitter.setEditable(false);

		int row = 0;
		detailsPane.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.commitId, 1, row, 1, 1);
		row++;

		detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.commitConfiguration, 1, row, 1, 1);
		row++;

		detailsPane.add(new Label("Committer: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.commitCommitter, 1, row, 1, 1);
		row++;

		splitPane.getItems().add(detailsPane);


		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<AssociationInfo>();
		associationsTable.setEditable(false);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> nameAssociationsCol = new TableColumn<>("Name");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, nameAssociationsCol, conditionAssociationsCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		nameAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getName()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getPresenceCondition().toString()));

		associationsTable.setItems(this.associationsData);

		splitPane.getItems().add(associationsTable);


		// show nothing initially
		this.showCommit(null);
	}


	public void showCommit(Commit commit) {
		this.currentCommit = commit;

		this.associationsData.clear();

		if (commit != null) {
			this.setCenter(this.splitPane);
			this.toolBar.setDisable(false);

			this.commitId.setText(String.valueOf(commit.getId()));
			this.commitConfiguration.setText(commit.getConfiguration() == null ? "" : commit.getConfiguration().toString());
			this.commitCommitter.setText(commit.getCommiter());

			// show associations
			for (Association association : commit.getAssociations()) {
				CommitDetailView.this.associationsData.add(new AssociationInfo(association));
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.commitId.setText("");
			this.commitConfiguration.setText("");
			this.commitCommitter.setText("");
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
