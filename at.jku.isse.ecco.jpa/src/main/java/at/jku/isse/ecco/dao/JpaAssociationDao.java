package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Association;
import com.google.inject.Inject;
import com.google.inject.name.Named;

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
	 * @param connectionString path to the database
	 * @param entityFactory    The factory which is used to create new entities.
	 */
	@Inject
	public JpaAssociationDao(@Named("connectionString") final String connectionString, final JpaEntityFactory entityFactory) {
		super(connectionString);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Association> loadAllAssociations() {
		try {
			entityManager.getTransaction().begin();
			List<Association> associations = entityManager.createQuery("from Association").getResultList();
			entityManager.getTransaction().commit();
			return associations;
		} catch (Exception e) {
			entityManager.getTransaction().rollback();
		}
		return new ArrayList<>();
	}

	@Override
	public Association load(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		try {
			entityManager.getTransaction().begin();
			Association association = (Association) entityManager.find(Association.class, Integer.valueOf(id));
			entityManager.getTransaction().commit();
			return association;
		} catch (Exception e) {
			entityManager.getTransaction().rollback();
		}
		return null;
	}

	@Override
	public void remove(final Association entity) {
		checkNotNull(entity);

		try {
			entityManager.getTransaction().begin();
			Association association = (Association) entityManager.find(Association.class, entity.getId());
			entityManager.remove(association);
			entityManager.getTransaction().commit();
		} catch (Exception e) {
			entityManager.getTransaction().rollback();
		}
	}

	@Override
	public void remove(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		try {
			entityManager.getTransaction().begin();
			Association association = (Association) entityManager.find(Association.class, Integer.valueOf(id));
			entityManager.remove(association);
			entityManager.getTransaction().commit();
		} catch (Exception e) {
			entityManager.getTransaction().rollback();
		}
	}

	@Override
	public Association save(final Association entity) {
		checkNotNull(entity);

		try {
			entityManager.getTransaction().begin();
			Association association = entityManager.merge(entity);
			entityManager.getTransaction().commit();
			return association;
		} catch (Exception e) {
			entityManager.getTransaction().rollback();
		}
		return null;
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
