package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AssociationDetailView extends BorderPane {

	private EccoService service;

	private Association currentAssociation;

	private TextField associationId;
	private SplitPane splitPane;
	private TextArea associationPC;
	private TableView<ModuleRow> modulesTable;
	private Label modulesSummary;

	/**
	 * A module revision of the association's condition: its order (the "d^N" of the module
	 * notation), how often the association was observed with it, and whether it is a term of the
	 * condition shown - or absorbed by one with fewer literals, which changes nothing.
	 */
	record ModuleRow(int order, String moduleRevision, int count, boolean inCondition) {
	}
	private ArtifactSnippetTreeView artifactTreeView;


	public AssociationDetailView(EccoService service) {
		this.service = service;

		// splitpane
		this.splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);


		// details
		GridPane associationDetails = new GridPane();
		associationDetails.setHgap(10);
		associationDetails.setVgap(10);
		associationDetails.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		associationDetails.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.associationId = new TextField();
		this.associationId.setEditable(false);

		this.associationPC = new TextArea();
		this.associationPC.setEditable(false);
		this.associationPC.setWrapText(true);
		this.associationPC.setPrefRowCount(8);

		int row = 0;
		associationDetails.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		associationDetails.add(this.associationId, 1, row, 1, 1);
		row++;
		associationDetails.add(new Label("Presence Condition: "), 1, row, 1, 1);
		row++;
		// the condition as everywhere, and the whole module lattice it was computed from (the
		// condition leaves out the module revisions others absorb)
		TabPane conditionTabs = new TabPane(new Tab("Condition", this.associationPC), new Tab("Modules", this.createModulesPane()));
		conditionTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		associationDetails.add(conditionTabs, 1, row, 1, 1);
		row++;


		splitPane.getItems().add(associationDetails);


		// containment table: artifact tree of the selected association
		this.artifactTreeView = new ArtifactSnippetTreeView();

		splitPane.getItems().add(this.artifactTreeView);


		// show nothing initially
		this.showAssociation(null);
	}


	private VBox createModulesPane() {
		this.modulesTable = new TableView<>();
		this.modulesTable.setPlaceholder(new Label("No modules"));
		this.modulesTable.setPrefHeight(200);
		TableColumn<ModuleRow, Integer> orderCol = new TableColumn<>("Order");
		orderCol.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().order()));
		TableColumn<ModuleRow, String> moduleCol = new TableColumn<>("Module revision");
		moduleCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().moduleRevision()));
		TableColumn<ModuleRow, Integer> countCol = new TableColumn<>("Count");
		countCol.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().count()));
		TableColumn<ModuleRow, String> inConditionCol = new TableColumn<>("In condition");
		inConditionCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().inCondition() ? "yes" : "absorbed"));
		this.modulesTable.getColumns().setAll(java.util.List.of(orderCol, moduleCol, countCol, inConditionCol));
		TableColumns.growToFill(this.modulesTable, moduleCol);
		this.modulesSummary = new Label();
		VBox pane = new VBox(6, this.modulesSummary, this.modulesTable);
		VBox.setVgrow(this.modulesTable, Priority.ALWAYS);
		return pane;
	}

	/** The module revisions of [association]'s condition, by order, with their counts. Package-visible for testing. */
	static java.util.List<ModuleRow> moduleRows(Association association) {
		Condition condition = association.computeCondition();
		java.util.Set<ModuleRevision> absorbed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		absorbed.addAll(condition.getAbsorbedModuleRevisions());
		java.util.List<ModuleRow> rows = new java.util.ArrayList<>();
		condition.getModules().forEach((module, revisions) -> {
			for (ModuleRevision revision : revisions)
				rows.add(new ModuleRow(revision.getOrder(), revision.toString(), count(association, revision), absorbed.contains(revision)));
		});
		rows.sort(java.util.Comparator.comparingInt(ModuleRow::order).thenComparing(ModuleRow::moduleRevision));
		return rows;
	}

	private static int count(Association association, ModuleRevision revision) {
		if (!(association instanceof Association.Op op))
			return 0;
		ModuleCounter moduleCounter = op.getCounter().getChild(revision.getModule());
		ModuleRevisionCounter revisionCounter = moduleCounter == null ? null : moduleCounter.getChild(revision);
		return revisionCounter == null ? 0 : revisionCounter.getCount();
	}

	public final void showAssociation(Association association) {
		this.currentAssociation = association;

		if (association != null) {
			this.setCenter(this.splitPane);

			// show details
			this.associationId.setText(String.valueOf(association.getId()));
			this.associationPC.setText(association.computeCondition().toDisplayString());
			java.util.List<ModuleRow> rows = moduleRows(association);
			this.modulesTable.setItems(FXCollections.observableArrayList(rows));
			this.modulesSummary.setText(rows.size() + " module revision(s), " + rows.stream().filter(ModuleRow::inCondition).count()
					+ " of them in the condition - the others are absorbed by one with fewer literals");

			// show containment table
			this.artifactTreeView.setRootNode(association.getRootNode());
		} else {
			this.setCenter(null);

			this.associationId.setText("");
			this.associationPC.setText("");
			this.modulesTable.getItems().clear();
			this.modulesSummary.setText("");

			this.artifactTreeView.setRootNode(null);
		}
	}

}
