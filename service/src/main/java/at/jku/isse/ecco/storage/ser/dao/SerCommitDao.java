package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.dao.Database;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class SerCommitDao extends SerAbstractGenericDao implements CommitDao {

	@Inject
	public SerCommitDao(SerTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}


	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final List<Commit> commits = new ArrayList<>(root.getCommitIndex().values());

		return commits;
	}

	@Override
	public Commit load(String id) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		return root.getCommitIndex().get(id);
	}

	@Override
	public void remove(String id) throws EccoException {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove commit without active READ_WRITE transaction.");

		final Database root = this.transactionStrategy.getDatabase();

		root.getCommitIndex().remove(id);
	}

	@Override
	public void remove(Commit entity) throws EccoException {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove commit without active READ_WRITE transaction.");

		final Database root = this.transactionStrategy.getDatabase();

		root.getCommitIndex().remove(entity.getId());
	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final MemCommit baseEntity = (MemCommit) entity;

		if (!root.getCommitIndex().containsKey(baseEntity.getId())) {
			baseEntity.setId(root.nextCommitId());
		}

		root.getCommitIndex().put(baseEntity.getId(), baseEntity);

		return baseEntity;
	}

}
