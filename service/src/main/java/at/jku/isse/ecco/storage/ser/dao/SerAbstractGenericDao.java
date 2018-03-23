package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.dao.GenericDao;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class SerAbstractGenericDao implements GenericDao {

	protected SerTransactionStrategy transactionStrategy;

	@Inject
	SerAbstractGenericDao(SerTransactionStrategy transactionStrategy) {
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
