package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AssociationDetailView extends BorderPane {

	private EccoService service;

	private Association currentAssociation;

	final ObservableList<ModuleInfo> modulesData = FXCollections.observableArrayList();

	private TextField associationId;
	private TextArea associationName;
	private SplitPane splitPane;
	private ToolBar toolBar;


	public AssociationDetailView(EccoService service) {
		this.service = service;

		this.currentAssociation = null;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);


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
		this.associationName = new TextArea();

		int row = 0;
		associationDetails.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		associationDetails.add(this.associationId, 1, row, 1, 1);
		row++;
		associationDetails.add(new Label("Name: "), 1, row, 1, 1);
		row++;
		associationDetails.add(this.associationName, 1, row, 1, 1);
		row++;

		splitPane.getItems().add(associationDetails);


		// containment table
		splitPane.getItems().add(new HBox(new Label("TODO: containment table")));


		// list of modules
		TableView<ModuleInfo> modulesTable = new TableView<ModuleInfo>();
		modulesTable.setEditable(false);
		modulesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<ModuleInfo, String> moduleCol = new TableColumn<>("Module");
		TableColumn<ModuleInfo, String> modulesCol = new TableColumn<>("Modules");

		modulesCol.getColumns().setAll(moduleCol);
		modulesTable.getColumns().setAll(modulesCol);

		modulesTable.setItems(this.modulesData);

		splitPane.getItems().add(modulesTable);


		// show nothing initially
		this.showAssociation(null);
	}


	public void showAssociation(Association association) {
		this.currentAssociation = association;

		this.modulesData.clear();

		if (association != null) {
			this.setCenter(this.splitPane);
			this.toolBar.setDisable(false);

			// show details
			this.associationId.setText(String.valueOf(association.getId()));
			this.associationName.setText(association.getName());

			// show containment table
			// TODO

			// show modules
			// TODO
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.associationId.setText("");
			this.associationName.setText("");
		}
	}


	public static class ModuleInfo {
		private ModuleInfo() {

		}
	}

}
