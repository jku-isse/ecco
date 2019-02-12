package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.Database;
import at.jku.isse.ecco.storage.neo4j.domain.NeoDatabase;
import at.jku.isse.ecco.storage.neo4j.domain.NeoRepository;
import com.google.inject.Inject;

public class NeoRepositoryDao extends NeoAbstractGenericDao implements RepositoryDao {

	@Inject
	public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final NeoDatabase root = this.transactionStrategy.getDatabase();

		return root.getRepository();
	}

	@Override
	public void store(Repository.Op repository) {
		// nothing to do

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to store repository without active READ_WRITE transaction.");
	}

}
