package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
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

	private Map<String, MemFeature> features;
	private Collection<Association.Op> associations;
	private ArrayList<Variant> variants = new ArrayList<>();;
	private List<Map<MemModule, MemModule>> modules;
	private Collection<Commit> commits;
	private int maxOrder;

	public MemRepository() {
		this.features = Maps.mutable.empty();
		this.associations = new ArrayList<>();
		this.modules = new ArrayList<>();
		this.commits = new ArrayList<>();
		this.setMaxOrder(2);
	}

	@Override
	public Collection<Feature> getFeatures() {
		return Collections.unmodifiableCollection(this.features.values());
	}

	public Collection<Feature> getMemFeatures() {
		return Collections.unmodifiableCollection(this.features.values());
	}

	@Override
	public Collection<Association.Op> getAssociations() {
		return Collections.unmodifiableCollection(this.associations);
	}

	@Override
	public ArrayList<Variant> getVariants() {
		return this.variants;
	}

	@Override
	public Variant getVariant(Configuration configuration) {
		for (Variant v: this.variants) {
			if(v.getConfiguration().getConfigurationString().equals(configuration.getConfigurationString())){
				return v;
			}
		}
		return null;
	}

	@Override
	public Variant getVariant(String id) {
		for (Variant v: this.variants) {
			if(v.getId().equals(id)){
				return v;
			}
		}
		return null;
	}

	@Override
	public Association getAssociation(String id) {
		Association assoc = null;
		for (Association.Op association : this.getAssociations()) {
			if (association.getId().equals(id)) {
				assoc = association;
			}
		}
		return assoc;
	}

	@Override
	public ArrayList<Feature> getFeature() {
		ArrayList<Feature> features =  new ArrayList<>();
		for (Feature feature : this.getFeatures()) {
				features.add(feature);
		}
		return features;
	}

	@Override
	public void setCommits(Collection<Commit> commits) {
		this.commits = commits;
	}

	@Override
	public Collection<Commit> getCommits() {
		return commits;
	}

	@Override
	public void addCommit(final Commit commit) {
		do {		//sets id
			commit.setId(UUID.randomUUID().toString());
		} while(getCommits().contains(commit));		//Just to make sure no Id is given twice
		commits.add(commit);
	}

	@Override
	public Collection<? extends Module> getModules(int order) {
		return Collections.unmodifiableCollection(this.modules.get(order).values());
	}

	@Override
	public MemFeature getFeature(String id) {
		return this.features.get(id);
	}

	@Override
	public Feature getOrphanedFeature(String id, String name) {
		MemFeature feature = this.getFeature(id);
		if (feature == null) {
			feature = new MemFeature(id, name);
		}
		return feature;
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
	public void addVariant(Variant variant) {
		if(variants == null) {
			variants = new ArrayList<>();
		}
		this.variants.add(variant);
	}

	@Override
	public void removeVariant(Variant variant) {
		this.variants.remove(variant);
	}

	@Override
	public void updateVariant(Variant variant, Configuration configuration, String name) {
		this.variants.remove(variant);
		variant.setConfiguration(configuration);
		variant.setName(name);
		this.variants.add(variant);
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
	public MemModule getModule(Feature[] pos, Feature[] neg) {
		MemModule queryModule = new MemModule(pos, neg);
		return this.modules.get(queryModule.getOrder()).get(queryModule);
	}

	@Override
	public Module getOrphanedModule(Feature[] pos, Feature[] neg) {
		MemModule module = this.getModule(pos, neg);
		if (module == null) {
			module = new MemModule(pos, neg);
		}
		return module;
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
