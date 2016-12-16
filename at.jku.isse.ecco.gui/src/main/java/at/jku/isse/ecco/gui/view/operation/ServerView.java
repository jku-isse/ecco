package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;

public class ServerView extends OperationView {

	private EccoService service;

	public ServerView(EccoService service) {
		super();
		this.service = service;


		// toolbar top
		ToolBar toolBar = new ToolBar();

		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		Label headerLabel = new Label("Server");

		toolBar.getItems().setAll(spacerLeft, headerLabel, spacerRight);

		this.setTop(toolBar);


		// main content

		Button startButton = new Button("Start");
		Button stopButton = new Button("Stop");


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

		ProgressBar progressBar = new ProgressBar();
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		gridPane.add(progressBar, 0, row, 1, 1);
		row++;

		gridPane.add(startButton, 1, row, 1, 1);
		row++;

		gridPane.add(stopButton, 1, row, 1, 1);
		row++;


		this.fit();
	}


}
