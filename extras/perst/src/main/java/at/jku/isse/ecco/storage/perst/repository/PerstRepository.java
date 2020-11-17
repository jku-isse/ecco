package at.jku.isse.ecco.storage.perst.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.perst.core.PerstAssociation;
import at.jku.isse.ecco.storage.perst.dao.PerstEntityFactory;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import org.garret.perst.Persistent;

import java.util.*;

/**
 * Perstory implementation of {@link Repository}.
 */
public class PerstRepository extends Persistent implements Repository, Repository.Op {

	private Map<String, PerstFeature> features;
	private Collection<PerstAssociation> associations;
	private List<Map<PerstModule, PerstModule>> modules;

	private EntityFactory entityFactory;

	private int maxOrder;


	public PerstRepository() {
		this.entityFactory = new PerstEntityFactory();
		this.features = new HashMap<>();
		this.associations = new ArrayList<>();
		this.modules = new ArrayList<>();
		this.setMaxOrder(2);
	}


	@Override
	public Collection<PerstFeature> getFeatures() {
		return Collections.unmodifiableCollection(this.features.values());
	}

	@Override
	public Collection<PerstAssociation> getAssociations() {
		return Collections.unmodifiableCollection(this.associations);
	}

	@Override
	public Collection<PerstModule> getModules(int order) {
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
		PerstFeature feature = new PerstFeature(id, name);
		this.features.put(feature.getId(), feature);
		return feature;
	}


	@Override
	public void addAssociation(Association.Op association) {
		if (!(association instanceof PerstAssociation))
			throw new EccoException("Only PerstAssociation can be added to PerstRepository!");
		this.associations.add((PerstAssociation) association);
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
			this.modules.add(new HashMap<>());
		}
	}


	@Override
	public EntityFactory getEntityFactory() {
		return this.entityFactory;
	}


	@Override
	public PerstModule getModule(Feature[] pos, Feature[] neg) {
		PerstModule queryModule = new PerstModule(pos, neg);
		return this.modules.get(queryModule.getOrder()).get(queryModule);
	}

	@Override
	public PerstModule addModule(Feature[] pos, Feature[] neg) {
		PerstModule module = new PerstModule(pos, neg);
		if (this.modules.get(module.getOrder()).containsKey(module))
			return null;
		this.modules.get(module.getOrder()).put(module, module);
		return module;
	}

}
