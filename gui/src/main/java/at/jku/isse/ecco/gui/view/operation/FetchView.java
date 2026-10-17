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


		fillRemotesLater(this.service, remoteComboBox);
		focusWhenShown(remoteComboBox);


		fetchButton.disableProperty().bind(remoteComboBox.getSelectionModel().selectedItemProperty().isNull());


		fetchButton.setOnAction(event -> {
			// the form only - the window was disabled, and a Cancel with it
			gridPane.setDisable(true);
			fetchButton.disableProperty().unbind();
			fetchButton.setDisable(true);
			java.util.concurrent.atomic.AtomicBoolean cancelRequested = new java.util.concurrent.atomic.AtomicBoolean();
			Button stopButton = new Button("Cancel");
			stopButton.setOnAction(e -> {
				cancelRequested.set(true);
				stopButton.setDisable(true);
				this.headerLabel.setText("Stopping before anything is written...");
			});
			this.leftButtons.getChildren().setAll(stopButton);
			this.headerLabel.setText("Fetching ...");

			Remote remote = remoteComboBox.getValue();

			Task<java.util.List<String>> fetchTask = new Task<>() {
				@Override
				public java.util.List<String> call() {
					at.jku.isse.ecco.core.Remote known = FetchView.this.service.getRemote(remote.getName());
					java.util.Set<String> before = known == null ? new java.util.HashSet<>() : TransferView.revisions(known.getFeatures());
					FetchView.this.service.fetch(remote.getName(), cancelRequested::get);
					java.util.Set<String> after = TransferView.revisions(FetchView.this.service.getRemote(remote.getName()).getFeatures());
					after.removeAll(before);
					return after.stream().sorted().toList();
				}

				@Override
				public void succeeded() {
					super.succeeded();
					// what came over - it said "Fetch operation was successful."
					FetchView.this.stepSuccess(TransferView.transferred("Fetch", "from", remote.getName(), this.getValue()));
				}

				@Override
				public void failed() {
					super.failed();
					FetchView.this.notRunning();
					if (at.jku.isse.ecco.service.OperationCancelledException.isCause(this.getException()))
						FetchView.this.showCancelledHeader("Cancelled - nothing was changed.");
					else
						FetchView.this.stepError("Error during fetch operation.", this.getException());
				}
			};
			this.track(fetchTask, "The fetch");
			this.running("the fetch", "Nothing is changed.", () -> cancelRequested.set(true));
			new Thread(fetchTask).start();
		});


		this.fit();
	}

}
