package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.service.EccoService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class CommitDetailView extends BorderPane {

	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();
	private Pane centerPane;
	private TextField commitId;
	private TextArea commitConfiguration;

	/** Whose lock a commit is read under. */
	private final EccoService service;
	/** Counts the commits asked to be shown: a read overtaken by a later one shows nothing. */
	private int shown;


	/**
	 * @param service the repository's: a commit's associations, their conditions and artifact counts
	 *                are read holding its lock, in the background - a commit running meanwhile changes them
	 */
	public CommitDetailView(EccoService service) {
		this.service = service;

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

		// read with the commit: a condition computed here, on the FX thread, read counters a commit changed meanwhile
		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getId()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getCondition()));
		conditionAssociationsCol.setCellFactory(TableColumns.wrappingCellFactory());

		associationsTable.setItems(this.associationsData);

		TableColumns.shortIds(idAssociationsCol, associationsTable.getItems());
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
		this.show(null);
	}


	/** Shows [commit] (nothing: null) once read - in the background, holding the service's lock; a newer call wins. */
	public final void showCommit(Commit commit) {
		int read = ++this.shown;
		if (commit == null) {
			this.show(null);
			return;
		}
		RepositoryAccess.read(this.service, "Commit details", service -> read(commit), details -> {
			if (read == this.shown)
				this.show(details);
		}, failure -> new ExceptionAlert(failure).show());
	}

	/**
	 * What is shown of [commit], as plain values - to be called holding the service's lock, off the
	 * FX thread: its associations, their conditions and artifact counts change with every commit.
	 */
	public static Details read(Commit commit) {
		List<AssociationInfo> associations = new ArrayList<>();
		for (Association association : commit.getAssociations())
			associations.add(new AssociationInfo(association.getId(), association.computeCondition().toDisplayString(),
					association.getRootNode().countArtifacts()));
		return new Details(String.valueOf(commit.getId()),
				commit.getConfiguration() == null ? "" : commit.getConfiguration().toString(), associations);
	}

	/** Shows [details] from {@link #read} (nothing: null), on the FX thread; a read still running for {@link #showCommit} is overtaken. */
	public final void show(Details details) {
		this.shown++;
		this.associationsData.clear();

		if (details != null) {
			this.setCenter(this.centerPane);

			this.commitId.setText(details.id());
			this.commitConfiguration.setText(details.configuration());

			// show associations
			this.associationsData.addAll(details.associations());
		} else {
			this.setCenter(null);

			this.commitId.setText("");
			this.commitConfiguration.setText("");
		}
	}


	/** A commit as shown: its id, configuration and associations. */
	public record Details(String id, String configuration, List<AssociationInfo> associations) {
	}

	/** An association as shown: its id, condition and number of artifacts. */
	public static class AssociationInfo {
		private final String id;
		private final String condition;
		private final int numArtifacts;

		public AssociationInfo(String id, String condition, int numArtifacts) {
			this.id = id;
			this.condition = condition;
			this.numArtifacts = numArtifacts;
		}

		public String getId() {
			return this.id;
		}

		public String getCondition() {
			return this.condition;
		}

		public int getNumArtifacts() {
			return this.numArtifacts;
		}
	}

}
