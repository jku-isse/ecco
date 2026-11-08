package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import com.google.inject.Inject;

/**
 * What {@link EccoService#open()} sets up for one repository: the storage plugin's DAOs and
 * transaction strategy, and the dispatch reader and writer of the enabled adapters. Injected by
 * the repository's Guice injector ({@code injectMembers}), one per open; it stays after close(),
 * as its objects did when they were fields of EccoService, and the next open replaces it.
 *
 * <p>The services EccoService delegates to reach these through it instead of through the
 * service's own fields. Not thread-safe by itself: callers hold the service, as before.
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

}
