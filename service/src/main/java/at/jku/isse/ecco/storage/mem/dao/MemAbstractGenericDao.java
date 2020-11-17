package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.dao.GenericDao;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class MemAbstractGenericDao implements GenericDao {

	protected MemTransactionStrategy transactionStrategy;

	@Inject
	MemAbstractGenericDao(MemTransactionStrategy transactionStrategy) {
		checkNotNull(transactionStrategy);

		this.transactionStrategy = transactionStrategy;
	}

	@Override
	public void init() {

	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

}
