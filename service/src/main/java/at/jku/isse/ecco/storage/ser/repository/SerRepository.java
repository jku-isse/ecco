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
import at.jku.isse.ecco.storage.ser.core.SerCommit;
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
	// only the association IDs are actually serialized as part of the "core" database (see the
	// fields below) - each association's own (large) content lives in its own file, written only
	// when dirty. See SerTransactionStrategy for the read/write side of this split.
	private Set<String> associationIds = new LinkedHashSet<>();
	private transient Map<String, Association.Op> associationsById = new LinkedHashMap<>();
	// which associations were added/removed since the last successful commit - read and cleared by
	// SerTransactionStrategy.endReadWrite(). Not serialized: purely a within-transaction concept.
	private transient Set<Association.Op> dirtyAssociations = new LinkedHashSet<>();
	private transient Set<String> removedAssociationIds = new LinkedHashSet<>();
	private ArrayList<Variant> variants = new ArrayList<>();
	private List<Map<SerModule, SerModule>> modules;
	private Collection<Commit> commits;
	private int maxOrder;
	private Node.Op mainTree;
	private MainTreeBuildingStrategy mainTreeBuildingStrategy;
	private EvaluationStrategy evaluationStrategy;

	public SerRepository() {
		this.features = Maps.mutable.empty();
		this.modules = new ArrayList<>();
		this.commits = new ArrayList<>();
		this.setMaxOrder(DEFAULT_MAX_ORDER);
		this.setEvaluationStrategy(DEFAULT_EVALUATION_STRATEGY);
		this.setMaintreeBuildingStrategy(DEFAULT_MAIN_TREE_BUILDING_STRATEGY);
	}

	/**
	 * Populates the transient, in-memory association map from associations loaded from their own
	 * per-association files - called once by {@link at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy}
	 * right after the "core" database (which only carries {@link #associationIds}, not the
	 * associations themselves) has been deserialized.
	 */
	public void restoreAssociations(Collection<? extends Association.Op> loaded) {
		this.associationsById = new LinkedHashMap<>();
		for (Association.Op association : loaded) {
			this.associationsById.put(association.getId(), association);
		}
		this.dirtyAssociations = new LinkedHashSet<>();
		this.removedAssociationIds = new LinkedHashSet<>();
	}

	/** The full set of association IDs that should have a file on disk - what {@link #restoreAssociations} needs loaded. */
	public Set<String> getAssociationIds() {
		return Collections.unmodifiableSet(this.associationIds);
	}

	/** Associations added or (re-)referenced since the last {@link #clearDirtyTracking()} - need writing. */
	public Set<Association.Op> getDirtyAssociations() {
		return Collections.unmodifiableSet(this.dirtyAssociations);
	}

	/** IDs removed since the last {@link #clearDirtyTracking()} - their files can be deleted. */
	public Set<String> getRemovedAssociationIds() {
		return Collections.unmodifiableSet(this.removedAssociationIds);
	}

	/** Called by SerTransactionStrategy after a successful write, to start tracking fresh for the next transaction. */
	public void clearDirtyTracking() {
		this.dirtyAssociations.clear();
		this.removedAssociationIds.clear();
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
		return Collections.unmodifiableCollection(this.associationsById.values());
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
		return this.associationsById.get(id);
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
		// wire up the association resolver immediately - extract() calls addAssociation() on this
		// commit shortly after, within the same transaction, well before any reload would otherwise
		// do this wiring (see SerCommit.getAssociations())
		if (commit instanceof SerCommit serCommit) {
			serCommit.setAssociationResolver(this);
		}
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
		this.associationsById.put(association.getId(), association);
		this.associationIds.add(association.getId());
		this.dirtyAssociations.add(association);
		this.removedAssociationIds.remove(association.getId());
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
		this.associationsById.remove(association.getId());
		this.associationIds.remove(association.getId());
		this.dirtyAssociations.remove(association);
		this.removedAssociationIds.add(association.getId());
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

	/**
	 * Marks the main tree as stale without paying to rebuild it - called by SerTransactionStrategy
	 * after every load, since the persisted mainTree copy is unusable (see the comment at its call
	 * site) but most read-only access (e.g. just listing associations) never touches the main tree
	 * at all. buildMainTree() is comparatively expensive (copies + boosts every association's tree
	 * and re-merges their PartialOrderGraphs), so doing it unconditionally on every open - rather
	 * than lazily, only when something actually asks for the main tree - was a real, measured
	 * performance regression.
	 */
	public void invalidateMainTree() {
		this.mainTree = null;
	}

	 @Override
	 public Node.Op getMainTree(){
		if (this.mainTree == null) {
			this.buildMainTree();
		}
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
		this.getMainTree().traverse(collectorVisitor);
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
