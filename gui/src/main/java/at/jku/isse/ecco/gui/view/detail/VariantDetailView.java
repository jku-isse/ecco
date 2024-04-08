package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;

public class VariantDetailView extends BorderPane {

	private EccoService service;

	private Variant variantCommit;

	private Pane centerPane;
	private ToolBar toolBar;


	private TextField variantId;
	private TextField variantName;
	private TextField variantConfiguration;

	public VariantDetailView(EccoService service) {
		this.service = service;


		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button updateButton = new Button("Update");
		toolBar.getItems().add(updateButton);
		updateButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task variantsUpdateTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Platform.runLater(() -> {
							String configuration = variantConfiguration.getText();
							String name = variantName.getText();
							String id = variantId.getText();
							Configuration config = VariantDetailView.this.service.parseConfigurationString(configuration);
							VariantDetailView.this.service.updateVariant(config,name,id, VariantDetailView.this.service);
						});
						Platform.runLater(() -> toolBar.setDisable(false));
						return null;
					}
				};

				new Thread(variantsUpdateTask).start();
			}
		});

		Button removeButton = new Button("Remove");
		toolBar.getItems().add(removeButton);
		removeButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task variantsRemoveTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Platform.runLater(() -> {
							String configuration = variantConfiguration.getText();
							Configuration config = VariantDetailView.this.service.parseConfigurationString(configuration);
							VariantDetailView.this.service.removeVariant(config);
						});
						Platform.runLater(() -> toolBar.setDisable(false));
						return null;
					}
				};

				new Thread(variantsRemoveTask).start();
			}
		});

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

		this.variantId = new TextField();
		this.variantId.setEditable(false);
		this.variantName = new TextField();
		this.variantName.setEditable(true);
		this.variantConfiguration = new TextField();
		this.variantConfiguration.setEditable(true);


		int row = 0;
		detailsPane.add(new Label("Id: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.variantId, 1, row, 1, 1);
		row++;

		detailsPane.add(new Label("Name: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.variantName, 1, row, 1, 1);
		row++;

		detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.variantConfiguration, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showVariant(null);
	}



	public void showVariant(Variant variant) {

		if (variant != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			this.variantId.setText(String.valueOf(variant.getId()));
			this.variantName.setText(String.valueOf(variant.getName()));
			this.variantConfiguration.setText(variant.getConfiguration() == null ? "" : variant.getConfiguration().toString());

		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.variantId.setText("");
			this.variantName.setText("");
			this.variantConfiguration.setText("");
		}
	}

	public void addVariant() {
		this.setCenter(this.centerPane);
		this.toolBar.setDisable(false);

		this.variantName.setText("");
		this.variantConfiguration.setText("");

	}

}
