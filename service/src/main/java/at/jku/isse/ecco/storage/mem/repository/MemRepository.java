package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.feature.MemFeature;
import at.jku.isse.ecco.storage.mem.module.MemModule;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 */
public class MemRepository implements Repository, Repository.Op {

	private Map<String, Feature> features;
	private Collection<Association.Op> associations;
	private Map<Module, Module> modules;

	private EntityFactory entityFactory;

	private int maxOrder;


	public MemRepository() {
		this.features = new HashMap<>();
		this.associations = new ArrayList<>();
		this.modules = new HashMap<>();
		this.entityFactory = new MemEntityFactory();
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
		Feature feature = new MemFeature(id, name);
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
		return this.modules.get(new MemModule(pos, neg));
	}

	@Override
	public Module addModule(Feature[] pos, Feature[] neg) {
		Module module = new MemModule(pos, neg);
		if (this.modules.containsKey(module))
			return null;
		this.modules.put(module, module);
		return module;
	}

}
