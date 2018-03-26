package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import com.google.inject.Inject;

import java.util.List;

public class SerCommitDao extends SerAbstractGenericDao implements CommitDao {

	@Inject
	public SerCommitDao(SerTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		return null;
	}

	@Override
	public Commit load(String id) throws EccoException {
		return null;
	}

	@Override
	public void remove(String id) throws EccoException {

	}

	@Override
	public void remove(Commit entity) throws EccoException {

	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		return null;
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
