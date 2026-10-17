package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;

/**
 * A fetch, pull or push stopped on request at one of its checkpoints - before anything was written:
 * its transaction was rolled back, nothing changed (see {@code RemoteSyncService}).
 */
public class OperationCancelledException extends EccoException {

	private static final long serialVersionUID = 1L;

	public OperationCancelledException() {
		super("Cancelled - nothing was changed.");
	}

	/** Whether [e], or one of its causes, is a cancellation - the operations wrap what they throw. */
	public static boolean isCause(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause())
			if (t instanceof OperationCancelledException)
				return true;
		return false;
	}

	/** Throws if [cancelled] says so: a checkpoint - call it only where nothing is written yet. */
	static void check(java.util.function.BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean())
			throw new OperationCancelledException();
	}
}
