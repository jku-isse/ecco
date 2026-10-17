package at.jku.isse.ecco.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** An {@link Alert} over the window that has the focus - see {@link Dialogs#ownByFocusedWindow}. */
public class OwnedAlert extends Alert {

	public OwnedAlert(AlertType alertType) {
		super(alertType);
		Dialogs.ownByFocusedWindow(this);
	}

	public OwnedAlert(AlertType alertType, String contentText, ButtonType... buttons) {
		super(alertType, contentText, buttons);
		Dialogs.ownByFocusedWindow(this);
	}
}
