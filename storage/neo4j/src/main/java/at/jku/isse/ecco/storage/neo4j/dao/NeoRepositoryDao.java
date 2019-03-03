package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.NeoRepository;
import com.google.inject.Inject;
import org.neo4j.ogm.session.Session;

import java.util.ArrayList;

public class NeoRepositoryDao extends NeoAbstractGenericDao implements RepositoryDao {

	@Inject
	public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		ArrayList<NeoRepository> repositories = new ArrayList<>(neoSession.loadAll(NeoRepository.class)); // TODO: load which one?
		if (repositories.isEmpty()) {
			return new NeoRepository();
		} else if (repositories.size() == 1) {
			return repositories.get(0);
		} else {
			//What now?
			throw new EccoException("Multiple repositories loaded!");
		}

	}

	@Override
	public void store(Repository.Op repository) {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to store repository without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();
		neoSession.save(repository);
	}

}
