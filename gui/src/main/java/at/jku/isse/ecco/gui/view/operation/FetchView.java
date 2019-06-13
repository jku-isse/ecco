package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Remote;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class FetchView extends OperationView {

	private EccoService service;


	public FetchView(EccoService service) {
		super();
		this.service = service;


		this.stepRemote();
	}


	private void stepRemote() {
		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event1 -> ((Stage) this.getScene().getWindow()).close());
		this.leftButtons.getChildren().setAll(cancelButton);

		this.headerLabel.setText("Remote");

		Button fetchButton = new Button("Fetch");
		this.rightButtons.getChildren().setAll(fetchButton);


		// main content

		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setMinWidth(GridPane.USE_PREF_SIZE);
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		this.setCenter(gridPane);

		int row = 0;

		Label remoteLabel = new Label("Remote: ");
		gridPane.add(remoteLabel, 0, row, 1, 1);

		ComboBox<Remote> remoteComboBox = new ComboBox<>();
		remoteLabel.setLabelFor(remoteComboBox);
		gridPane.add(remoteComboBox, 1, row, 1, 1);

		row++;


		remoteComboBox.getItems().add(null);
		for (Remote remote : this.service.getRemotes()) {
			remoteComboBox.getItems().add(remote);
		}


		fetchButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());


		fetchButton.setOnAction(event -> {
			this.setDisable(true);

			Remote remote = remoteComboBox.getValue();

			Task fetchTask = new Task<Void>() {
				@Override
				public Void call() {
					FetchView.this.service.fetch(remote.getName());
					return null;
				}

				@Override
				public void succeeded() {
					super.succeeded();
					FetchView.this.stepSuccess("Pull operation was successful.");
					FetchView.this.setDisable(false);
				}

				@Override
				public void cancelled() {
					super.cancelled();
					FetchView.this.stepError("Pull operation was cancelled.", this.getException());
					FetchView.this.setDisable(false);
				}

				@Override
				public void failed() {
					super.failed();
					FetchView.this.stepError("Error during pull operation.", this.getException());
					FetchView.this.setDisable(false);
				}
			};
			new Thread(fetchTask).start();
		});


		this.fit();
	}

}
