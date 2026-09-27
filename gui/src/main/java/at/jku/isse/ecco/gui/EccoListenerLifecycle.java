package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * Ties an {@link EccoListener}'s registration to the lifetime of the window showing a view.
 * <p>
 * Views that live in their own, repeatedly opened window (the server log in Preferences, the
 * commit comparison) registered themselves with the service in their constructor and never
 * unregistered: every time the window was opened another listener leaked, and the closed views
 * kept receiving - and reacting to - service events for the rest of the session.
 */
public final class EccoListenerLifecycle {

	private EccoListenerLifecycle() {
	}

	/**
	 * Removes the given listeners from the service once the window that {@code view} ends up in is
	 * hidden. The listeners are expected to have been added already.
	 */
	public static void removeWhenWindowHidden(Node view, EccoService service, EccoListener... listeners) {
		Runnable remove = () -> {
			for (EccoListener listener : listeners)
				service.removeListener(listener);
		};
		view.sceneProperty().addListener((observable, oldScene, scene) -> onWindowOf(scene, remove));
		onWindowOf(view.getScene(), remove);
	}

	private static void onWindowOf(Scene scene, Runnable remove) {
		if (scene == null)
			return;
		if (scene.getWindow() != null)
			scene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> remove.run());
		else
			scene.windowProperty().addListener((observable, oldWindow, window) -> {
				if (window != null)
					window.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> remove.run());
			});
	}
}
