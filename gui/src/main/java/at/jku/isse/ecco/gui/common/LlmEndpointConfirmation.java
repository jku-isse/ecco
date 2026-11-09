package at.jku.isse.ecco.gui.common;

import at.jku.isse.ecco.service.LlmPreferences;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.function.Predicate;

/**
 * Asks before code first goes to an LLM server that is not on the user's machine (NFR-S6d, gap #90):
 * Import from Git and Suggest order call it before their first request. A server the user agrees to
 * is remembered ({@link LlmPreferences#confirmEndpoint}) until another one is set.
 */
public final class LlmEndpointConfirmation {

	/** Shows the question and says whether the user agreed; replaced by tests. */
	static Predicate<String> asker = LlmEndpointConfirmation::showDialog;

	private LlmEndpointConfirmation() {
	}

	/** Whether code may go to [endpointUrl]: it is on this machine, was confirmed before, or the user agrees now. */
	public static boolean confirm(String endpointUrl) {
		if (LlmPreferences.mayReceiveCode(endpointUrl))
			return true;
		if (!asker.test(LlmPreferences.remoteEndpointWarning(endpointUrl)))
			return false;
		LlmPreferences.confirmEndpoint(endpointUrl);
		return true;
	}

	private static boolean showDialog(String warning) {
		ButtonType send = new ButtonType("Send to this server", ButtonBar.ButtonData.OK_DONE);
		Alert alert = new OwnedAlert(Alert.AlertType.WARNING, warning + "\n\nSend code to it? You are not asked again for this server.",
				send, ButtonType.CANCEL);
		alert.setHeaderText("Send code to another machine?");
		return alert.showAndWait().filter(send::equals).isPresent();
	}
}
