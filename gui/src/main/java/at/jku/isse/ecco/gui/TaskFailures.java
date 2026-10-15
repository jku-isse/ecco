package at.jku.isse.ecco.gui;

import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.function.Consumer;

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
		reportAndReenable(task, exception -> new ExceptionAlert(exception).show(), controls);
	}

	/** As above, with the exception going to {@code report} instead of a dialog (tests). */
	static void reportAndReenable(Task<?> task, Consumer<Throwable> report, Node... controls) {
		task.setOnFailed(event -> {
			for (Node control : controls)
				control.setDisable(false);
			if (!repositoryClosed(task.getException()))
				report.accept(task.getException());
		});
	}

	/**
	 * Whether {@code failure} only says that the repository was closed while the work ran - a
	 * refresh started by a commit or checkout, and Close right after: no error worth a dialog (it
	 * showed "Service is not initialized").
	 */
	public static boolean repositoryClosed(Throwable failure) {
		for (Throwable t = failure; t != null; t = t.getCause())
			if (t instanceof at.jku.isse.ecco.service.ServiceNotInitializedException)
				return true;
		return false;
	}

	/**
	 * The innermost message of {@code exception}, the reason a user can act on: the service wraps it,
	 * e.g. "Error adding remote." around "Remote with this name already exists.".
	 */
	public static String reason(Throwable exception) {
		String message = exception.getMessage();
		for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause())
			if (cause.getMessage() != null)
				message = cause.getMessage();
		return message == null ? exception.toString() : message;
	}
}
