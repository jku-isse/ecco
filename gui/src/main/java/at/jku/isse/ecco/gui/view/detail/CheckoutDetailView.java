package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.ModuleRevisions;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class CheckoutDetailView extends BorderPane {

	private EccoService service;

	private Checkout currentCheckout;

	final ObservableList<DiagnosticInfo> warningsData = FXCollections.observableArrayList();


	private Pane centerPane;
	private ToolBar toolBar;


	private TextField checkoutConfiguration;


	public CheckoutDetailView(EccoService service) {
		this.service = service;

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

		RowConstraints emptyRowConstraint = new RowConstraints();
		RowConstraints heightRowConstraint = new RowConstraints();
		heightRowConstraint.setVgrow(Priority.ALWAYS);
		heightRowConstraint.setFillHeight(true);
		detailsPane.getRowConstraints().addAll(emptyRowConstraint, emptyRowConstraint, heightRowConstraint);


		this.checkoutConfiguration = new TextField();
		this.checkoutConfiguration.setEditable(false);

		int row = 0;
		detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.checkoutConfiguration, 1, row, 1, 1);
		row++;


		// list of missing/surplus module diagnostics
		TableView<DiagnosticInfo> warningsTable = new TableView<>();
		warningsTable.setEditable(false);
		warningsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<DiagnosticInfo, String> typeCol = new TableColumn<>("Type");
		TableColumn<DiagnosticInfo, String> moduleCol = new TableColumn<>("Module");
		TableColumn<DiagnosticInfo, String> traceCol = new TableColumn<>("Trace");

		warningsTable.getColumns().setAll(typeCol, moduleCol, traceCol);

		typeCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getType()));
		moduleCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getModule()));
		traceCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getTrace()));

		warningsTable.setItems(this.warningsData);

		detailsPane.add(warningsTable, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showCheckout(null);
	}


	public final void showCheckout(Checkout checkout) {
		this.currentCheckout = checkout;

		this.warningsData.clear();

		if (checkout != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			if (checkout.getConfiguration() != null)
				this.checkoutConfiguration.setText(String.valueOf(checkout.getConfiguration().toString()));
			else
				this.checkoutConfiguration.setText("");

			// show missing and surplus module diagnostics (see Repository.Op.compose())
			List<ModuleRevision> sortedMissing = new ArrayList<>(checkout.getMissing());
			sortedMissing.sort(ModuleRevisions.RELEVANCE_ORDER);
			for (ModuleRevision missingModuleRevision : sortedMissing) {
				String location = checkout.getMissingLocations().getOrDefault(missingModuleRevision, "");
				CheckoutDetailView.this.warningsData.add(new DiagnosticInfo("MISSING", ModuleRevisions.describe(missingModuleRevision), location));
			}
			for (java.util.Map.Entry<ModuleRevision, String> surplusEntry : checkout.getSurplusModules().entrySet()) {
				CheckoutDetailView.this.warningsData.add(new DiagnosticInfo("SURPLUS", surplusEntry.getKey().toString(), surplusEntry.getValue()));
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.checkoutConfiguration.setText("");
		}
	}


	public static class DiagnosticInfo {
		private final String type;
		private final String module;
		private final String trace;

		public DiagnosticInfo(String type, String module, String trace) {
			this.type = type;
			this.module = module;
			this.trace = trace;
		}

		public String getType() {
			return this.type;
		}

		public String getModule() {
			return this.module;
		}

		public String getTrace() {
			return this.trace;
		}
	}

}
