package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Warning;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class CheckoutDetailView extends BorderPane {

	private EccoService service;

	private Checkout currentCheckout;

	final ObservableList<WarningInfo> warningsData = FXCollections.observableArrayList();


	private Pane centerPane;
	private ToolBar toolBar;


	private TextField checkoutConfiguration;


	public CheckoutDetailView(EccoService service) {
		this.service = service;

		this.currentCheckout = null;


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


		// list of warnings
		TableView<WarningInfo> warningsTable = new TableView<>();
		warningsTable.setEditable(false);
		warningsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<WarningInfo, String> moduleWarningsCol = new TableColumn<>("Module");
		TableColumn<WarningInfo, String> warningsCol = new TableColumn<>("Warnings");

		warningsCol.getColumns().setAll(moduleWarningsCol);
		warningsTable.getColumns().setAll(warningsCol);

		moduleWarningsCol.setCellValueFactory((TableColumn.CellDataFeatures<WarningInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().toString()));

		warningsTable.setItems(this.warningsData);

		detailsPane.add(warningsTable, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showCheckout(null);
	}


	public void showCheckout(Checkout checkout) {
		this.currentCheckout = checkout;

		this.warningsData.clear();

		if (checkout != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			if (checkout.getConfiguration() != null)
				this.checkoutConfiguration.setText(String.valueOf(checkout.getConfiguration().toString()));
			else
				this.checkoutConfiguration.setText("");

			// show warnings
			for (Warning warning : checkout.getWarnings()) {
				CheckoutDetailView.this.warningsData.add(new WarningInfo(warning));
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.checkoutConfiguration.setText("");
		}
	}


	public static class WarningInfo {
		private Warning warning;

		public WarningInfo(Warning warning) {
			this.warning = warning;
		}

		public Warning getWarning() {
			return this.warning;
		}
	}

}
