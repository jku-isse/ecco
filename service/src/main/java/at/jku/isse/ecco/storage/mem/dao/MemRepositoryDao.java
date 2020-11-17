package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import com.google.inject.Inject;

public class MemRepositoryDao extends MemAbstractGenericDao implements RepositoryDao {

	@Inject
	public MemRepositoryDao(MemTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final Database root = this.transactionStrategy.getDatabase();

		return root.getRepository();
	}

	@Override
	public void store(Repository.Op repository) {
		// nothing to do for memory implementation
	}

}
