package at.jku.isse.ecco.gui;

import javafx.concurrent.Task;
import javafx.scene.Node;

/**
 * Failure handling for the background tasks behind toolbar actions. Most views disable their
 * toolbar, start a Task, and re-enable the toolbar at the end of call() - so any exception left the
 * toolbar disabled until the application was restarted, with nothing shown to the user.
 */
public final class TaskFailures {

	private TaskFailures() {
	}

	/**
	 * On failure of {@code task}: re-enables {@code controls} and shows the exception. Must be called
	 * before the task is started.
	 */
	public static void reportAndReenable(Task<?> task, Node... controls) {
		task.setOnFailed(event -> {
			for (Node control : controls)
				control.setDisable(false);
			new ExceptionAlert(task.getException()).show();
		});
	}
}
