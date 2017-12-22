package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.feature.BaseFeature;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.repository.RepositoryOperator;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class MemRepository implements Repository, Repository.Op {

	private transient RepositoryOperator operator;


	private Map<String, Feature> features;
	private Collection<Association.Op> associations;

	private EntityFactory entityFactory;

	private int maxOrder = 5;


	public MemRepository() {
		this.features = new HashMap<>();
		this.associations = new ArrayList<>();
		this.entityFactory = new MemEntityFactory();
		this.maxOrder = 5;

		this.operator = new RepositoryOperator(this);
	}


	@Override
	public Commit extract(Configuration configuration, Set<Node.Op> nodes) {
		return this.operator.extract(configuration, nodes);
	}

	@Override
	public Checkout compose(Configuration configuration) {
		return this.operator.compose(configuration);
	}

	@Override
	public Op subset(Collection<FeatureVersion> deselected, int maxOrder, EntityFactory entityFactory) {
		return this.operator.subset(deselected, maxOrder, entityFactory);
	}

	@Override
	public Op copy(EntityFactory entityFactory) {
		return this.operator.copy(entityFactory);
	}

	@Override
	public void merge(Op repository) {
		this.operator.merge(repository);
	}


	@Override
	public Collection<Feature> getFeatures() {
		return new ArrayList<>(this.features.values());
	}


	@Override
	public Collection<Association.Op> getAssociations() {
		//return new ArrayList<>(this.associations);
		return Collections.unmodifiableCollection(this.associations);
	}


	@Override
	public Feature getFeature(String id) {
		return this.features.get(id);
	}

	@Override
	public Collection<Feature> getFeaturesByName(String name) {
		return this.operator.getFeaturesByName(name);
	}

	@Override
	public Feature addFeature(String id, String name, String description) {
		Feature feature = new BaseFeature(id, name, description);
		this.features.put(feature.getId(), feature);
		return feature;
	}


	@Override
	public void addAssociation(Association.Op association) {
		this.associations.add(association);
	}

	@Override
	public void removeAssociation(Association.Op association) {
		this.associations.remove(association);
	}


	@Override
	public int getMaxOrder() {
		return this.maxOrder;
	}

	@Override
	public void setMaxOrder(int maxOrder) {
		this.maxOrder = maxOrder;
	}

	@Override
	public EntityFactory getEntityFactory() {
		return this.entityFactory;
	}

}
