package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class MemCommitDao extends MemAbstractGenericDao implements CommitDao {

	@Inject
	public MemCommitDao(MemTransactionStrategy transactionStrategy) {
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
		final Database root = this.transactionStrategy.getDatabase();

		root.getCommitIndex().remove(id);
	}

	@Override
	public void remove(Commit entity) throws EccoException {
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
