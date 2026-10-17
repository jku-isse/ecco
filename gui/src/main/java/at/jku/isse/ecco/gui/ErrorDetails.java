package at.jku.isse.ecco.gui;

import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A failure in words - its causes, as {@link ExceptionAlert#describe} reads them - with the stack
 * trace folded away under Details. Dialogs showed the stack trace alone, with no summary.
 */
public class ErrorDetails extends VBox {

	private final Label summary;
	private final TitledPane details;
	private final ExceptionTextArea stackTrace;

	public ErrorDetails(Throwable exception) {
		super(8);
		this.summary = new Label(exception == null ? "" : ExceptionAlert.describe(exception));
		this.summary.setWrapText(true);
		this.summary.setStyle("-fx-text-fill: firebrick;");
		this.stackTrace = new ExceptionTextArea(exception);
		this.stackTrace.setPrefRowCount(12);
		this.details = new TitledPane("Details", this.stackTrace);
		this.details.setExpanded(false);
		this.details.setAnimated(false);
		this.getChildren().addAll(this.summary, this.details);
		this.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(this, Priority.ALWAYS);
		GridPane.setVgrow(this, Priority.ALWAYS);
	}

	public String summary() {
		return this.summary.getText();
	}

	public String stackTrace() {
		return this.stackTrace.getText();
	}

	public boolean isExpanded() {
		return this.details.isExpanded();
	}
}
