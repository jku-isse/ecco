package at.jku.isse.ecco.storage.perst.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.perst.dao.PerstEntityFactory;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import org.garret.perst.Persistent;

import java.util.*;

/**
 * Perstory implementation of {@link Repository}.
 */
public class PerstRepository extends Persistent implements Repository, Repository.Op {

	private Map<String, Feature> features;
	private Collection<Association.Op> associations;
	private Map<Module, Module> modules;

	private EntityFactory entityFactory;

	private int maxOrder;


	public PerstRepository() {
		this.features = new HashMap<>();
		this.associations = new ArrayList<>();
		this.modules = new HashMap<>();
		this.entityFactory = new PerstEntityFactory();
		this.maxOrder = 2;
	}


	@Override
	public Collection<Feature> getFeatures() {
		return Collections.unmodifiableCollection(this.features.values());
	}

	@Override
	public Collection<Association.Op> getAssociations() {
		return Collections.unmodifiableCollection(this.associations);
	}

	@Override
	public Collection<? extends Module> getModules() {
		return Collections.unmodifiableCollection(this.modules.values());
	}


	@Override
	public Feature getFeature(String id) {
		return this.features.get(id);
	}

	@Override
	public Feature addFeature(String id, String name) {
		if (this.features.containsKey(id))
			return null;
		Feature feature = new PerstFeature(id, name);
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


	@Override
	public Module getModule(Feature[] pos, Feature[] neg) {
		return this.modules.get(new PerstModule(pos, neg));
	}

	@Override
	public Module addModule(Feature[] pos, Feature[] neg) {
		Module module = new PerstModule(pos, neg);
		if (this.modules.containsKey(module))
			return null;
		this.modules.put(module, module);
		return module;
	}

}
