package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Refreshes a view from the repository - the latest state only, one refresh at a time:
 * <ul>
 * <li>asked again while one runs, it runs once more after it, however often it was asked - each ask
 * started a refresh of its own, and during an import of a thousand commits they piled up, all but
 * the last thrown away when done;</li>
 * <li>asked while the view is hidden, it only notes it, and refreshes when the view is shown;</li>
 * <li>it reads the repository holding the service's lock, so not while a commit changes it - read
 * meanwhile, counters changed under it (an error dialog, or a crash); the result is applied on the
 * FX thread, which never waits for the lock: a commit holds it for its whole run.</li>
 * </ul>
 * A repository closed meanwhile ({@link #clear()}) applies nothing.
 */
public final class LatestRefresh<T> {

	private final String name;
	private final EccoService service;
	private final Function<EccoService, T> compute;
	private final Consumer<T> apply;
	private final Consumer<Throwable> failed;

	private boolean running;
	private boolean again;
	private boolean visible = true;
	private boolean missed;
	/** Counts {@link #clear()}: a refresh started before one applies nothing. */
	private long epoch;

	/**
	 * @param compute reads the repository, in the background, holding the service's lock; only
	 *                called while the repository is open
	 * @param apply   shows the result, on the FX thread
	 * @param failed  shows a failure, on the FX thread (not called when the repository was closed meanwhile)
	 */
	public LatestRefresh(String name, EccoService service, Function<EccoService, T> compute, Consumer<T> apply, Consumer<Throwable> failed) {
		this.name = name;
		this.service = service;
		this.compute = compute;
		this.apply = apply;
		this.failed = failed;
	}

	/** Asks for a refresh - from any thread. */
	public synchronized void request() {
		if (!this.visible) {
			this.missed = true;
			return;
		}
		if (this.running) {
			this.again = true;
			return;
		}
		this.running = true;
		this.start(this.epoch);
	}

	/** Whether the view is shown: hidden, requests are noted; shown again, a noted one runs. */
	public synchronized void setVisible(boolean visible) {
		this.visible = visible;
		if (visible && this.missed) {
			this.missed = false;
			this.request();
		}
	}

	/** The repository was closed: nothing started before applies, nothing noted runs. */
	public synchronized void clear() {
		this.epoch++;
		this.again = false;
		this.missed = false;
	}

	/** Whether a refresh runs or is to run - for tests. */
	public synchronized boolean busy() {
		return this.running || this.again;
	}

	private void start(long epoch) {
		Thread thread = new Thread(() -> this.run(epoch), this.name + " refresh");
		thread.setDaemon(true);
		thread.start();
	}

	private void run(long epoch) {
		T result = null;
		Throwable failure = null;
		boolean computed = false;
		try {
			synchronized (this.service) {
				if (this.service.isInitialized()) {
					result = this.compute.apply(this.service);
					computed = true;
				}
			}
		} catch (Throwable t) {
			failure = t;
		}
		boolean stale;
		synchronized (this) {
			stale = epoch != this.epoch || this.again;
			if (this.again && this.visible) {
				this.again = false;
				this.start(this.epoch); // asked again meanwhile: the newer state, not this one
				return;
			}
			if (this.again) {
				this.again = false;
				this.missed = true;
			}
			this.running = false;
		}
		if (stale)
			return;
		T shown = result;
		Throwable thrown = failure;
		boolean apply = computed;
		Platform.runLater(() -> {
			synchronized (this) {
				if (epoch != this.epoch)
					return; // closed meanwhile
			}
			if (thrown != null) {
				if (!TaskFailures.repositoryClosed(thrown))
					this.failed.accept(thrown);
			} else if (apply) {
				this.apply.accept(shown);
			}
		});
	}
}
