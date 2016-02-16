package at.jku.isse.ecco.dao;

import com.google.inject.Inject;

public abstract class JpaAbstractGenericDao<T extends Persistable> implements GenericDao<T> {

	protected JpaTransactionStrategy transactionStrategy;

	@Inject
	JpaAbstractGenericDao(JpaTransactionStrategy transactionStrategy) {
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
