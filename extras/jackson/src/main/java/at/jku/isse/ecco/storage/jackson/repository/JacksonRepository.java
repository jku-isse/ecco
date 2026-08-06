package at.jku.isse.ecco.storage.jackson.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.jackson.core.JacksonAssociation;
import at.jku.isse.ecco.storage.jackson.dao.JacksonEntityFactory;
import at.jku.isse.ecco.storage.jackson.feature.JacksonFeature;
import at.jku.isse.ecco.storage.jackson.module.JacksonModule;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.eclipse.collections.impl.factory.Maps;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public final class JacksonRepository implements Repository, Repository.Op {

	public static final long serialVersionUID = 1L;


	private Map<String, JacksonFeature> features;
	@JsonManagedReference
	private Collection<JacksonAssociation> associations;
	private List<Map<String, JacksonModule>> modules;

	private int maxOrder;


	public JacksonRepository() {
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
		JacksonFeature feature = new JacksonFeature(id, name);
		this.features.put(feature.getId(), feature);
		return feature;
	}


	@Override
	public void addAssociation(Association.Op association) {
		if (!(association instanceof JacksonAssociation))
			throw new EccoException("Only Jackson storage types can be used.");
		this.associations.add((JacksonAssociation) association);
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
		return new JacksonEntityFactory();
	}


	@Override
	public Module getModule(Feature[] pos, Feature[] neg) {
		JacksonModule queryModule = new JacksonModule(pos, neg);
		return this.modules.get(queryModule.getOrder()).get(queryModule.toString());
	}

	@Override
	public Module addModule(Feature[] pos, Feature[] neg) {
		JacksonModule module = new JacksonModule(pos, neg);
		if (this.modules.get(module.getOrder()).containsKey(module.toString()))
			return null;
		this.modules.get(module.getOrder()).put(module.toString(), module);
		return module;
	}

}
