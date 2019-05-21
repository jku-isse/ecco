package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.storage.perst.core.PerstCommit;
import at.jku.isse.ecco.dao.CommitDao;
import com.google.inject.Inject;
import org.garret.perst.FieldIndex;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstCommitDao extends PerstAbstractGenericDao<Commit> implements CommitDao {

	private final PerstEntityFactory entityFactory;

	@Inject
	PerstCommitDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final List<Commit> result = new ArrayList<>(root.getCommitIndex());

		this.transactionStrategy.done();

		return result;
	}

	@Override
	public Commit load(String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Commit perstCommit = root.getCommitIndex().get(id);

		this.transactionStrategy.done();

		return perstCommit;
	}

	@Override
	public void remove(String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getCommitIndex().remove(id);

		this.transactionStrategy.done();
	}

	@Override
	public void remove(Commit entity) throws EccoException {
		checkNotNull(entity);

		remove(String.valueOf(entity.getId()));
	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		checkNotNull(entity);

		//final PerstAssociation association = entityFactory.createPerstAssociation(entity);
		final PerstCommit commit = (PerstCommit) entity; // TODO!

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		final FieldIndex<PerstCommit> commitIndex = root.getCommitIndex();

		if (!commitIndex.contains(commit)) {
			commit.setId(root.nextCommitId());
			commitIndex.put(commit);
		} else {
			commitIndex.set(commit);
		}

		//commit.store();
		commit.modify();

		this.transactionStrategy.done();

		return commit;
	}

}
