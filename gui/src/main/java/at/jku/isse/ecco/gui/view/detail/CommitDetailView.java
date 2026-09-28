package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.TableColumns;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class CommitDetailView extends BorderPane {

	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();
	private Pane centerPane;
	private TextField commitId;
	private TextArea commitConfiguration;


	public CommitDetailView() {
		// callers that place it flush against other content (e.g. under a log) override this
		this.setPadding(new Insets(10));

		// labels left of their fields; the associations below take the remaining height
		GridPane detailsPane = new GridPane();
		this.centerPane = detailsPane;
		detailsPane.setHgap(10);
		detailsPane.setVgap(8);

		ColumnConstraints labelColumn = new ColumnConstraints();
		labelColumn.setMinWidth(Region.USE_PREF_SIZE);
		ColumnConstraints fieldColumn = new ColumnConstraints();
		fieldColumn.setFillWidth(true);
		fieldColumn.setHgrow(Priority.ALWAYS);
		detailsPane.getColumnConstraints().addAll(labelColumn, fieldColumn);

		this.commitId = new TextField();
		this.commitId.setEditable(false);
		// configurations are long comma-separated lists: wrapped, so all of it is readable
		this.commitConfiguration = new TextArea();
		this.commitConfiguration.setEditable(false);
		this.commitConfiguration.setWrapText(true);
		this.commitConfiguration.setPrefRowCount(2);

		int row = 0;
		Label idLabel = new Label("Id:");
		detailsPane.add(idLabel, 0, row);
		detailsPane.add(this.commitId, 1, row);
		row++;

		Label configurationLabel = new Label("Configuration:");
		GridPane.setValignment(configurationLabel, VPos.TOP);
		configurationLabel.setPadding(new Insets(4, 0, 0, 0)); // level with the text area's first line
		detailsPane.add(configurationLabel, 0, row);
		detailsPane.add(this.commitConfiguration, 1, row);
		row++;

		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(false);
		associationsTable.setPlaceholder(new Label("No associations"));
		associationsTable.setPrefHeight(160);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		associationsTable.getColumns().setAll(idAssociationsCol, conditionAssociationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().computeCondition().toString()));
		conditionAssociationsCol.setCellFactory(TableColumns.wrappingCellFactory());

		associationsTable.setItems(this.associationsData);

		TableColumns.defaultWidth(idAssociationsCol, 90);
		TableColumns.growToFill(associationsTable, conditionAssociationsCol);

		Label associationsLabel = new Label("Associations:");
		GridPane.setValignment(associationsLabel, VPos.TOP);
		associationsLabel.setPadding(new Insets(4, 0, 0, 0));
		detailsPane.add(associationsLabel, 0, row);
		detailsPane.add(associationsTable, 1, row);
		RowConstraints fixedRow = new RowConstraints();
		RowConstraints growingRow = new RowConstraints();
		growingRow.setVgrow(Priority.ALWAYS);
		detailsPane.getRowConstraints().addAll(fixedRow, fixedRow, growingRow);
		row++;

		// show nothing initially
		this.showCommit(null);
	}


	public final void showCommit(Commit commit) {

		this.associationsData.clear();

		if (commit != null) {
			this.setCenter(this.centerPane);

			this.commitId.setText(String.valueOf(commit.getId()));
			this.commitConfiguration.setText(commit.getConfiguration() == null ? "" : commit.getConfiguration().toString());

			// show associations
			for (Association association : commit.getAssociations()) {
				CommitDetailView.this.associationsData.add(new AssociationInfo(association));
			}
		} else {
			this.setCenter(null);

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
