package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One-off reads and writes of the repository, away from the FX thread. A commit, pull or import
 * holds the service's lock for its whole run and changes associations, conditions and order graphs
 * meanwhile, so:
 * <ul>
 * <li>a read of repository structures - an association's condition, a node's children, an order
 * graph, the features - runs holding the lock (NFR-C1b), else it reads them half changed (an error,
 * or a wrong view);</li>
 * <li>it does not run on the FX thread (NFR-C3), which would wait for the lock and freeze the window
 * for the rest of the commit - and neither does a write, for the same reason.</li>
 * </ul>
 * The result is applied on the FX thread. For a view that refreshes itself on every change, see
 * {@link LatestRefresh}.
 */
public final class RepositoryAccess {

	private RepositoryAccess() {
	}

	/**
	 * Reads the repository in the background, holding the service's lock, and hands the result to
	 * {@code apply} on the FX thread. Nothing is read or applied if the repository is not open (or
	 * was closed meanwhile); another failure goes to {@code failed}, on the FX thread.
	 *
	 * @param read reads the repository; must not touch the scene graph
	 */
	public static <T> void read(EccoService service, String name, Function<EccoService, T> read, Consumer<T> apply, Consumer<Throwable> failed) {
		read(service, name, read, apply, failed, () -> {
		});
	}

	/**
	 * {@link #read(EccoService, String, Function, Consumer, Consumer)}, running {@code skipped} on the
	 * FX thread when nothing was read because the repository is not open (or was closed meanwhile) -
	 * for a caller that marks itself as reading until {@code apply} or {@code failed} runs: neither
	 * ran then, and the mark stayed for good (a tree item never opened again, OK never enabled).
	 */
	public static <T> void read(EccoService service, String name, Function<EccoService, T> read, Consumer<T> apply, Consumer<Throwable> failed, Runnable skipped) {
		start(name, () -> {
			T result;
			try {
				synchronized (service) {
					if (!service.isInitialized()) {
						Platform.runLater(skipped);
						return;
					}
					result = read.apply(service);
				}
			} catch (Throwable t) {
				Platform.runLater(TaskFailures.repositoryClosed(t) ? skipped : () -> failed.accept(t));
				return;
			}
			Platform.runLater(() -> apply.accept(result));
		});
	}

	/**
	 * Runs {@code write} - service calls that change the repository - in the background, then
	 * {@code done} on the FX thread, or {@code failed} with the failure.
	 */
	public static void write(String name, Runnable write, Runnable done, Consumer<Throwable> failed) {
		start(name, () -> {
			try {
				write.run();
			} catch (Throwable t) {
				Platform.runLater(() -> failed.accept(t));
				return;
			}
			Platform.runLater(done);
		});
	}

	private static void start(String name, Runnable work) {
		Thread thread = new Thread(work, name);
		thread.setDaemon(true);
		thread.start();
	}
}
