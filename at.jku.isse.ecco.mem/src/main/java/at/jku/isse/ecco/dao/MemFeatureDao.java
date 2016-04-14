package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.BaseFeature;
import at.jku.isse.ecco.feature.Feature;
import com.google.inject.Inject;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemFeatureDao extends MemAbstractGenericDao<Feature> implements FeatureDao {

	private final MemEntityFactory entityFactory;

	@Inject
	public MemFeatureDao(MemTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public Set<Feature> loadAllFeatures() throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final Set<Feature> features = new LinkedHashSet<>(root.getFeatureIndex().values());

		return features;
	}

	@Override
	public Feature load(String id) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		return root.getFeatureIndex().get(id);
	}

	@Override
	public void remove(String id) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		root.getFeatureIndex().remove(id);
	}

	@Override
	public void remove(Feature entity) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		root.getFeatureIndex().remove(entity.getName());
	}

	@Override
	public Feature save(Feature entity) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final BaseFeature baseEntity = (BaseFeature) entity;

		root.getFeatureIndex().put(baseEntity.getName(), baseEntity);

		return baseEntity;
	}

}
