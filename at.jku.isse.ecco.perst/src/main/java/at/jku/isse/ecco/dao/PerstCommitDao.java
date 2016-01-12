package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.PerstCommit;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.garret.perst.FieldIndex;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstCommitDao extends PerstAbstractGenericDao<Commit> implements CommitDao {

	private final PerstEntityFactory entityFactory;

	@Inject
	PerstCommitDao(@Named("connectionString") String connectionString, final PerstEntityFactory entityFactory) {
		super(connectionString);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Commit> loadAllCommits() {
		if (!database.isOpened()) database.open(connectionString);
		final DatabaseRoot root = database.getRoot();

		final List<Commit> result = new ArrayList<>(root.getCommitIndex());

		database.close();

		return result;
	}

	@Override
	public Commit load(String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		if (!database.isOpened()) database.open(connectionString);
		final DatabaseRoot root = database.getRoot();

		final Commit perstCommit = root.getCommitIndex().get(id);

		database.close();

		return perstCommit;
	}

	@Override
	public void remove(String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		if (!database.isOpened()) database.open(connectionString);
		final DatabaseRoot root = database.getRoot();

		root.getAssociationIndex().remove(id);

		database.close();
	}

	@Override
	public void remove(Commit entity) {
		checkNotNull(entity);

		remove(String.valueOf(entity.getId()));
	}

	@Override
	public Commit save(Commit entity) {
		checkNotNull(entity);

		//final PerstAssociation association = entityFactory.createPerstAssociation(entity);
		final PerstCommit commit = (PerstCommit) entity; // TODO!

		final DatabaseRoot root = openDatabase();
		final FieldIndex<PerstCommit> commitIndex = root.getCommitIndex();

		if (!commitIndex.contains(commit)) {
			commit.setId(root.nextCommitId());
			commitIndex.put(commit);
		} else {
			commitIndex.set(commit);
		}

		closeDatabase();

		return commit;
	}

}
