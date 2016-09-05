package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.PerstFeature;
import at.jku.isse.ecco.feature.PerstFeatureVersion;
import com.google.inject.Inject;
import org.garret.perst.FieldIndex;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The feature dao which provides access methods for features.
 * <p>
 * TODO: this DAO needs a major rework!
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstFeatureDao extends PerstAbstractGenericDao<Feature> implements FeatureDao {

	private final PerstEntityFactory entityFactory;

	/**
	 * Constructs a new feature dao with the given connection string which is a
	 * path to a database file or the path where a new database should be
	 * created.
	 *
	 * @param transactionStrategy The transaction strategy.
	 * @param entityFactory       The factory which is used to create new entities.
	 */
	@Inject
	public PerstFeatureDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public Feature load(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non-empty id but was empty.");

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Feature feature = root.getFeatureIndex().get(id);

		this.transactionStrategy.done();

		return feature;
	}

	@Override
	public Set<Feature> loadAllFeatures() throws EccoException {

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Set<Feature> features = new LinkedHashSet<>(root.getFeatureIndex());

		this.transactionStrategy.done();

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
		checkArgument(!id.isEmpty(), "Expected a non-empty id but was empty.");

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getFeatureIndex().remove(id);

		this.transactionStrategy.done();
	}


	@Override
	public Feature save(final Feature entity) throws EccoException {
		checkNotNull(entity);

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		final FieldIndex<PerstFeature> featureIndex = root.getFeatureIndex();

		//final PerstFeature perstEntity = entityFactory.createPerstFeature(entity);
		final PerstFeature perstEntity = (PerstFeature) entity;

		PerstFeature result;

		if (perstEntity.getName().isEmpty()) {
			result = saveNewFeature(featureIndex, perstEntity);
		} else {
			result = updateFeature(featureIndex, perstEntity);
		}

		result.store();
		for (PerstFeatureVersion featureVersion : result.getVersions()) {
			featureVersion.store();
		}

		this.transactionStrategy.done();

		return result;
	}

	private PerstFeature updateFeature(final FieldIndex<PerstFeature> featureIndex, final PerstFeature perstEntity) {
		assert featureIndex != null;
		assert perstEntity != null;
		assert !perstEntity.getName().isEmpty() : "Expected that the entity is already stored in the database";

		featureIndex.set(perstEntity);

		return perstEntity;
	}


	private PerstFeature saveNewFeature(final FieldIndex<PerstFeature> featureIndex, final PerstFeature perstEntity) {
		assert featureIndex != null;
		assert perstEntity != null;
		assert perstEntity.getName().isEmpty() : "Expected that the entity is new to the database.";

		featureIndex.put(perstEntity);

		final PerstFeature result = perstEntity;

		return result;
	}

}
