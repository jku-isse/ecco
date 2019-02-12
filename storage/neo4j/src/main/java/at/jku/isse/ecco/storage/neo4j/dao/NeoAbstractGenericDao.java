package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.dao.GenericDao;
import at.jku.isse.ecco.dao.Persistable;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class NeoAbstractGenericDao implements GenericDao {

	NeoTransactionStrategy transactionStrategy;

	@Inject
    NeoAbstractGenericDao(NeoTransactionStrategy transactionStrategy) {
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
