package at.jku.isse.ecco.storage.ser.repository;

import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveBasedEvaluation;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 */
public final class SerRepository implements Repository, Repository.Op {

	public static final long serialVersionUID = 1L;
	public static final int DEFAULT_MAX_ORDER = 2;
	public static final EvaluationStrategy DEFAULT_EVALUATION_STRATEGY = new SerProactiveBasedEvaluation();
	public static final MainTreeBuildingStrategy DEFAULT_MAIN_TREE_BUILDING_STRATEGY = new SerBoostedAssociationMerger();

	private Map<String, SerFeature> features;
	private Collection<Association.Op> associations;
	private ArrayList<Variant> variants = new ArrayList<>();
	private List<Map<SerModule, SerModule>> modules;
	private Collection<Commit> commits;
	private int maxOrder;
	private Node.Op mainTree;
	private MainTreeBuildingStrategy mainTreeBuildingStrategy;
	private EvaluationStrategy evaluationStrategy;

	public SerRepository() {
		this.features = Maps.mutable.empty();
		this.associations = new ArrayList<>();
		this.modules = new ArrayList<>();
		this.commits = new ArrayList<>();
		this.setMaxOrder(DEFAULT_MAX_ORDER);
		this.setEvaluationStrategy(DEFAULT_EVALUATION_STRATEGY);
		this.setMaintreeBuildingStrategy(DEFAULT_MAIN_TREE_BUILDING_STRATEGY);
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
		do {        //sets id
			commit.setId(UUID.randomUUID().toString());
		} while(getCommits().contains(commit));        //Just to make sure no Id is given twice
		commits.add(commit);
	}

	@Override
	public Collection<? extends Module> getModules(int order) {
		return Collections.unmodifiableCollection(this.modules.get(order).values());
	}

	@Override
	public SerFeature getFeature(String id) {
		return this.features.get(id);
	}

	@Override
	public Feature getOrphanedFeature(String id, String name) {
		SerFeature feature = this.getFeature(id);
		if (feature == null) {
			feature = new SerFeature(id, name);
		}
		return feature;
	}

	@Override
	public Feature addFeature(String id, String name) {
		if (this.features.containsKey(id))
			return null;
		SerFeature feature = new SerFeature(id, name);
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
		return new SerEntityFactory();
	}

	@Override
	public void buildMainTree() {
		this.mainTree = this.mainTreeBuildingStrategy.buildMainTree(this.getAssociations());
	}

	 @Override
	 public Node.Op getMainTree(){
		return this.mainTree;
	 }

	private void addFeatureRevisionIfMissing(String featureRevisionName){
		// add feature if missing, add revision if missing
		String[] nameParts = featureRevisionName.split("\\.");
		String featureName = nameParts[0];
		this.addFeatureIfMissing(featureName);

		String revisionName = nameParts[1];
		Collection<Feature> features = this.getFeaturesByName(featureName);
		if (features.size() == 0){
			throw new RuntimeException("could not add Feature " + featureName);
		}
		Feature feature = features.iterator().next();
		feature.addRevision(revisionName);
	}

	private Feature addFeatureIfMissing(String featureName){
		Collection<Feature> features = this.getFeaturesByName(featureName);
		if (features.size() != 0) { return features.iterator().next(); }
		String id = UUID.randomUUID().toString();
		return this.addFeature(id, featureName);
	}

	@Override
	public SerModule getModule(Feature[] pos, Feature[] neg) {
		SerModule queryModule = new SerModule(pos, neg);
		return this.modules.get(queryModule.getOrder()).get(queryModule);
	}

	@Override
	public Module getOrphanedModule(Feature[] pos, Feature[] neg) {
		SerModule module = this.getModule(pos, neg);
		if (module == null) {
			module = new SerModule(pos, neg);
		}
		return module;
	}

	@Override
	public Module addModule(Feature[] pos, Feature[] neg) {
		SerModule module = new SerModule(pos, neg);
		if (this.modules.get(module.getOrder()).containsKey(module))
			return null;
		this.modules.get(module.getOrder()).put(module, module);
		return module;
	}

	@Override
	public Collection<FeatureTrace> getFeatureTraces(){
		FeatureTraceCollectorVisitor collectorVisitor = new FeatureTraceCollectorVisitor();
		this.mainTree.traverse(collectorVisitor);
		return collectorVisitor.getFeatureTraces();
	}

	@Override
	public void setMaintreeBuildingStrategy(MainTreeBuildingStrategy mainTreeBuildingStrategy){
		this.mainTreeBuildingStrategy = mainTreeBuildingStrategy;
	}

	@Override
	public MainTreeBuildingStrategy getMainTreeBuildingStrategy() {
		return this.mainTreeBuildingStrategy;
	}

	@Override
	public void setEvaluationStrategy(EvaluationStrategy evaluationStrategy) {
		this.evaluationStrategy = evaluationStrategy;
	}

	@Override
	public EvaluationStrategy getEvaluationStrategy() {
		return this.evaluationStrategy;
	}
}
