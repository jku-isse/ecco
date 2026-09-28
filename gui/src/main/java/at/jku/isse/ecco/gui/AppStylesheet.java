package at.jku.isse.ecco.gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * ecco.css for every window of the app, not just the main one: dialogs, alerts and previews each
 * create their own Scene, and without it they fell back to the Cupertino theme's defaults - a 14px
 * font and 3em table rows, so a table of one-line configurations looked double-spaced next to the
 * same table in the main window.
 */
final class AppStylesheet {

	static final String FILE = "ecco.css";

	private AppStylesheet() {
	}

	/** Applies ecco.css to all current and future windows (and to scenes they switch to). */
	static void applyToAllWindows() {
		Window.getWindows().forEach(AppStylesheet::apply);
		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next())
				change.getAddedSubList().forEach(AppStylesheet::apply);
		});
	}

	private static void apply(Window window) {
		addTo(window.getScene());
		window.sceneProperty().addListener((observable, oldScene, newScene) -> addTo(newScene));
	}

	private static void addTo(Scene scene) {
		if (scene != null && !scene.getStylesheets().contains(FILE))
			scene.getStylesheets().add(FILE);
	}
}
