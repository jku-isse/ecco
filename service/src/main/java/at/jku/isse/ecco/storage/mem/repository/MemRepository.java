package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.util.Trees;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.feature.MemFeature;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;
import org.logicng.io.parsers.ParserException;

import java.util.*;

/**
 * Memory implementation of {@link Repository}.
 */
public final class MemRepository implements Repository, Repository.Op {

	public static final long serialVersionUID = 1L;

	private Map<String, MemFeature> features;
	private Collection<Association.Op> associations;
	private ArrayList<Variant> variants = new ArrayList<>();
	private List<Map<MemModule, MemModule>> modules;
	private Collection<Commit> commits;
	private int maxOrder;
	private Node.Op featureTraceTree;
	private transient FormulaFactory formulaFactory = new FormulaFactory();


	public MemRepository() {
		this.features = Maps.mutable.empty();
		this.associations = new ArrayList<>();
		this.modules = new ArrayList<>();
		this.commits = new ArrayList<>();
		this.setMaxOrder(2);
	}

	@Override
	public void mergeFeatureTraceTree(Node.Op root) {
		this.featureTraceTree = Trees.treeFusion(this.featureTraceTree, root);
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
	public Node.Op fuseAssociationsWithFeatureTraces() {
		// todo: overwrite copySingleNodeCompletely in MemRootNode

		Node.Op mainTree = this.featureTraceTree.copySingleNodeCompletely();
		Trees.treeFusion(mainTree, this.featureTraceTree);

		for (Association.Op association : this.associations){
			Node.Op associationTree = association.getTraceTree();
			Trees.treeFusion(mainTree, associationTree);
		}

		return mainTree;
	}


	@Override
	public void removeFeatureTracePercentage(int percentage) {
		if (percentage < 0 || percentage > 100){
			throw new RuntimeException(String.format("Percentage of feature traces is invalid (%d).", percentage));
		}
		Collection<FeatureTrace> traces = this.getFeatureTraces();
		int noOfRemovals = (traces.size() * percentage) / 100;
		List<FeatureTrace> featureTraceList = new ArrayList<>(traces);
		Collections.shuffle(featureTraceList);
		Iterator<FeatureTrace> iterator = featureTraceList.stream().iterator();
		for (int i = 1; i <= noOfRemovals; i++){
			FeatureTrace trace = iterator.next();
			Node.Op traceNode = (Node.Op) trace.getNode();
			traceNode.removeFeatureTrace();
			// todo: remove node if it's a leaf? (and all nodes on path to new leaf?)
		}
	}


	private void SyncRepositoryWithFeatureTrace(FeatureTrace featureTrace){
		// replace condition-features with feature-revisions
		// add missing features / feature-revisions

		// iterate through literals
		// feature-revision -> add to repo if missing
		// feature 	-> add to repo if missing (create first revision)
		// 			-> replace literal with feature-revision (latest)

		Formula userCondition = this.parseFormulaString(featureTrace.getUserConditionString());
		Collection<Literal> literals = userCondition.literals();
		for (Literal literal : literals){
			String literalName = literal.name();
			if (literalName.contains("_")){
				String featureRevisionName = literalName.replaceFirst("_", ".");
				featureRevisionName = featureRevisionName.replace("_", "-");
				this.addFeatureRevisionIfMissing(featureRevisionName);
			} else {
				this.SyncRepoWithFeature(featureTrace, literalName);
			}
		}
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

	private void SyncRepoWithFeature(FeatureTrace featureTrace, String featureName) {
		String userCondition = featureTrace.getUserConditionString();
		Feature feature = this.addFeatureIfMissing(featureName);
		FeatureRevision latestRevision = feature.getLatestRevision();
		String revisionName;
		String newFeatureRevision;
		if (latestRevision == null) {
			revisionName = UUID.randomUUID().toString();
			newFeatureRevision = featureName + "." + revisionName;
			this.addFeatureRevisionIfMissing(featureName + "." + revisionName);
		} else {
			revisionName = latestRevision.getId();
			newFeatureRevision = featureName + "." + revisionName;
		}
		userCondition = userCondition.replace(featureName, newFeatureRevision);
		featureTrace.setUserCondition(userCondition);
	}

	private Formula parseFormulaString(String string){
		try{
			return this.formulaFactory.parse(string);
		} catch (ParserException e){
			throw new RuntimeException("Formula String could not be parsed: " + string + ": " + e.getMessage());
		}
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

	@Override
	public Node.Op getFeatureTree() {
		return this.featureTraceTree;
	}

	@Override
	public Collection<FeatureTrace> getFeatureTraces(){
		FeatureTraceCollectorVisitor collectorVisitor = new FeatureTraceCollectorVisitor();
		this.featureTraceTree.traverse(collectorVisitor);
		return collectorVisitor.getFeatureTraces();
	}
}
