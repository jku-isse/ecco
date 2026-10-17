package at.jku.isse.ecco.gui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.HashMap;
import java.util.Map;

/**
 * Opens the application's dialog windows, one way for all: over their owner, with Cmd+W (close),
 * Cmd+Q (quit) and Esc, at the size the user last gave a window of the same title in this session.
 * The main window opened its dialogs one way, the Variants tab and the commit comparison theirs -
 * without an owner, or without the shortcuts.
 */
public final class Dialogs {

	/** Stage property set when a remembered size was given: the dialog's own fitting then only grows it. */
	public static final String SIZE_GIVEN = "ecco.dialogSizeGiven";

	/** Width and height by title, for this session - not stored: the tests open dialogs too. */
	private static final Map<String, double[]> SIZES = new HashMap<>();

	private Dialogs() {
	}

	/** Opens [content] in a window titled [title] over [owner] (null: none), [modality] as given. */
	public static Stage open(Window owner, String title, Parent content, Modality modality) {
		Stage dialog = new Stage();
		dialog.initStyle(StageStyle.UTILITY);
		dialog.initModality(modality);
		if (owner != null)
			dialog.initOwner(owner);

		Scene scene = new Scene(content);
		installStandardAccelerators(scene);
		// Esc closes - an operation dialog asks first while it works (OperationView handles it)
		scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode() == KeyCode.ESCAPE && !event.isConsumed()) {
				event.consume();
				if (dialog.getOnCloseRequest() == null)
					dialog.close();
			}
		});
		dialog.setScene(scene);
		dialog.setTitle(title);

		double[] size = SIZES.get(title);
		if (size != null) {
			dialog.setWidth(size[0]);
			dialog.setHeight(size[1]);
			dialog.getProperties().put(SIZE_GIVEN, true);
		}
		dialog.setOnHidden(event -> SIZES.put(title, new double[]{dialog.getWidth(), dialog.getHeight()}));

		dialog.show();
		dialog.requestFocus();
		return dialog;
	}

	/**
	 * Makes the focused window [dialog]'s owner, so it opens over the window it is about - message
	 * boxes had none and opened in the middle of the main screen. No owner if no window has the focus.
	 */
	public static void ownByFocusedWindow(javafx.scene.control.Dialog<?> dialog) {
		Window.getWindows().stream().filter(w -> w.isShowing() && w.isFocused()).findFirst()
				.ifPresent(owner -> {
					try {
						dialog.initOwner(owner);
					} catch (IllegalStateException alreadyShown) {
						// shown before: it keeps where it is
					}
				});
	}

	/**
	 * Cmd+Q quits the whole app - also while a dialog, not the main window, has the focus. Cmd+W closes
	 * the window of [scene] - the main window replaces this (see {@code MainView}): closing it would quit.
	 * Ctrl on Windows and Linux (SHORTCUT_DOWN is the platform's shortcut modifier).
	 */
	public static void installStandardAccelerators(Scene scene) {
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), Platform::exit);
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
				() -> ((Stage) scene.getWindow()).close());
	}
}
