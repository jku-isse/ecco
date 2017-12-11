package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.BaseCommit;
import at.jku.isse.ecco.core.Commit;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemCommitDao extends MemAbstractGenericDao implements CommitDao {

	private final MemEntityFactory entityFactory;

	@Inject
	public MemCommitDao(MemTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
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

		final BaseCommit baseEntity = (BaseCommit) entity;

		if (!root.getCommitIndex().containsKey(baseEntity.getId())) {
			baseEntity.setId(root.nextCommitId());
		}

		root.getCommitIndex().put(baseEntity.getId(), baseEntity);

		return baseEntity;
	}

}
