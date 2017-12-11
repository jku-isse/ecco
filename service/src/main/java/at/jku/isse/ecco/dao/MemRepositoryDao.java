package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.repository.Repository;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemRepositoryDao extends MemAbstractGenericDao implements RepositoryDao {

	private final MemEntityFactory entityFactory;

	@Inject
	public MemRepositoryDao(MemTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
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
