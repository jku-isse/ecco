package at.jku.isse.ecco.gui;

import javafx.application.Platform;

/**
 * Entry point for pushing a message onto the app's single {@link NotificationPanel} from
 * anywhere - views, background tasks, event handlers - without threading a panel reference
 * through every call site. {@link #install(NotificationPanel)} is called once at startup (see
 * EccoGui); calls made before that (or from a headless/test context) are silently ignored rather
 * than failing.
 */
public final class Notifications {

	private static volatile NotificationPanel panel;

	private Notifications() {
	}

	public static void install(NotificationPanel panel) {
		Notifications.panel = panel;
	}

	public static void success(String message) {
		show(NotificationPanel.Type.SUCCESS, message);
	}

	public static void error(String message) {
		show(NotificationPanel.Type.ERROR, message);
	}

	public static void info(String message) {
		show(NotificationPanel.Type.INFO, message);
	}

	private static void show(NotificationPanel.Type type, String message) {
		NotificationPanel currentPanel = panel;
		if (currentPanel == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			currentPanel.show(type, message);
		} else {
			Platform.runLater(() -> currentPanel.show(type, message));
		}
	}

}
