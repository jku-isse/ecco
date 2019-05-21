package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.JpaCommit;
import com.google.inject.Inject;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JpaCommitDao extends JpaAbstractGenericDao<Commit> implements CommitDao {

	private final JpaEntityFactory entityFactory;

	@Inject
	JpaCommitDao(JpaTransactionStrategy transactionStrategy, final JpaEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		// obtain entity manager from transaction strategy
		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		// do query
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<JpaCommit> cq = cb.createQuery(JpaCommit.class);
		Root<JpaCommit> rootEntry = cq.from(JpaCommit.class);
		CriteriaQuery<JpaCommit> all = cq.select(rootEntry);
		TypedQuery<JpaCommit> allQuery = entityManager.createQuery(all);

		List<Commit> commit = new ArrayList<>(allQuery.getResultList());

		// return result
		return commit;
	}

	@Override
	public Commit load(String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Commit commit = (Commit) entityManager.find(Commit.class, Integer.valueOf(id));

		return commit;
	}

	@Override
	public void remove(String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Commit association = (Commit) entityManager.find(Commit.class, Integer.valueOf(id));
		entityManager.remove(association);
	}

	@Override
	public void remove(Commit entity) throws EccoException {
		checkNotNull(entity);

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Commit commit = (Commit) entityManager.find(Commit.class, entity.getId());
		entityManager.remove(commit);
	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		checkNotNull(entity);

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Commit commit = entityManager.merge(entity);

		return commit;
	}

}
