package at.jku.isse.ecco.storage.ser.repository;

import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.featuretrace.RejectedTrace;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveBasedEvaluation;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.core.SerConstraint;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.*;

/**
 * The serialized (Ser storage) implementation of {@link Repository}.
 */
public final class SerRepository implements Repository, Repository.Op {

	public static final long serialVersionUID = 1L;
	public static final int DEFAULT_MAX_ORDER = 2;
	public static final EvaluationStrategy DEFAULT_EVALUATION_STRATEGY = new SerProactiveBasedEvaluation();
	public static final MainTreeBuildingStrategy DEFAULT_MAIN_TREE_BUILDING_STRATEGY = new SerBoostedAssociationMerger();

	private Map<String, SerFeature> features;
	// accepted feature-model constraints (see Constraint) -- may be null on a repository
	// deserialized from before this field existed (serialVersionUID has never changed for exactly
	// this reason); every accessor defensively initializes it, mirroring addVariant()'s guard below.
	private Map<String, SerConstraint> constraints;
	// rejected constraint suggestions - null on a repository written before they were stored here
	// (they were per-machine preferences then); older builds ignore the field
	private Map<String, SerConstraint> rejectedConstraints;
	// accepted parents in the feature model, feature name to parent name, and the rejected ones - null
	// on a repository written before they were stored (serialVersionUID stays, as for the fields above:
	// an older build reading a newer repository ignores them). A later field (e.g. or/alternative
	// groups) can be added the same way.
	private Map<String, String> featureParents;
	private Map<String, Set<String>> rejectedFeatureParents;
	// only the association IDs are actually serialized as part of the "core" database (see the
	// fields below) - each association's own (large) content lives in its own file, written only
	// when dirty. See SerTransactionStrategy for the read/write side of this split.
	private Set<String> associationIds = new LinkedHashSet<>();
	private transient Map<String, Association.Op> associationsById = new LinkedHashMap<>();
	// which associations were added/removed since the last successful commit - read and cleared by
	// SerTransactionStrategy.endReadWrite(). Not serialized: purely a within-transaction concept.
	private transient Set<Association.Op> dirtyAssociations = new LinkedHashSet<>();
	private transient Set<String> removedAssociationIds = new LinkedHashSet<>();
	// artifacts are their own independently-persisted, deduplicated entities too (same split as
	// associations, and for the same reason: an artifact reachable from more than one association -
	// e.g. shared, non-unique structure - must not be redundantly re-serialized once per association
	// file, or it comes back as multiple distinct objects claiming the same id after reload, each
	// with its own diverging state like an independently-deserialized PartialOrderGraph copy. See
	// the duplicated-shared-artifact bug). Unlike associations, artifacts have no
	// add/remove choke point (SerEntityFactory.createArtifact() doesn't register with a repository
	// at all) - SerTransactionStrategy discovers dirty artifacts by walking dirty associations'
	// trees at write time instead, via registerArtifact() below.
	//
	// Artifacts are stored in pack files (see SerTransactionStrategy), and the core lists the packs in
	// the order they were written (packOrder). Which artifacts there are and which pack holds each is
	// not in the core: loading reads the packs in that order, a later pack's copy of an artifact
	// superseding an earlier one's.
	private List<String> packOrder = new ArrayList<>();
	// the association packs in the order they were written - a later pack's copy of an association
	// is the current one (SerTransactionStrategy)
	private List<String> associationPackOrder;
	// the module revisions association counters refer to by index (CompactCounters, version 2):
	// append-only, so an index once written stays valid
	private List<SerModuleRevision> countedRevisions;
	// ... and the index of each (made when first asked for)
	private transient Map<SerModuleRevision, Integer> countedIndex;
	private transient Map<String, Artifact.Op<?>> artifactsById = new LinkedHashMap<>();
	// the index, built when loading: every artifact's id, the pack holding its current copy, how many
	// current artifacts each pack holds
	private transient Set<String> liveArtifactIds;
	private transient Map<String, String> livePacks;
	private transient Map<String, Integer> currentPerPack;
	// each pack's number of entries (to tell when it is mostly superseded)
	private Map<String, Integer> packSizes = new HashMap<>();
	// the same in bytes, as serialized - counted when read or written, not stored: an artifact's
	// bytes, each pack's current bytes and all bytes written into it. One whole file with its order
	// graph outweighs thousands of lines: counting artifacts kept a pack 99.6% current by count and
	// 40% by bytes, and tinycc's packs held 6 MB of superseded files
	private transient Map<String, Integer> artifactBytes;
	private transient Map<String, Long> currentBytesPerPack;
	private transient Map<String, Long> packBytes;
	// identity-keyed (Collections.newSetFromMap(new IdentityHashMap<>()), same pattern
	// Trees.java's newIdentitySet() already uses for the same reason): SerArtifact.equals()/
	// hashCode() are deliberately data-based (Trees.slice() relies on it), so a plain
	// content-equality Set here would silently drop a genuinely distinct artifact (different
	// storageId, already known) whenever its DATA happens to match one already
	// in the set - its id would then have no file ever written for it, a real bug this caught via
	// a many-commits-with-repeated-content benchmark (NoSuchFileException on the next reload).
	private transient Set<Artifact.Op<?>> dirtyArtifacts = Collections.newSetFromMap(new IdentityHashMap<>());
	private ArrayList<Variant> variants = new ArrayList<>();
	private List<Map<SerModule, SerModule>> modules;
	private Collection<Commit> commits;
	private int maxOrder;
	// purely derived from associations (see buildMainTree()) and unusable as persisted anyway - a
	// freshly-loaded mainTree's node/artifact references aren't wired up by
	// SerTransactionStrategy's post-load resolution pass (that only walks repo.getAssociations()),
	// so loadDatabase() already discards whatever was here via invalidateMainTree() on every load.
	// Serializing it was therefore always pure waste, and since SerRootNode.writeObject() BFS-walks
	// and explicitly re-serializes every node in the tree regardless of the transient marker on
	// children, an eagerly-built mainTree (see the removed EccoService.commit() call) made the
	// "core" database write silently O(whole-repo-tree-size) instead of O(metadata), causing an
	// OutOfMemoryError partway through a 60+ commit Git import.
	private transient Node.Op mainTree;
	// the proactive traces the last main-tree build rejected (see ProactiveTraceCheck), derived with it
	private transient List<RejectedTrace> rejectedTraces;
	private MainTreeBuildingStrategy mainTreeBuildingStrategy;
	private EvaluationStrategy evaluationStrategy;

	public SerRepository() {
		this.features = Maps.mutable.empty();
		this.constraints = new LinkedHashMap<>();
		this.modules = new ArrayList<>();
		this.commits = new ArrayList<>();
		this.setMaxOrder(DEFAULT_MAX_ORDER);
		this.setEvaluationStrategy(DEFAULT_EVALUATION_STRATEGY);
		this.setMaintreeBuildingStrategy(DEFAULT_MAIN_TREE_BUILDING_STRATEGY);
	}

	/**
	 * Resolves the associations' node artifacts, containing nodes, references and order graphs
	 * against the artifacts received with this repository over the wire, as a load resolves them
	 * against the artifact store ({@link at.jku.isse.ecco.storage.ser.dao.LoadResolver#resolveArtifacts}).
	 * Call after {@link #restoreAssociations}, since it walks {@link #getAssociations()}.
	 */
	@Override
	public void resolveArtifacts(Collection<? extends Artifact.Op<?>> artifacts) {
		Map<String, Artifact.Op<?>> artifactsById = new HashMap<>();
		for (Artifact.Op<?> artifact : artifacts) {
			if (artifact instanceof at.jku.isse.ecco.storage.ser.artifact.SerArtifact<?> serArtifact) {
				artifactsById.put(serArtifact.getStorageId(), artifact);
			}
		}
		at.jku.isse.ecco.storage.ser.dao.LoadResolver.resolveArtifacts(this.getAssociations(), artifactsById, "among the artifacts received");
	}

	/**
	 * Populates the transient, in-memory association map from associations loaded from their own
	 * per-association files - called once by {@link at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy}
	 * right after the "core" database (which only carries {@link #associationIds}, not the
	 * associations themselves) has been deserialized. Also used to restore associations sent as a
	 * companion payload over a raw socket (see {@link Repository.Op#restoreAssociations}), which hits
	 * the same transient-index gap since it bypasses the DAO entirely.
	 */
	@Override
	public void restoreAssociations(Collection<? extends Association.Op> loaded) {
		this.associationsById = new LinkedHashMap<>();
		for (Association.Op association : loaded) {
			this.associationsById.put(association.getId(), association);
		}
		this.dirtyAssociations = new LinkedHashSet<>();
		this.removedAssociationIds = new LinkedHashSet<>();
	}

	/**
	 * Whether this core is in the current layout: it has association ids and the artifact pack order,
	 * and its associations are in packs. Cores written before - a file per artifact or association,
	 * the artifact index in the core - are no longer read (see SerTransactionStrategy).
	 */
	public boolean isCurrentLayout() {
		return this.associationIds != null && this.packOrder != null
				&& (this.associationIds.isEmpty() || (this.associationPackOrder != null && !this.associationPackOrder.isEmpty()));
	}

	/** The full set of association IDs that should have a file on disk - what {@link #restoreAssociations} needs loaded. */
	public Set<String> getAssociationIds() {
		return this.associationIds == null ? Set.of() : Collections.unmodifiableSet(this.associationIds);
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
		this.dirtyArtifacts.clear();
	}

	/**
	 * Populates the transient, in-memory artifact map from artifacts loaded from their own
	 * per-artifact files - called once by SerTransactionStrategy right after the "core" database has
	 * been deserialized, before any association is loaded (associations' nodes only carry an
	 * artifactId until this store is in place to resolve them against).
	 */
	public void restoreArtifacts(Collection<? extends Artifact.Op<?>> loaded) {
		this.artifactsById = new LinkedHashMap<>();
		for (Artifact.Op<?> artifact : loaded) {
			if (artifact instanceof at.jku.isse.ecco.storage.ser.artifact.SerArtifact<?> serArtifact) {
				this.artifactsById.put(serArtifact.getStorageId(), artifact);
			}
		}
		this.dirtyArtifacts = Collections.newSetFromMap(new IdentityHashMap<>());
	}

	/** The ids of all artifacts, loaded or registered since. */
	public Set<String> getArtifactIds() {
		return Collections.unmodifiableSet(this.liveArtifactIds());
	}

	private Set<String> liveArtifactIds() {
		if (this.liveArtifactIds == null)
			this.liveArtifactIds = new LinkedHashSet<>();
		return this.liveArtifactIds;
	}

	/** Artifacts touched since the last {@link #clearDirtyTracking()} - need writing. */
	public Set<Artifact.Op<?>> getDirtyArtifacts() {
		return Collections.unmodifiableSet(this.dirtyArtifacts);
	}

	/** The pack holding the current copy of an artifact, or null if it has a file of its own. */
	public String getArtifactPack(String artifactId) {
		return this.livePacks == null ? null : this.livePacks.get(artifactId);
	}

	/** Records that [pack] holds the current copy of an artifact, of [bytes] serialized, superseding the one it had. */
	public void setArtifactPack(String artifactId, String pack, int bytes) {
		if (this.livePacks == null) {
			this.livePacks = new HashMap<>();
			this.currentPerPack = new HashMap<>();
			this.artifactBytes = new HashMap<>();
			this.currentBytesPerPack = new HashMap<>();
		}
		this.liveArtifactIds().add(artifactId);
		String before = this.livePacks.put(artifactId, pack);
		Integer bytesBefore = this.artifactBytes.put(artifactId, bytes);
		if (before != null) {
			this.currentPerPack.merge(before, -1, Integer::sum);
			this.currentBytesPerPack.merge(before, -(long) (bytesBefore == null ? 0 : bytesBefore), Long::sum);
		}
		this.currentPerPack.merge(pack, 1, Integer::sum);
		this.currentBytesPerPack.merge(pack, (long) bytes, Long::sum);
	}

	/** The serialized bytes of an artifact's current copy (0 if it has none). */
	public int artifactBytes(String artifactId) {
		return this.artifactBytes == null ? 0 : this.artifactBytes.getOrDefault(artifactId, 0);
	}

	/** The serialized bytes of the artifacts [pack] holds the current copy of. */
	public long currentBytes(String pack) {
		return this.currentBytesPerPack == null ? 0 : this.currentBytesPerPack.getOrDefault(pack, 0L);
	}

	/** Pack file -> the serialized bytes of all artifacts written into it (not stored: counted when read or written). */
	public Map<String, Long> getPackBytes() {
		if (this.packBytes == null)
			this.packBytes = new HashMap<>();
		return this.packBytes;
	}

	/** How many artifacts [pack] holds the current copy of. */
	public int currentArtifacts(String pack) {
		return this.currentPerPack == null ? 0 : this.currentPerPack.getOrDefault(pack, 0);
	}

	/** The artifacts [pack] holds the current copy of - a walk over all artifacts, for the few packs compacted. */
	public List<String> currentArtifactsOf(String pack) {
		List<String> ids = new ArrayList<>();
		if (this.livePacks != null)
			this.livePacks.forEach((id, p) -> { if (p.equals(pack)) ids.add(id); });
		return ids;
	}

	/** The packs in the order they were written: a later one's copy of an artifact is current. */
	public List<String> getPackOrder() {
		return this.packOrder;
	}

	/** Adds a pack, written now, at the end of {@link #getPackOrder()}. */
	public void addPack(String pack) {
		this.packOrder.add(pack);
	}

	/** Takes packs that hold no current artifact anymore out of {@link #getPackOrder()} and the sizes. */
	public void dropEmptyPacks() {
		this.packOrder.removeIf(pack -> this.currentArtifacts(pack) == 0);
		this.getPackSizes().keySet().retainAll(this.getPackOrder());
		this.getPackBytes().keySet().retainAll(this.getPackOrder());
	}

	/** Pack file -> the number of artifacts written into it. */
	public Map<String, Integer> getPackSizes() {
		if (this.packSizes == null)
			this.packSizes = new HashMap<>();
		return this.packSizes;
	}

	public Artifact.Op<?> getArtifact(String id) {
		return this.artifactsById.get(id);
	}

	/**
	 * Registers an artifact discovered while walking a dirty association's tree at write time
	 * (SerTransactionStrategy.endReadWrite()) - the closest equivalent to addAssociation() artifacts
	 * have, since nothing calls this at creation time (see the field javadoc above). Safe to call
	 * repeatedly for the same artifact (e.g. reachable from more than one dirty association) - a
	 * LinkedHashSet/Map, so re-adding is a no-op beyond confirming it's tracked and dirty.
	 */
	public void registerArtifact(Artifact.Op<?> artifact) {
		if (!(artifact instanceof at.jku.isse.ecco.storage.ser.artifact.SerArtifact<?> serArtifact)) return;
		this.artifactsById.put(serArtifact.getStorageId(), artifact);
		this.liveArtifactIds().add(serArtifact.getStorageId());
		this.dirtyArtifacts.add(artifact);
	}

	@Override
	public Collection<Feature> getFeatures() {
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
			// the same revisions - the displayed strings compared ids cut to 7 characters, in array order
			if (v.getConfiguration().equals(configuration)) {
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
		// a new commit gets an id; a copied one keeps its own (Repository.Op#copyInto), so a later pull
		// can tell which commits both repositories have
		if (commit.getId() == null || getCommits().contains(commit)) {
			do {
				commit.setId(UUID.randomUUID().toString());
			} while (getCommits().contains(commit));        //Just to make sure no Id is given twice
		}
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
	public Collection<Constraint> getConstraints() {
		if (this.constraints == null) {
			this.constraints = new LinkedHashMap<>();
		}
		return Collections.unmodifiableCollection(this.constraints.values());
	}

	@Override
	public Constraint getConstraint(String id) {
		if (this.constraints == null) {
			this.constraints = new LinkedHashMap<>();
		}
		return this.constraints.get(id);
	}

	@Override
	public Constraint addConstraint(Constraint.Kind kind, String featureA, String featureB) {
		if (this.constraints == null) {
			this.constraints = new LinkedHashMap<>();
		}
		SerConstraint constraint = new SerConstraint(kind, featureA, featureB);
		if (this.constraints.containsKey(constraint.getId()))
			return null;
		this.constraints.put(constraint.getId(), constraint);
		return constraint;
	}

	@Override
	public void removeConstraint(Constraint constraint) {
		if (this.constraints == null) return;
		this.constraints.remove(constraint.getId());
	}

	private Map<String, SerConstraint> rejectedConstraints() {
		if (this.rejectedConstraints == null) {
			this.rejectedConstraints = new LinkedHashMap<>();
		}
		return this.rejectedConstraints;
	}

	@Override
	public Collection<Constraint> getRejectedConstraints() {
		return Collections.unmodifiableCollection(this.rejectedConstraints().values());
	}

	@Override
	public Constraint getRejectedConstraint(String id) {
		return this.rejectedConstraints().get(id);
	}

	@Override
	public Constraint addRejectedConstraint(Constraint.Kind kind, String featureA, String featureB) {
		SerConstraint constraint = new SerConstraint(kind, featureA, featureB);
		if (this.rejectedConstraints().containsKey(constraint.getId()))
			return null;
		this.rejectedConstraints().put(constraint.getId(), constraint);
		return constraint;
	}

	@Override
	public void removeRejectedConstraint(Constraint constraint) {
		this.rejectedConstraints().remove(constraint.getId());
	}

	@Override
	public Map<String, String> getFeatureParents() {
		return this.featureParents == null ? Map.of() : Collections.unmodifiableMap(this.featureParents);
	}

	@Override
	public Map<String, Set<String>> getRejectedFeatureParents() {
		if (this.rejectedFeatureParents == null)
			return Map.of();
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		this.rejectedFeatureParents.forEach((feature, parents) -> copy.put(feature, Set.copyOf(parents)));
		return Collections.unmodifiableMap(copy);
	}

	@Override
	public void setFeatureParent(String feature, String parent) {
		if (this.featureParents == null)
			this.featureParents = new LinkedHashMap<>();
		if (parent == null) {
			this.featureParents.remove(feature);
			return;
		}
		if (Repository.makesCycle(this.featureParents, feature, parent))
			throw new EccoException("Making " + parent + " the parent of " + feature + " would make " + feature + " its own ancestor.");
		this.featureParents.put(feature, parent);
	}

	@Override
	public void addRejectedFeatureParent(String feature, String parent) {
		if (this.rejectedFeatureParents == null)
			this.rejectedFeatureParents = new LinkedHashMap<>();
		this.rejectedFeatureParents.computeIfAbsent(feature, f -> new LinkedHashSet<>()).add(parent);
	}

	@Override
	public void removeRejectedFeatureParent(String feature, String parent) {
		if (this.rejectedFeatureParents == null)
			return;
		Set<String> parents = this.rejectedFeatureParents.get(feature);
		if (parents != null && parents.remove(parent) && parents.isEmpty())
			this.rejectedFeatureParents.remove(feature);
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
		ProactiveTraceCheck traceCheck = new ProactiveTraceCheck(this.getCommits());
		this.mainTree = this.mainTreeBuildingStrategy.buildMainTree(this.getAssociations(), traceCheck);
		this.rejectedTraces = traceCheck.getRejected();
	}

	@Override
	public List<RejectedTrace> getRejectedTraces() {
		this.getMainTree();
		return this.rejectedTraces == null ? List.of() : this.rejectedTraces;
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
		this.rejectedTraces = null;
	}

	 @Override
	 public Node.Op getMainTree(){
		if (this.mainTree == null) {
			this.buildMainTree();
		}
		return this.mainTree;
	 }

	/** The association packs, oldest first. */
	public List<String> getAssociationPackOrder() {
		return this.associationPackOrder == null ? List.of() : Collections.unmodifiableList(this.associationPackOrder);
	}

	/** Sets the association packs, oldest first - before the core is written. */
	public void setAssociationPackOrder(List<String> packs) {
		this.associationPackOrder = new ArrayList<>(packs);
	}

	/** The index counters refer to [revision] by - added at the end if it has none yet. */
	public synchronized int countedIndex(SerModuleRevision revision) {
		if (this.countedRevisions == null)
			this.countedRevisions = new ArrayList<>();
		if (this.countedIndex == null) {
			this.countedIndex = new HashMap<>();
			for (int i = 0; i < this.countedRevisions.size(); i++)
				this.countedIndex.putIfAbsent(this.countedRevisions.get(i), i);
		}
		Integer index = this.countedIndex.get(revision);
		if (index == null) {
			index = this.countedRevisions.size();
			this.countedRevisions.add(revision);
			this.countedIndex.put(revision, index);
		}
		return index;
	}

	/** The module revision counters refer to by [index]; null if there is none. */
	public synchronized SerModuleRevision countedRevision(int index) {
		return this.countedRevisions == null || index < 0 || index >= this.countedRevisions.size() ? null : this.countedRevisions.get(index);
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
