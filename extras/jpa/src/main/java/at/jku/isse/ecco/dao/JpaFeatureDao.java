package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.JpaFeature;
import com.google.inject.Inject;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JpaFeatureDao extends JpaAbstractGenericDao<Feature> implements FeatureDao {

	private final JpaEntityFactory entityFactory;

	/**
	 * Constructs a new feature dao with the given connection string which is a
	 * path to a database file or the path where a new database should be
	 * created.
	 *
	 * @param entityFactory The factory which is used to create new entities.
	 */
	@Inject
	public JpaFeatureDao(JpaTransactionStrategy transactionStrategy, final JpaEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public Feature load(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Feature feature = (Feature) entityManager.find(JpaFeature.class, id);

		return feature;
	}

	@Override
	public Set<Feature> loadAllFeatures() throws EccoException {
		// obtain entity manager from transaction strategy
		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		// do query
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<JpaFeature> cq = cb.createQuery(JpaFeature.class);
		Root<JpaFeature> rootEntry = cq.from(JpaFeature.class);
		CriteriaQuery<JpaFeature> all = cq.select(rootEntry);
		TypedQuery<JpaFeature> allQuery = entityManager.createQuery(all);

		Set<Feature> features = new HashSet<>(allQuery.getResultList());

		// return result
		return features;
	}

	@Override
	public void remove(final Feature entity) throws EccoException {
		checkNotNull(entity);
		checkNotNull(entity.getName(), "Expected the id of the entity to be non-null but was null.");
		checkArgument(!entity.getName().isEmpty(), "Expected the id of the entity to be non-empty but was empty.");

		remove(entity.getName());
	}

	@Override
	public void remove(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Feature feature = (Feature) entityManager.find(Feature.class, Integer.valueOf(id));
		entityManager.remove(feature);
	}


	@Override
	public Feature save(final Feature entity) throws EccoException {
		checkNotNull(entity);

		EntityManager entityManager = this.transactionStrategy.getEntityManager();

		Feature existingFeature = (Feature) entityManager.find(JpaFeature.class, entity.getName());
		if (existingFeature == null) {
			entityManager.persist(entity);
			return entity;
		} else {
			Feature feature = entityManager.merge(entity);
			return feature;
		}
	}

}
