package at.jku.isse.ecco.gui;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionTextArea extends TextArea {

	public ExceptionTextArea() {
		this.setEditable(false);
		this.setWrapText(true);
		this.setMaxWidth(Double.MAX_VALUE);
		this.setMaxHeight(Double.MAX_VALUE);

		GridPane.setVgrow(this, Priority.ALWAYS);
		GridPane.setHgrow(this, Priority.ALWAYS);
	}

	public ExceptionTextArea(Throwable ex) {
		this();
		this.showException(ex);
	}


	public void showException(Throwable ex) {
		if (ex != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String exceptionText = sw.toString();

			this.setText(exceptionText);
		} else {
			this.setText("");
		}
	}

}
