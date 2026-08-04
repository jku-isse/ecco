package at.jku.isse.ecco.gui.view.operation;


import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.Notifications;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;

import java.util.Stack;

// TODO: forward, backward, enter, escape, etc.

public abstract class OperationView extends BorderPane {

	public interface StepActivator {
		public void activate();
	}

	protected Stack<StepActivator> steps = new Stack<>();

	protected void pushStep(StepActivator step) {
		step.activate();
		this.steps.push(step);
	}

	protected void popStep() {
		if (this.steps.isEmpty())
			((Stage) this.getScene().getWindow()).close();
		else {
			this.steps.pop(); // pop current one
			if (this.steps.isEmpty())
				((Stage) this.getScene().getWindow()).close(); // close window
			else
				this.steps.peek().activate(); // activate previous one
		}
	}

	/**
	 * Used when there is no going back to the previous step (e.g. after having performed an operation).
	 */
	protected void clearSteps() {
		this.steps.clear();
	}


	// -----------------------------------------------------------------------------------------------------------------


	protected ToolBar toolBar;
	protected HBox leftButtons;
	protected HBox rightButtons;
	protected Label headerLabel;

	public OperationView() {
		super();
		//Scene scene = this.getScene();
		this.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				((Stage) this.getScene().getWindow()).close();
				//this.popStep();
			}
		});

		this.setMinWidth(600);
		//this.setMinHeight(200);


		// toolbar top
		this.toolBar = new ToolBar();

		final Pane spacerLeft = new Pane();
		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
		final Pane spacerRight = new Pane();
		HBox.setHgrow(spacerRight, Priority.SOMETIMES);

		leftButtons = new HBox();
		leftButtons.setAlignment(Pos.CENTER_LEFT);
		leftButtons.setSpacing(10);

		headerLabel = new Label();

		rightButtons = new HBox();
		rightButtons.setAlignment(Pos.CENTER_RIGHT);
		rightButtons.setSpacing(10);

		leftButtons.minWidthProperty().bind(rightButtons.widthProperty());
		rightButtons.minWidthProperty().bind(leftButtons.widthProperty());
		toolBar.getItems().setAll(leftButtons, spacerLeft, headerLabel, spacerRight, rightButtons);

		this.setTop(toolBar);


//		// toolbar bottom
//		ToolBar toolBarBottom = new ToolBar();
//		this.setBottom(toolBarBottom);
	}


	protected void fit() {
		this.autosize();
		if (this.getScene() != null && this.getScene().getWindow() != null) {
			this.getScene().getWindow().sizeToScene();
		} else {
			// Not just "not ready yet, try again next pulse": showing a new Stage can itself
			// trigger a nested pump of this same runLater queue (seen in practice as a one-time
			// cost on the very first additional Stage.show() after app startup, e.g. opening
			// Settings right away), which can drain this callback before the caller (openDialog)
			// has gotten to attach the Scene/Window at all. A single deferred retry isn't
			// guaranteed to land after that - keep rescheduling until getScene()/getWindow() are
			// actually non-null, however many pumps that takes, instead of assuming exactly one.
			Platform.runLater(this::fit);
		}
	}


	protected void showSuccessHeader() {
		this.toolBar.setStyle("-fx-base: #00cc99;");
		this.toolBar.getStyleClass().add("success");

		this.leftButtons.getChildren().clear();

		this.headerLabel.setText("");

		Button finishButton = new Button("Done");
		finishButton.setDefaultButton(true);
		finishButton.getStyleClass().add("success");
		finishButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.rightButtons.getChildren().setAll(finishButton);
	}

	/**
	 * Closes this operation's dialog immediately and reports success via the non-blocking
	 * {@link Notifications} panel, instead of replacing the dialog's content with a message the
	 * user has to click "Done" to dismiss before they can go back to using the app. Steps that show
	 * richer content on success (e.g. a summary) should call {@link #showSuccessHeader()} directly
	 * instead, to keep that content on screen.
	 */
	protected void stepSuccess(String text) {
		Notifications.success(text);
		((Stage) this.getScene().getWindow()).close();
	}


	protected void showErrorHeader() {
		this.toolBar.setStyle("-fx-base: #ff6666;");
		this.toolBar.getStyleClass().add("error");

		this.leftButtons.getChildren().clear();

		this.headerLabel.setText("");

		Button finishButton = new Button("Done");
		finishButton.getStyleClass().add("error");
		finishButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.rightButtons.getChildren().setAll(finishButton);
	}

	protected void stepError(String text, Throwable ex) {
//		this.clearSteps();

//		// toolbar top
//		ToolBar toolBar = new ToolBar();
//		toolBar.setStyle("-fx-base: #ff6666;");
//		toolBar.getStyleClass().add("error");
//		final Pane spacerLeft = new Pane();
//		HBox.setHgrow(spacerLeft, Priority.SOMETIMES);
//		final Pane spacerRight = new Pane();
//		HBox.setHgrow(spacerRight, Priority.SOMETIMES);
//
//		Button finishButton = new Button("Done");
//		finishButton.getStyleClass().add("error");
//
//		finishButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
//
//		toolBar.getItems().setAll(spacerLeft, finishButton);
//
//		this.setTop(toolBar);

		this.showErrorHeader();


		// main content
		GridPane gridPane = new GridPane();
		gridPane.setMaxWidth(Double.MAX_VALUE);
		gridPane.setHgap(10);
		gridPane.setVgap(10);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		Label label = new Label(text);
		gridPane.add(label, 0, 0);

		if (ex != null) {
//			StringWriter sw = new StringWriter();
//			PrintWriter pw = new PrintWriter(sw);
//			ex.printStackTrace(pw);
//			String exceptionText = sw.toString();
//
//			TextArea textArea = new TextArea(exceptionText);
//			textArea.setEditable(false);
//			textArea.setWrapText(true);
//			textArea.setMaxWidth(Double.MAX_VALUE);
//			textArea.setMaxHeight(Double.MAX_VALUE);
//
//			GridPane.setVgrow(textArea, Priority.ALWAYS);
//			GridPane.setHgrow(textArea, Priority.ALWAYS);
//
//			gridPane.add(textArea, 0, 1);
			gridPane.add(new ExceptionTextArea(ex), 0, 1);
		}

		this.setCenter(gridPane);


		this.fit();
	}


	/**
	 * {@link TextFieldTableCell#forTableColumn()}'s edit only commits on Enter (its inner text
	 * field has no focus-lost handling) - clicking straight from the field to another control, e.g.
	 * this dialog's own "Commit"/"Import" button, silently discards whatever was typed instead of
	 * committing it. Use this in place of {@code TextFieldTableCell.forTableColumn()} for any
	 * editable string column whose value feeds into an action button, so a typed-but-not-Entered
	 * edit is never lost.
	 */
	protected static <S> Callback<TableColumn<S, String>, TableCell<S, String>> editableStringCellFactory() {
		return column -> new TextFieldTableCell<S, String>(new DefaultStringConverter()) {
			@Override
			public void startEdit() {
				super.startEdit();
				if (this.getGraphic() instanceof TextField textField) {
					textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
						if (!isNowFocused && this.isEditing()) {
							this.commitEdit(textField.getText());
						}
					});
				}
			}
		};
	}

}
