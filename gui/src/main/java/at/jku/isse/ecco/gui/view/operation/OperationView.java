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

import java.util.List;
import java.util.Stack;


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
		// Esc, and the window's close control, ask first while an operation runs (see mayClose)
		this.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				event.consume();
				if (this.mayClose())
					this.getScene().getWindow().hide();
			}
		});
		this.sceneProperty().addListener((o, oldScene, scene) -> {
			if (scene != null)
				scene.windowProperty().addListener((o2, oldWindow, window) -> this.watch(window));
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

		// Enter presses the rightmost button - the step's main action - and Esc a Cancel or Close at
		// the left: only a few dialogs set them, so Enter did nothing in Commit, Checkout, Fetch, ...
		rightButtons.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> this.markDefaultButton());
		leftButtons.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> this.markCancelButton());

		leftButtons.minWidthProperty().bind(rightButtons.widthProperty());
		rightButtons.minWidthProperty().bind(leftButtons.widthProperty());
		toolBar.getItems().setAll(leftButtons, spacerLeft, headerLabel, spacerRight, rightButtons);

		// a mistake in the form, said above it - the form stays (see showInputError)
		this.inputErrorLabel.setWrapText(true);
		this.inputErrorLabel.setStyle("-fx-text-fill: firebrick; -fx-padding: 6 10 0 10;");
		this.inputErrorLabel.setVisible(false);
		this.inputErrorLabel.setManaged(false);
		this.inputErrorLabel.setMaxWidth(Double.MAX_VALUE);
		this.setTop(new VBox(toolBar, this.inputErrorLabel));


//		// toolbar bottom
//		ToolBar toolBarBottom = new ToolBar();
//		this.setBottom(toolBarBottom);
	}


	private final Label inputErrorLabel = new Label();

	/**
	 * Says [text] above the form, which stays as it is - a mistake in it replaced it by an error
	 * screen, and the dialog had to be opened again. Cleared by {@link #clearInputError}.
	 */
	protected void showInputError(String text) {
		this.inputErrorLabel.setText(text);
		this.inputErrorLabel.setVisible(true);
		this.inputErrorLabel.setManaged(true);
		this.notRunning();
		if (this.getScene() != null && this.getScene().getWindow() != null)
			this.growToFit();
	}

	protected void clearInputError() {
		this.inputErrorLabel.setVisible(false);
		this.inputErrorLabel.setManaged(false);
	}

	/** "Error opening repository: Not an ECCO repository: ..." - [what] and the causes of [exception]. */
	protected void showInputError(String what, Throwable exception) {
		this.showInputError(what.replaceAll("\\.$", "") + ": " + at.jku.isse.ecco.gui.ExceptionAlert.describe(exception));
	}

	/** The error said above the form, null if there is none. Package-visible for testing. */
	String inputError() {
		return this.inputErrorLabel.isVisible() ? this.inputErrorLabel.getText() : null;
	}

	// ---- an operation running: closing the window asks first ------------------------------------

	/** What runs, "The commit" - null if nothing does. */
	private String runningWhat;
	/** What closing the window then adds to the question. */
	private String runningDetail;
	/** Stops it; null if it cannot be stopped - closing then lets it finish in the background. */
	private Runnable stopper;

	/** [what] runs from now; [stopper] stops it, or null if it cannot be stopped. */
	protected void running(String what, Runnable stopper) {
		this.running(what, null, stopper);
	}

	protected void running(String what, String detail, Runnable stopper) {
		this.runningWhat = what;
		this.runningDetail = detail;
		this.stopper = stopper;
	}

	/** Nothing runs any more: the window closes without asking. */
	protected void notRunning() {
		this.runningWhat = null;
		this.runningDetail = null;
		this.stopper = null;
	}

	/** [what] runs until [task] ends; it cannot be stopped. */
	protected void track(javafx.concurrent.Task<?> task, String what) {
		this.running(what, null);
		task.stateProperty().addListener((o, old, state) -> {
			if (state == javafx.concurrent.Worker.State.SUCCEEDED || state == javafx.concurrent.Worker.State.FAILED
					|| state == javafx.concurrent.Worker.State.CANCELLED)
				this.notRunning();
		});
	}

	/**
	 * Whether the window may close now: nothing runs, or the user said so - stopping what can be
	 * stopped. Closing went through at once, and the operation went on unseen.
	 */
	boolean mayClose() {
		if (this.runningWhat == null)
			return true;
		if (this.stopper != null) {
			if (!this.askToClose("Stop " + this.runningWhat + "?" + (this.runningDetail == null ? "" : " " + this.runningDetail), "Stop"))
				return false;
			Runnable stop = this.stopper;
			this.notRunning();
			stop.run();
			return true;
		}
		return this.askToClose(this.runningWhat + " is still running - it finishes even if you close this window. Close anyway?", "Close");
	}

	/** Asks [question]; true if the answer is [closeLabel], false to keep the window open. Package-visible for testing. */
	boolean askToClose(String question, String closeLabel) {
		javafx.scene.control.ButtonType close = new javafx.scene.control.ButtonType(closeLabel, javafx.scene.control.ButtonBar.ButtonData.OTHER);
		javafx.scene.control.ButtonType keep = new javafx.scene.control.ButtonType("Keep Open", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
		javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION, question, close, keep);
		alert.setHeaderText(null);
		if (this.getScene() != null && this.getScene().getWindow() != null)
			alert.initOwner(this.getScene().getWindow());
		((Button) alert.getDialogPane().lookupButton(keep)).setDefaultButton(true);
		((Button) alert.getDialogPane().lookupButton(close)).setDefaultButton(false);
		return alert.showAndWait().orElse(keep) == close;
	}

	/** Called once the window is closed - whatever still runs is the view's to end. */
	protected void closed() {
	}

	private void watch(javafx.stage.Window window) {
		if (window instanceof Stage stage) {
			stage.setOnCloseRequest(event -> {
				if (!this.mayClose())
					event.consume();
			});
			stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> this.closed());
		}
	}

	/** The prompt of a configuration field to check out or save - nothing said how to write one. */
	public static final String CONFIGURATION_HINT = "Features, comma-separated: A (its latest revision), A.3f2a9c1 (that revision)";
	/** The prompt of a configuration field to commit with. */
	public static final String COMMIT_CONFIGURATION_HINT = "Features, comma-separated: A, A.3f2a9c1 (that revision), A' (a new revision of A)";
	/** Added to {@link #COMMIT_CONFIGURATION_HINT} where a blank field reads the folder's .config file. */
	public static final String BLANK_READS_CONFIG_FILE = "; blank: the folder's .config file";

	/** The rightmost button of the toolbar's right side is the default one, no other. */
	private void markDefaultButton() {
		List<javafx.scene.Node> buttons = this.rightButtons.getChildren();
		Button last = null;
		for (javafx.scene.Node node : buttons)
			if (node instanceof Button button)
				last = button;
		for (javafx.scene.Node node : buttons)
			if (node instanceof Button button)
				button.setDefaultButton(button == last);
	}

	/** A Cancel or Close button at the toolbar's left is the cancel button. */
	private void markCancelButton() {
		for (javafx.scene.Node node : this.leftButtons.getChildren())
			if (node instanceof Button button)
				button.setCancelButton("Cancel".equals(button.getText()) || "Close".equals(button.getText()));
	}

	/**
	 * Fills [comboBox] with the repository's remotes - no empty entry - and selects the only one,
	 * or else "origin" if there is one: Fetch, Pull and Push started on an empty choice.
	 */
	protected static void fillRemotes(javafx.scene.control.ComboBox<at.jku.isse.ecco.core.Remote> comboBox,
									  java.util.Collection<? extends at.jku.isse.ecco.core.Remote> remotes) {
		comboBox.getItems().setAll(remotes);
		if (comboBox.getItems().size() == 1)
			comboBox.getSelectionModel().select(0);
		else
			comboBox.getItems().stream().filter(r -> "origin".equals(r.getName())).findFirst()
					.ifPresent(r -> comboBox.getSelectionModel().select(r));
	}

	/** Puts the keyboard focus on [node] once it is shown - the first field of a step. */
	protected static void focusWhenShown(javafx.scene.Node node) {
		Platform.runLater(node::requestFocus);
	}

	protected void fit() {
		// no autosize() first: it set this view to its preferred size, and where the window then
		// kept its size (macOS keeps a utility window's width when its content gets smaller) the
		// scene never laid the view out again - it stayed that narrow inside the wider window
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


	/**
	 * Like {@link #fit()}, but only ever makes the window larger (up to the screen), never smaller:
	 * for content that appears after the window is up (e.g. a result below a log), without undoing
	 * a size the user chose.
	 */
	protected void growToFit() {
		if (this.getScene() == null || !(this.getScene().getWindow() instanceof Stage stage))
			return;
		this.applyCss();
		this.layout();
		double decorationWidth = stage.getWidth() - this.getScene().getWidth();
		double decorationHeight = stage.getHeight() - this.getScene().getHeight();
		double width = this.prefWidth(-1) + decorationWidth;
		double height = this.prefHeight(width - decorationWidth) + decorationHeight;
		javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
		stage.setWidth(Math.min(Math.max(stage.getWidth(), width), screen.getWidth()));
		stage.setHeight(Math.min(Math.max(stage.getHeight(), height), screen.getHeight()));
		if (stage.getY() + stage.getHeight() > screen.getMaxY())
			stage.setY(Math.max(screen.getMinY(), screen.getMaxY() - stage.getHeight()));
		if (stage.getX() + stage.getWidth() > screen.getMaxX())
			stage.setX(Math.max(screen.getMinX(), screen.getMaxX() - stage.getWidth()));
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


	/**
	 * Like {@link #showSuccessHeader()}, for an operation the user cancelled: neither success nor
	 * error, {@code text} says what was done.
	 */
	protected void showCancelledHeader(String text) {
		this.leftButtons.getChildren().clear();

		this.headerLabel.setText(text);

		Button finishButton = new Button("Done");
		finishButton.setDefaultButton(true);
		finishButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
		this.rightButtons.getChildren().setAll(finishButton);
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
		this.notRunning();
		this.clearInputError();
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
			gridPane.add(new at.jku.isse.ecco.gui.ErrorDetails(ex), 0, 1);
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
