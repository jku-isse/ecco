package at.jku.isse.ecco.gui.view.operation;


import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class OperationView extends BorderPane {


	public OperationView() {
		super();
		//Scene scene = this.getScene();
		this.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				((Stage) this.getScene().getWindow()).close();
			}
		});

		this.setMinWidth(400);
		//this.setMinHeight(200);


//		// toolbar bottom
//		ToolBar toolBarBottom = new ToolBar();
//		this.setBottom(toolBarBottom);
	}


	protected void fit() {
		this.autosize();
		if (this.getScene() != null && this.getScene().getWindow() != null)
			this.getScene().getWindow().sizeToScene();
	}


	protected void stepSuccess(String text) {
		// toolbar top
		ToolBar toolBar = new ToolBar();
		toolBar.setStyle("-fx-base: #00cc99;");
		toolBar.getStyleClass().add("success");
		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		Button finishButton = new Button("Done");
		finishButton.getStyleClass().add("success");

		finishButton.setOnAction(event1 -> {
			((Stage) this.getScene().getWindow()).close();
		});

		toolBar.getItems().setAll(spacerLeft, finishButton);

		this.setTop(toolBar);


		// main content
		Label label = new Label(text);
		label.setPadding(new Insets(10));
		this.setCenter(label);


		this.fit();
	}


	protected void stepError(String text, Throwable ex) {
		// toolbar top
		ToolBar toolBar = new ToolBar();
		toolBar.setStyle("-fx-base: #ff6666;");
		toolBar.getStyleClass().add("error");
		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		Button finishButton = new Button("Done");
		finishButton.getStyleClass().add("error");

		finishButton.setOnAction(event1 -> {
			((Stage) this.getScene().getWindow()).close();
		});

		toolBar.getItems().setAll(spacerLeft, finishButton);

		this.setTop(toolBar);


		// main content
		GridPane gridPane = new GridPane();
		gridPane.setMaxWidth(Double.MAX_VALUE);
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		Label label = new Label(text);
		gridPane.add(label, 0, 0);

		if (ex != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String exceptionText = sw.toString();

			TextArea textArea = new TextArea(exceptionText);
			textArea.setEditable(false);
			textArea.setWrapText(true);
			textArea.setMaxWidth(Double.MAX_VALUE);
			textArea.setMaxHeight(Double.MAX_VALUE);

			GridPane.setVgrow(textArea, Priority.ALWAYS);
			GridPane.setHgrow(textArea, Priority.ALWAYS);

			gridPane.add(textArea, 0, 1);
		}

		this.setCenter(gridPane);


		this.fit();
	}

}
