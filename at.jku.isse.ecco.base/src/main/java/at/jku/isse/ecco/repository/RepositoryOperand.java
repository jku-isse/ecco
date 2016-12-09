package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.Set;

public interface RepositoryOperand extends Repository {

	public Commit extract(Configuration configuration, Set<Node> nodes);

	public Checkout compose(Configuration configuration);

	public RepositoryOperand subset(Collection<FeatureVersion> deselected, int maxOrder, EntityFactory entityFactory);

	public RepositoryOperand copy(EntityFactory entityFactory);

	public void merge(RepositoryOperand repository);


	public Feature getFeature(String id);

	public Collection<Feature> getFeaturesByName(String name);

	public Feature addFeature(String name);

	public Feature addFeature(String name, String description);

	public Feature addFeature(String id, String name, String description);


	public void addAssociation(Association association);

	public void removeAssociation(Association association);


	public int getMaxOrder();

	public void setMaxOrder(int maxOrder);


	public EntityFactory getEntityFactory();

}
