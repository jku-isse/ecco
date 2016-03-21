package at.jku.isse.ecco.dao;

import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class MemAbstractGenericDao<T extends Persistable> implements GenericDao<T> {

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
