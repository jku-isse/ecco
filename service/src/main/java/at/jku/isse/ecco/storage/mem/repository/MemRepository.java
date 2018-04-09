package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.feature.MemFeature;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import org.eclipse.collections.impl.factory.Maps;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 */
public final class MemRepository implements Repository, Repository.Op {

	public static final long serialVersionUID = 1L;


	private Map<String, Feature> features;
	private Collection<Association.Op> associations;
	private List<Map<Module, Module>> modules;

	private int maxOrder;


	public MemRepository() {
		//this.features = new HashMap<>();
		this.features = Maps.mutable.empty();
		this.associations = new ArrayList<>();
		this.modules = new ArrayList<>();
		this.setMaxOrder(2);
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
	public Collection<? extends Module> getModules(int order) {
		return Collections.unmodifiableCollection(this.modules.get(order).values());
	}


	@Override
	public Feature getFeature(String id) {
		return this.features.get(id);
	}

	@Override
	public Feature addFeature(String id, String name) {
		if (this.features.containsKey(id))
			return null;
		MemFeature feature = new MemFeature(id, name);
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
		for (int order = this.modules.size(); order <= this.maxOrder; order++) {
			//this.modules.add(new HashMap<>());
			this.modules.add(Maps.mutable.empty());
		}
	}


	@Override
	public EntityFactory getEntityFactory() {
		return new MemEntityFactory();
	}


	@Override
	public Module getModule(Feature[] pos, Feature[] neg) {
		MemModule queryModule = new MemModule(pos, neg);
		return this.modules.get(queryModule.getOrder()).get(queryModule);
	}

	@Override
	public Module addModule(Feature[] pos, Feature[] neg) {
		MemModule module = new MemModule(pos, neg);
		if (this.modules.get(module.getOrder()).containsKey(module))
			return null;
		this.modules.get(module.getOrder()).put(module, module);
		return module;
	}

}
