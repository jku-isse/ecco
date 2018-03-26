package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import com.google.inject.Inject;

public class SerRepositoryDao extends SerAbstractGenericDao implements RepositoryDao {

	@Inject
	public SerRepositoryDao(SerTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		return null;
	}

	@Override
	public void store(Repository.Op repository) {

	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

	@Override
	public void init() {

	}

}
