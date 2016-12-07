package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.MemEntityFactory;
import at.jku.isse.ecco.feature.BaseFeature;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class MemRepository implements Repository, RepositoryOperand {

	private transient RepositoryOperator operator;


	private Map<String, Feature> features;
	private Collection<Association> associations;

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
	public Commit extract(Configuration configuration, Set<Node> nodes) {
		return this.operator.extract(configuration, nodes);
	}

	@Override
	public Checkout compose(Configuration configuration) {
		return this.operator.compose(configuration);
	}

	@Override
	public RepositoryOperand subset(Collection<FeatureVersion> deselected, int maxOrder, EntityFactory entityFactory) {
		return this.operator.subset(deselected, maxOrder, entityFactory);
	}

	@Override
	public void merge(RepositoryOperand repository) {
		this.operator.merge(repository);
	}


	@Override
	public Collection<Feature> getFeatures() {
		return new ArrayList<>(this.features.values());
	}


	@Override
	public Collection<Association> getAssociations() {
		return new ArrayList<>(this.associations);
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
	public Feature addFeature(String name) {
		return this.addFeature(name, "");
	}

	@Override
	public Feature addFeature(String name, String description) {
		return this.addFeature(UUID.randomUUID().toString(), name, description);
	}

	@Override
	public Feature addFeature(String id, String name, String description) {
		Feature feature = new BaseFeature(id, name, description);
		this.features.put(feature.getId(), feature);
		return feature;
	}


	@Override
	public void addAssociation(Association association) {
		this.associations.add(association);
	}

	@Override
	public void removeAssociation(Association association) {
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
