package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import com.google.inject.Inject;

import java.util.function.Function;

/**
 * What {@link EccoService#open()} sets up for one repository: the storage plugin's DAOs and
 * transaction strategy, and the dispatch reader and writer of the enabled adapters. Injected by
 * the repository's Guice injector ({@code injectMembers}), one per open; it stays after close(),
 * as its objects did when they were fields of EccoService, and the next open replaces it.
 *
 * <p>The services EccoService delegates to reach these through it instead of through the
 * service's own fields, and run their transactions with it ({@link #read}, {@link #write},
 * {@link #inTransaction}); {@link EccoService#context()} gives it to them once a repository is open.
 * Not thread-safe by itself: callers hold the service, as before - {@link #write} takes its monitor.
 */
final class RepositoryContext {

	@Inject
	DispatchReader reader;
	@Inject
	DispatchWriter writer;
	@Inject
	EntityFactory entityFactory;
	@Inject
	TransactionStrategy transactionStrategy;
	@Inject
	RepositoryDao repositoryDao;
	@Inject
	RemoteDao remoteDao;

	/** The service's monitor: a write holds it, as EccoService's synchronized methods do. */
	private final Object lock;
	private final ListenerRegistry listeners;

	RepositoryContext(Object lock, ListenerRegistry listeners) {
		this.lock = lock;
		this.listeners = listeners;
	}

	/**
	 * Runs {@code transaction} on the repository inside a READ_WRITE transaction, stores the result
	 * and fires a status change - the shared write path of VariantManager and ConstraintService (both
	 * used to carry their own identical copy). Rolls back quietly on failure, so a failed begin()
	 * can't replace the real error with "No transaction active".
	 */
	void write(Function<Repository.Op, Repository.Op> transaction) {
		this.write("Error during repository write transaction.", transaction);
	}

	void write(String errorMessage, Function<Repository.Op, Repository.Op> transaction) {
		synchronized (this.lock) {
			this.listeners.setWriteInProgress(true);
			try {
				this.inTransaction(TransactionStrategy.TRANSACTION.READ_WRITE, errorMessage, () -> {
					this.repositoryDao.store(transaction.apply(this.repositoryDao.load()));
					return null;
				});
				this.listeners.fireStatusChangedEvent();
			} finally {
				this.listeners.setWriteInProgress(false);
			}
		}
	}

	/** Work done in a transaction - see {@link #inTransaction}. */
	@FunctionalInterface
	interface TransactionWork<T> {
		T run() throws Exception;
	}

	/**
	 * Runs {@code work} in a transaction of {@code kind}: ends it when the work is done, rolls it back
	 * if anything fails - also an Error, also when begin() itself failed (quietly then, see
	 * {@link #rollbackIfActive}). A failure is reported as an EccoException saying
	 * {@code errorMessage}, with the failure as its cause; with no message, a runtime exception or
	 * Error is passed on as it is. The one place the begin/end/rollback sequence is written.
	 */
	<T> T inTransaction(TransactionStrategy.TRANSACTION kind, String errorMessage, TransactionWork<T> work) {
		try {
			this.transactionStrategy.begin(kind);
			T result = work.run();
			this.transactionStrategy.end();
			return result;
		} catch (Error e) {
			this.rollbackIfActive();
			throw e;
		} catch (RuntimeException e) {
			this.rollbackIfActive();
			if (errorMessage == null)
				throw e;
			throw new EccoException(errorMessage, e);
		} catch (Exception e) {
			this.rollbackIfActive();
			throw new EccoException(errorMessage != null ? errorMessage : "Error in a repository transaction.", e);
		}
	}

	/** {@link #inTransaction} reading the repository. */
	<T> T read(String errorMessage, TransactionWork<T> work) {
		return this.inTransaction(TransactionStrategy.TRANSACTION.READ_ONLY, errorMessage, work);
	}

	/**
	 * Rolls back the current transaction if there is one. TransactionStrategy has no "is a
	 * transaction active" query, so this probes via rollback() and ignores the "no transaction
	 * active" case (as RemoteSyncService does).
	 */
	void rollbackIfActive() {
		if (this.transactionStrategy == null)
			return;
		try {
			this.transactionStrategy.rollback();
		} catch (EccoException ignored) {
			// no transaction was active
		}
	}

}
