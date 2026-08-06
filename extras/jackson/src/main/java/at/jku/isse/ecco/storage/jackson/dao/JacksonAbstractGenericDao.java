package at.jku.isse.ecco.storage.jackson.dao;

import at.jku.isse.ecco.dao.GenericDao;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class JacksonAbstractGenericDao implements GenericDao {

	protected JacksonTransactionStrategy transactionStrategy;

	@Inject
	JacksonAbstractGenericDao(JacksonTransactionStrategy transactionStrategy) {
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
