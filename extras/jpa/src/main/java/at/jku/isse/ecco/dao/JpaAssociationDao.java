package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.JpaAssociation;
import com.google.inject.Inject;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JpaAssociationDao extends JpaAbstractGenericDao<Association> implements AssociationDao {

	private final JpaEntityFactory entityFactory;

	/**
	 * Constructs new association dao with the given connection string which is
	 * a path to a database file or the path where a new database should be
	 * created.
	 *
	 * @param entityFactory The factory which is used to create new entities.
	 */
	@Inject
	public JpaAssociationDao(JpaTransactionStrategy transactionStrategy, final JpaEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Association> loadAllAssociations() throws EccoException {
		// obtain entity manager from transaction strategy
		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		// do query
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<JpaAssociation> cq = cb.createQuery(JpaAssociation.class);
		Root<JpaAssociation> rootEntry = cq.from(JpaAssociation.class);
		CriteriaQuery<JpaAssociation> all = cq.select(rootEntry);
		TypedQuery<JpaAssociation> allQuery = entityManager.createQuery(all);

		List<Association> associations = new ArrayList<>(allQuery.getResultList());

		// return result
		return associations;
	}

	@Override
	public Association load(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Association association = (Association) entityManager.find(Association.class, Integer.valueOf(id));

		return association;
	}

	@Override
	public void remove(final Association entity) throws EccoException {
		checkNotNull(entity);

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Association association = (Association) entityManager.find(Association.class, entity.getId());
		entityManager.remove(association);
	}

	@Override
	public void remove(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Association association = (Association) entityManager.find(Association.class, Integer.valueOf(id));
		entityManager.remove(association);
	}

	@Override
	public Association save(final Association entity) throws EccoException {
		checkNotNull(entity);

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Association association = entityManager.merge(entity);

		return association;
	}


	@Override
	public Map<Association, Map<Association, Integer>> loadDependencyMap() {
		return null;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadConflictsMap() {
		return null;
	}

	@Override
	public void storeDependencyMap(final Map<Association, Map<Association, Integer>> dependencyMap) {

	}

	@Override
	public void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) {

	}

}
