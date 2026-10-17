package at.jku.isse.ecco.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionAlert extends Alert {

	public ExceptionAlert(Throwable ex) {
		super(AlertType.ERROR);
		Dialogs.ownByFocusedWindow(this);

		this.setContentText(describe(ex));

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String exceptionText = sw.toString();

		TextArea textArea = new TextArea(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(true);
		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);

		GridPane expContent = new GridPane();
		expContent.setMaxWidth(Double.MAX_VALUE);
		expContent.add(textArea, 0, 0);

		GridPane.setVgrow(textArea, Priority.ALWAYS);
		GridPane.setHgrow(textArea, Priority.ALWAYS);

		this.getDialogPane().setExpandableContent(expContent);
	}


	/**
	 * The messages of {@code ex} and its causes, one per line - the top one alone often only said
	 * what failed ("Error when retrieving repository."), and why was hidden in the details; a
	 * message already contained in an earlier one is left out.
	 */
	public static String describe(Throwable ex) {
		java.util.List<String> messages = new java.util.ArrayList<>();
		for (Throwable t = ex; t != null; t = t.getCause()) {
			String message = t.getMessage() == null ? null : t.getMessage().strip();
			if (message == null || message.isEmpty())
				continue;
			if (messages.stream().noneMatch(earlier -> earlier.contains(message)))
				messages.add(message);
		}
		return messages.isEmpty() ? ex.toString() : String.join("\n", messages);
	}
}
