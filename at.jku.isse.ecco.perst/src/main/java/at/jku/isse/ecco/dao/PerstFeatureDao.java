package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.feature.PerstFeature;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.garret.perst.FieldIndex;
import org.garret.perst.IterableIterator;

import java.util.*;

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
	 * @param connectionString path to the database
	 * @param entityFactory    The factory which is used to create new entities.
	 */
	@Inject
	public PerstFeatureDao(@Named("connectionString") final String connectionString, final PerstEntityFactory entityFactory) {
		super(connectionString);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public boolean exists(final String featureName) {
		checkNotNull(featureName);
		checkArgument(!featureName.isEmpty(), "Expected non-empty feature name but was empty.");

		final DatabaseRoot root = openDatabase();

		final boolean result = root.getFeatureIndex().get(featureName) != null;

		closeDatabase();

		return result;
	}

	@Override
	public Optional<FeatureVersion> find(String featureName, int version) {
		checkNotNull(featureName);
		checkArgument(!featureName.isEmpty(), "Expected non-empty feature name but was empty.");

		final DatabaseRoot root = openDatabase();

		final Feature feature = root.getFeatureIndex().get(featureName);

		FeatureVersion result = null;

		for (FeatureVersion featureVersion : feature.getVersions()) {
			if (featureVersion.getVersion() == version)
				result = featureVersion;
		}

		closeDatabase();

		return Optional.ofNullable(result);
	}

	@Override
	public Optional<Set<FeatureVersion>> loadAllVersions(final String featureName) {
		checkNotNull(featureName);
		checkArgument(!featureName.isEmpty(), "Expected non-empty feature name but was empty.");

		final DatabaseRoot root = openDatabase();

		final Set<FeatureVersion> result = new HashSet<FeatureVersion>(root.getFeatureIndex().get(featureName).getVersions());

		closeDatabase();

		return Optional.ofNullable(result);
	}

	@Override
	public Set<String> loadAllFeatureNames() {


		final DatabaseRoot root = openDatabase();

		final Set<String> featureNames = new LinkedHashSet<>();
		IterableIterator<Map.Entry<Object, PerstFeature>> it = root.getFeatureIndex().entryIterator();
		while (it.hasNext()) {
			featureNames.add((String) it.next().getKey());
		}

		closeDatabase();

		return featureNames;
	}

	@Override
	public Feature load(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non-empty id but was empty.");

		final DatabaseRoot root = openDatabase();

		final Feature feature = root.getFeatureIndex().get(id);

		closeDatabase();

		return feature;
	}

	@Override
	public Set<Feature> loadAllFeatures() {

		final DatabaseRoot root = openDatabase();

		final Set<Feature> features = new LinkedHashSet<>(root.getFeatureIndex());

		closeDatabase();

		return features;
	}

	@Override
	public void remove(final Feature entity) {
		checkNotNull(entity);
		checkNotNull(entity.getName(), "Expected the id of the entity to be non-null but was null.");
		checkArgument(!entity.getName().isEmpty(), "Expected the id of the entity to be non-empty but was empty.");

		remove(entity.getName());
	}

	@Override
	public void remove(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non-empty id but was empty.");

		final DatabaseRoot root = openDatabase();

		root.getFeatureIndex().remove(id);

		closeDatabase();
	}

	@Override
	public void removeWithAllVersions(final String featureName) {
		checkNotNull(featureName);
		checkArgument(!featureName.isEmpty(), "Expected non-empty feature name but was empty.");

		this.remove(featureName);
	}


	@Override
	public Feature save(final Feature entity) {
		checkNotNull(entity);

		final DatabaseRoot root = openDatabase();
		final FieldIndex<PerstFeature> featureIndex = root.getFeatureIndex();

		//final PerstFeature perstEntity = entityFactory.createPerstFeature(entity);
		final PerstFeature perstEntity = (PerstFeature) entity;

		Feature result;

		if (perstEntity.getName().isEmpty()) {
			result = saveNewFeature(featureIndex, perstEntity);
		} else {
			result = updateFeature(featureIndex, perstEntity);
		}

		closeDatabase();

		return result;
	}

	private Feature updateFeature(final FieldIndex<PerstFeature> featureIndex, final PerstFeature perstEntity) {
		assert featureIndex != null;
		assert perstEntity != null;
		assert !perstEntity.getName().isEmpty() : "Expected that the entity is already stored in the database";

		featureIndex.set(perstEntity);

		return perstEntity;
	}


	private Feature saveNewFeature(final FieldIndex<PerstFeature> featureIndex, final PerstFeature perstEntity) {
		assert featureIndex != null;
		assert perstEntity != null;
		assert perstEntity.getName().isEmpty() : "Expected that the entity is new to the database.";

		featureIndex.put(perstEntity);

		final Feature result = perstEntity;

		return result;
	}

}
