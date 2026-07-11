package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AssociationDetailView extends BorderPane {

	private EccoService service;

	private Association currentAssociation;

	private TextField associationId;
	private SplitPane splitPane;
	private ToolBar toolBar;
	private TextArea associationPC;
	private ArtifactSnippetTreeView artifactTreeView;


	public AssociationDetailView(EccoService service) {
		this.service = service;

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

		this.associationPC = new TextArea();
		this.associationPC.setEditable(false);
		this.associationPC.setWrapText(true);
		this.associationPC.setPrefRowCount(8);

		Button updateButton = new Button("Update");

		int row = 0;
		associationDetails.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		associationDetails.add(this.associationId, 1, row, 1, 1);
		row++;
		associationDetails.add(new Label("Name: "), 1, row, 1, 1);
		row++;
		associationDetails.add(new Label("Presence Condition: "), 1, row, 1, 1);
		row++;
		associationDetails.add(this.associationPC, 1, row, 1, 1);
		row++;
		associationDetails.add(updateButton, 1, row, 1, 1);
		row++;


//		updateButton.setOnAction(event -> {
//			AssociationDetailView.this.toolBar.setDisable(true);
//
//			Task updateTask = new Task<Void>() {
//				@Override
//				public Void call() throws EccoException {
//					PresenceCondition pc = AssociationDetailView.this.service.parsePresenceConditionString(AssociationDetailView.this.associationPC.getText());
//					AssociationDetailView.this.currentAssociation.setPresenceCondition(pc);
//					AssociationDetailView.this.service.updateAssociation(AssociationDetailView.this.currentAssociation);
//					return null;
//				}
//
//				public void finished() {
//					AssociationDetailView.this.showAssociation(AssociationDetailView.this.currentAssociation);
//					AssociationDetailView.this.toolBar.setDisable(false);
//				}
//
//				@Override
//				public void succeeded() {
//					super.succeeded();
//					this.finished();
//				}
//
//				@Override
//				public void cancelled() {
//					super.cancelled();
//				}
//
//				@Override
//				public void failed() {
//					super.failed();
//					this.finished();
//
//					ExceptionAlert alert = new ExceptionAlert(this.getException());
//					alert.setTitle("Checkout Error");
//					alert.setHeaderText("Checkout Error");
//
//					alert.showAndWait();
//				}
//			};
//
//			new Thread(updateTask).start();
//		});


		splitPane.getItems().add(associationDetails);


		// containment table: artifact tree of the selected association
		this.artifactTreeView = new ArtifactSnippetTreeView();

		splitPane.getItems().add(this.artifactTreeView);


		// show nothing initially
		this.showAssociation(null);
	}


	public final void showAssociation(Association association) {
		this.currentAssociation = association;

		if (association != null) {
			this.setCenter(this.splitPane);
			this.toolBar.setDisable(false);

			// show details
			this.associationId.setText(String.valueOf(association.getId()));
			this.associationPC.setText(association.computeCondition().toString());

			// show containment table
			this.artifactTreeView.setRootNode(association.getRootNode());
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.associationId.setText("");
			this.associationPC.setText("");

			this.artifactTreeView.setRootNode(null);
		}
	}

}
