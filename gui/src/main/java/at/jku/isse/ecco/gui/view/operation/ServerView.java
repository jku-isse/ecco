package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class ServerView extends OperationView implements EccoListener {

	private EccoService service;

	final ObservableList<String> logData = FXCollections.observableArrayList();

	private Spinner<Integer> portTextField;

	private boolean stopStep = false;


	public ServerView(EccoService service) {
		super();
		this.service = service;

		this.portTextField = new Spinner<>(0, Integer.MAX_VALUE, 3770);

		service.addListener(this);

		if (service.serverRunning())
			this.stepStop(-1);
		else
			this.stepStart();

//		this.setOnKeyPressed(event -> {
//			if (event.getCode() == KeyCode.ESCAPE && !this.stopStep) {
//				((Stage) this.getScene().getWindow()).close();
//			}
//		});
	}


	@Override
	public void serverEvent(EccoService service, String message) {
		this.logData.add(message);
	}

	@Override
	public void serverStartEvent(EccoService service, int port) {
		if (!this.stopStep)
			Platform.runLater(() -> this.stepStop(port));
	}

	@Override
	public void serverStopEvent(EccoService service) {
		if (this.stopStep)
			Platform.runLater(() -> this.stepStart());
	}


	private void stepStart() {
		this.stopStep = false;

		Button cancelButton = new Button("Cancel");
		cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		leftButtons.getChildren().setAll(cancelButton);

		headerLabel.setText("Server");

		Button startButton = new Button("Start");
		rightButtons.getChildren().setAll(startButton);


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

		Label portLabel = new Label("Port: ");
		gridPane.add(portLabel, 0, row, 1, 1);

		portTextField.setDisable(false);
		portTextField.setEditable(true);
		portTextField.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				Integer.parseInt(newValue);
				portTextField.increment(0);
			} catch (NumberFormatException e) {
			}
		}); // workaround for not committing value when editing value
		portLabel.setLabelFor(portTextField);
		gridPane.add(portTextField, 1, row, 1, 1);


		startButton.setOnAction(event -> {
			int port = portTextField.getValue();
			Task serverTask = new Task<Void>() {
				@Override
				public Void call() {
					ServerView.this.service.startServer(port);
					return null;
				}

				@Override
				public void failed() {
					super.failed();
					ServerView.this.stepError("Server error.", this.getException());
				}
			};
			new Thread(serverTask).start();
		});


		this.fit();
	}


	private void stepStop(int port) {
		this.stopStep = true;

		headerLabel.setText("Server running on port " + port);

		Button stopButton = new Button("Stop");
		stopButton.setOnAction(event -> this.service.stopServer());
		leftButtons.getChildren().setAll(stopButton);

		rightButtons.getChildren().clear();


		// main content

		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		ColumnConstraints col1constraint = new ColumnConstraints();
		col1constraint.setFillWidth(true);
		gridPane.getColumnConstraints().addAll(col1constraint);

		this.setCenter(gridPane);

		int row = 0;

		// progress bar
		ProgressBar progressBar = new ProgressBar();
		progressBar.setMaxWidth(Double.MAX_VALUE);
		//progressBar.setPrefWidth(Double.MAX_VALUE);
		progressBar.prefWidthProperty().bind(gridPane.widthProperty().subtract(20));
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		gridPane.add(progressBar, 0, row, 1, 1);
		row++;

		// server log
		TableView<String> fileTable = new TableView<>();
		fileTable.setEditable(false);
		fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<String, String> timeCol = new TableColumn<>("Time");
		TableColumn<String, String> messageCol = new TableColumn<>("Message");
		fileTable.getColumns().setAll(timeCol, messageCol);
		messageCol.setCellValueFactory((TableColumn.CellDataFeatures<String, String> param) -> new ReadOnlyStringWrapper(""));
		//messageCol.setCellValueFactory(new PropertyValueFactory<>("action"));
		fileTable.setItems(this.logData);

		TitledPane titledPane = new TitledPane();
		titledPane.setText("Log");
		titledPane.setCollapsible(false);
		titledPane.setContent(fileTable);
		titledPane.setMaxWidth(Double.MAX_VALUE);
		titledPane.setMaxHeight(Double.MAX_VALUE);
		gridPane.add(titledPane, 0, row, 1, 1);
		row++;


		this.fit();
	}

}
