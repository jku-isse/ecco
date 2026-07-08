package at.jku.isse.ecco.gui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A floating stack of transient, auto-dismissing notification cards, meant to replace blocking
 * "OK to continue" dialogs (JavaFX {@code Alert}, or {@link at.jku.isse.ecco.gui.view.operation.OperationView}'s
 * step-success screen) for messages that only confirm something worked and don't need explicit
 * acknowledgement. Mount once as an overlay on top of the rest of the UI (see EccoGui) and drive
 * it via {@link Notifications} from anywhere, rather than threading a reference through every view.
 */
public class NotificationPanel extends VBox {

	public enum Type {
		SUCCESS("notification-success"),
		ERROR("notification-error"),
		INFO("notification-info");

		private final String styleClass;

		Type(String styleClass) {
			this.styleClass = styleClass;
		}
	}

	private static final Duration VISIBLE_DURATION = Duration.seconds(4);
	private static final Duration FADE_DURATION = Duration.millis(300);

	public NotificationPanel() {
		this.setSpacing(8);
		this.setPadding(new Insets(16));
		this.setAlignment(Pos.BOTTOM_RIGHT);
		this.setMaxWidth(360);
		this.setMaxHeight(USE_PREF_SIZE);
		// let clicks in the (mostly empty) overlay area fall through to the view underneath -
		// individual notification cards below still receive their own clicks normally
		this.setMouseTransparent(true);
		this.setPickOnBounds(false);
	}

	public void show(Type type, String message) {
		HBox card = new HBox(8);
		card.getStyleClass().addAll("notification-card", type.styleClass);
		card.setAlignment(Pos.CENTER_LEFT);
		card.setPadding(new Insets(10, 12, 10, 12));
		card.setMouseTransparent(false);
		card.setMaxWidth(Double.MAX_VALUE);

		Label label = new Label(message);
		label.setWrapText(true);
		HBox.setHgrow(label, Priority.ALWAYS);

		Button closeButton = new Button("✕");
		closeButton.getStyleClass().add("notification-close");

		card.getChildren().addAll(label, closeButton);

		closeButton.setOnAction(event -> dismiss(card));

		this.getChildren().add(card);

		PauseTransition pause = new PauseTransition(VISIBLE_DURATION);
		pause.setOnFinished(event -> dismiss(card));
		pause.play();
	}

	private void dismiss(HBox card) {
		if (!this.getChildren().contains(card)) {
			return;
		}
		FadeTransition fade = new FadeTransition(FADE_DURATION, card);
		fade.setFromValue(1);
		fade.setToValue(0);
		fade.setOnFinished(event -> this.getChildren().remove(card));
		fade.play();
	}

}
