package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.util.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.featuretrace.RejectedTrace;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Public repository interface. A repository contains {@link at.jku.isse.ecco.feature.Feature}s and {@link at.jku.isse.ecco.core.Association}s.
 */
public interface Repository extends Persistable {
	String MERGE = "merge";

	Collection<? extends Feature> getFeatures();

	Collection<? extends Association> getAssociations();

	/**
	 * Accepted feature-model constraints (see {@link Constraint}) -- purely advisory, never trusted
	 * without re-verification against freshly mined data (see {@code EccoService#acceptedSuggestions}
	 * in {@code service}).
	 */
	Collection<? extends Constraint> getConstraints();

	/**
	 * Rejected constraint suggestions: a reviewer decided they do not hold, so they are not proposed
	 * again. Stored in the repository like the accepted ones, so the decision travels with
	 * fork/pull/push. A suggestion is accepted, rejected, or neither - never both.
	 */
	Collection<? extends Constraint> getRejectedConstraints();

	/**
	 * Accepted parents in the feature model, feature name to its parent's name: one parent each, no
	 * cycles. Not a {@link Constraint}: a parent's only logical content - the feature requires its
	 * parent - is accepted with it as a REQUIRES; which of the features it requires is its parent is
	 * the model's structure, which configurations cannot decide (She et al., "Reverse engineering
	 * feature models", ICSE 2011). Stored like the constraints, so it travels with fork/pull/push.
	 * Empty on a repository written before parents were stored.
	 */
	Map<String, String> getFeatureParents();

	/** Rejected parent suggestions: feature name to the parents rejected for it. */
	Map<String, Set<String>> getRejectedFeatureParents();

	/** Whether making [parent] the parent of [feature] closes a cycle in [parents]: [parent] is [feature] or below it. */
	static boolean makesCycle(Map<String, String> parents, String feature, String parent) {
		Set<String> seen = new HashSet<>();
		for (String f = parent; f != null && seen.add(f); f = parents.get(f))
			if (f.equals(feature))
				return true;
		return false;
	}

	ArrayList<Variant> getVariants();

	Variant getVariant(Configuration configuration);

	Variant getVariant(String id);

	Association getAssociation(String id);

	ArrayList<Feature> getFeature();

	void addVariant(Variant variant);

	void removeVariant(Variant variant);

	void updateVariant(Variant variant, Configuration configuration, String name);

	Collection<Commit> getCommits();

	void setCommits(Collection<Commit> commits);

	void addCommit(Commit commit);

	Collection<FeatureTrace> getFeatureTraces();

	void setMaintreeBuildingStrategy(MainTreeBuildingStrategy mainTreeBuildingStrategy);
	MainTreeBuildingStrategy getMainTreeBuildingStrategy();

	void setEvaluationStrategy(EvaluationStrategy evaluationStrategy);
	EvaluationStrategy getEvaluationStrategy();

	/**
	 * Private repository interface.
	 */
	interface Op extends Repository {

		@Override
		Collection<? extends Feature> getFeatures();

		@Override
		Collection<? extends Association.Op> getAssociations();

		/**
		 * Returns an unmodifiable collection of modules in the repository.
		 *
		 * @return Unmodifiable collection of modules in the repository.
		 */
		//public Collection<? extends Module> getModules();

		/**
		 * Returns an unmodifiable collection of modules of given order in the repository.
		 *
		 * @param order The order of retrieved modules
		 * @return The collection of modules.
		 */
		Collection<? extends Module> getModules(int order);

		/**
		 * Retrieves the feature stored in this repository with the given id. If no such feature exists in this repository, null is returned.
		 *
		 * @param id The id of the feature.
		 * @return The feature with the given id, or null if there is no such feature.
		 */
		Feature getFeature(String id);

		Feature getOrphanedFeature(String id, String name);

		Feature addFeature(String id, String name);

		@Override
		Collection<? extends Constraint> getConstraints();

		/**
		 * Retrieves the constraint stored in this repository with the given id (its natural
		 * {@code kind|featureA|featureB} signature). If no such constraint exists, null is returned.
		 *
		 * @param id The id of the constraint.
		 * @return The constraint with the given id, or null if there is no such constraint.
		 */
		Constraint getConstraint(String id);

		/**
		 * Adds a constraint with the given kind/features if one with the same natural id does not
		 * already exist (mirrors {@link #addFeature}'s idempotent-add shape).
		 *
		 * @param kind     of the constraint
		 * @param featureA antecedent / first feature / mandatory feature name
		 * @param featureB consequent / second feature name; null for {@link Constraint.Kind#MANDATORY}
		 * @return The newly added constraint, or null if one with the same id already existed.
		 */
		Constraint addConstraint(Constraint.Kind kind, String featureA, String featureB);

		/**
		 * Removes a constraint (e.g. GUI "move back to pending" / undo-accept). No-op if not present.
		 *
		 * @param constraint to remove.
		 */
		void removeConstraint(Constraint constraint);

		@Override
		Collection<? extends Constraint> getRejectedConstraints();

		/**
		 * @param id The natural {@code kind|featureA|featureB} id.
		 * @return The rejected constraint with the given id, or null if there is none.
		 */
		Constraint getRejectedConstraint(String id);

		/**
		 * Records a rejected suggestion if one with the same natural id is not recorded yet; does not
		 * touch the accepted constraints (see {@code ConstraintService}, which keeps the two apart).
		 *
		 * @return The newly recorded rejection, or null if it already existed.
		 */
		Constraint addRejectedConstraint(Constraint.Kind kind, String featureA, String featureB);

		/**
		 * Removes a rejection (e.g. GUI "move back to pending"). No-op if not present.
		 */
		void removeRejectedConstraint(Constraint constraint);

		/** Makes [parent] the parent of [feature], or - null - takes its parent away. Checks no cycle: see {@link Repository#makesCycle}. */
		void setFeatureParent(String feature, String parent);

		void addRejectedFeatureParent(String feature, String parent);

		void removeRejectedFeatureParent(String feature, String parent);

		void addVariant(Variant variant);

		void addAssociation(Association.Op association);

		/**
		 * Re-populates a repository's in-memory associations after they were carried separately from
		 * the "core" repository object -- needed because implementations that persist each association
		 * as its own independently-stored entity (see {@code SerRepository}) typically hold them behind
		 * a transient index that isn't part of the repository's own (de)serialized form, e.g. when a
		 * {@code Repository.Op} is sent whole over a raw socket (see {@code EccoService#pull}/
		 * {@code #push}/{@code #startServer}) rather than reloaded through the normal per-association-file
		 * DAO path. No-op by default; only meaningful for such implementations.
		 *
		 * @param associations the associations to restore, typically read as a companion payload sent
		 *                     alongside this repository.
		 */
		default void restoreAssociations(Collection<? extends Association.Op> associations) {
			// no-op by default
		}

		/**
		 * Collects every distinct artifact reachable from this repository's associations' trees,
		 * deduplicated by identity -- an artifact reachable from more than one node/association (e.g. a
		 * shared, non-unique artifact) must not be collected twice, same concern as
		 * {@code SerRepository.dirtyArtifacts}. Companion to {@link #resolveArtifacts}: send this
		 * alongside a {@code Repository.Op} transferred whole over a raw socket (see
		 * {@code EccoService#pull}/{@code #push}/{@code #startServer}), since artifacts are otherwise
		 * held behind implementation-specific transient indices/references that ordinary serialization
		 * of this repository does not include -- see {@link #resolveArtifacts}'s javadoc for why.
		 *
		 * @return the distinct artifacts reachable from this repository's associations.
		 */
		default Collection<? extends Artifact.Op<?>> collectArtifacts() {
			Set<Artifact.Op<?>> artifacts = Collections.newSetFromMap(new IdentityHashMap<>());
			for (Association.Op association : this.getAssociations()) {
				collectArtifactsRec(association.getRootNode(), artifacts);
			}
			return artifacts;
		}

		private static void collectArtifactsRec(Node.Op node, Set<Artifact.Op<?>> artifacts) {
			if (node.getArtifact() != null) {
				artifacts.add(node.getArtifact());
			}
			for (Node.Op child : node.getChildren()) {
				collectArtifactsRec(child, artifacts);
			}
		}

		/**
		 * Resolves each of this repository's associations' node artifact references against the given
		 * artifacts -- companion to {@link #collectArtifacts}, and to {@link #restoreAssociations} (call
		 * this AFTER that, since it walks {@link #getAssociations()}). No-op by default; only meaningful
		 * for implementations that hold a node's artifact behind a transient, node-external reference
		 * (e.g. {@code SerRepository}/{@code SerNode}, deliberately, so that an artifact reachable from
		 * more than one node/association is resolved to one shared instance rather than redundantly
		 * re-created once per node -- the same class of bug documented as
		 * the duplicated-shared-artifact bug). Such an implementation would
		 * otherwise only restore this via its normal per-artifact-file DAO load path, which a
		 * {@code Repository.Op} sent whole over a raw socket bypasses entirely.
		 *
		 * @param artifacts the artifacts to resolve against, typically read as a companion payload sent
		 *                  alongside this repository (see {@link #collectArtifacts}).
		 */
		default void resolveArtifacts(Collection<? extends Artifact.Op<?>> artifacts) {
			// no-op by default
		}

		void removeVariant(Variant variant);

		void removeAssociation(Association.Op association);

		Module getOrphanedModule(Feature[] posFeatures, Feature[] neg);

		int getMaxOrder();

		void setMaxOrder(int maxOrder);

		EntityFactory getEntityFactory();

		/**
		 * Sets the retroactive conditions of every association's unique nodes - after a commit or a
		 * merge, and when a repository is read: an association's condition depends on how often each
		 * feature combination was committed in the whole repository, so a commit changes those of
		 * associations it did not touch. They are derived, not stored (SerFeatureTrace), so nothing
		 * needs writing for them (ReopenEquivalenceTest).
		 */
		default void setRetroactiveConditions(){
			for (Association.Op association : this.getAssociations())
				association.setRetroactiveConditions();
		}

		void buildMainTree();

		/**
		 * The proactive feature traces the main tree does not use because they contradict the
		 * commit history (see ProactiveTraceCheck) - none for strategies that do not check.
		 */
		default List<RejectedTrace> getRejectedTraces() {
			return List.of();
		}

		Node.Op getMainTree();

		void invalidateMainTree();

		default Collection<FeatureRevision> addFeatureRevisions(FeatureRevision[] featureRevisions){
			return CommitExtraction.addFeatureRevisions(this, featureRevisions);
		}

		/**
		 * Retrieves the module instance with given positive and negative features from the repository.
		 *
		 * @param pos Positive features of the module.
		 * @param neg Negative features of the module.
		 * @return The module or null if it does not exist.
		 */
		Module getModule(Feature[] pos, Feature[] neg);

		/**
		 * Checks if the given module already exists and throws an exception if it does.
		 * Otherwise the new module is added to the repository.
		 *
		 * @param pos Positive features of the module.
		 * @param neg Negative features of the module.
		 * @return The module instance that was added to the repository.
		 */
		Module addModule(Feature[] pos, Feature[] neg);

		/**
		 * Returns a collection of all features in this repository with the given name.
		 * Returns the collection of features with the given name. A feature is uniquely identified by its id. A name should be, but does not need to be, unique.
		 *
		 * @param name The name of the feature(s)
		 * @return The collection of features with the given name.
		 */
		default Collection<Feature> getFeaturesByName(String name) {
			Collection<Feature> features = new ArrayList<>();
			for (Feature feature : this.getFeatures()) {
				if (feature.getName().equals(name))
					features.add(feature);
			}
			return features;
		}

		/**
		 * Adds new modules to the repository that contain the new feature negatively.
		 *
		 * @param feature The new feature.
		 */
		default void addNegativeFeatureModules(Feature feature) {
			CommitExtraction.addNegativeFeatureModules(this, feature);
		}

		/**
		 * Adds all features and feature revisions in the given configuration that are not already contained in the repository to the repository.
		 * In the process, all associations in the repository are updated with new modules containing the new features negatively.
		 * Returns a collection of all feature revision instances in the repository that are contained in the repository, those that already existed and those that were added.
		 *
		 * @param configuration The configuration whose features are added to the repository.
		 * @return Collection of all feature revision instances of the repository that are contained in the configuration.
		 */
		default Collection<FeatureRevision> addConfigurationFeatures(Configuration configuration) {
			checkNotNull(configuration);
			// add new features and feature revisions from configuration to this repository
			return this.addFeatureRevisions(configuration.getFeatureRevisions());
		}

		/**
		 * Compute modules for configuration. New modules are added to the repository. Old ones have their counter incremented.
		 * <p>
		 * Uses all feature revisions of the configuration positively and all other features contained in the repository but not in the configuration negatively.
		 * <p>
		 * Uses feature and feature revision instances contained in the repository and discards the instances in the configuration.
		 * <p>
		 * Uses module and module revision instances contained in the repository.
		 * If a module or module revision does not yet exist in the repository it is created and added to the repository.
		 *
		 * @param configuration The configuration whose modules are computed and added to the repository.
		 * @return Collection of all module revision instances of the repository that are contained in the configuration.
		 */
		default Collection<ModuleRevision> addConfigurationModules(Configuration configuration) {
			return CommitExtraction.addConfigurationModules(this, configuration);
		}

		/**
		 * Extracts new associations and refines existing associations in this repository based on the given configuration and artifact tree.
		 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
		 *
		 * @param configuration The configuration describing the given artifact tree.
		 * @param nodes         The root node of the artifact tree representing the implementation of the given configuration.
		 * @param committer     The Name of the user who committed.
		 * @return The commit object.
		 */
		default Commit extract(Configuration configuration, Set<Node.Op> nodes, String committer) {
			return CommitExtraction.extract(this, configuration, nodes, committer);
		}

		/**
		 * When an association is committed directly then the corresponding configuration must be added manually first!
		 *
		 * @param association The association to be committed.
		 */
		default void extract(Association.Op association, Commit commit) {
			CommitExtraction.extract(this, association, commit);
		}

		/**
		 * Returns modules contained in the given configuration. Features, Modules, Revisions are taken from the repository if they exist, otherwise they are created as temporary orphaned objets. For example, a feature revision that is not actually added to the feature.
		 *
		 * @param configuration
		 * @return
		 */
		//private
		default Collection<ModuleRevision> getOrphanedConfigurationModules(Configuration configuration) {
			return CommitExtraction.getOrphanedConfigurationModules(this, configuration);
		}

		/**
		 * Composes an artifact tree from the associations stored in this repository that implements the given configuration.
		 *
		 * @param configuration The configuration for which the implementing artifact tree shall be retrieved.
		 * @return The checkout object.
		 */
		default Checkout compose(Configuration configuration) {
			return this.compose(configuration, Map.of());
		}

		/**
		 * Like {@link #compose(Configuration)}, but the files come from a main tree in which the
		 * associations named in {@code retroactiveConditions} carry those conditions instead of their
		 * own (see {@link MainTreeBuildingStrategy#buildMainTree(Collection, ProactiveTraceCheck, Map)}).
		 * That main tree is built for this checkout only; the cached one is not touched. Which
		 * associations count as selected, and the warnings, still come from their own conditions.
		 */
		default Checkout compose(Configuration configuration, Map<String, LogicCondition> retroactiveConditions) {
			return CheckoutComposition.compose(this, configuration, retroactiveConditions);
		}

		default Checkout compose(Collection<? extends Association.Op> selectedAssociations) {
			return CheckoutComposition.compose(this, selectedAssociations);
		}

		/**
		 * Maps the given tree (e.g. result from a reader) to the repository without modifying the repository by replacing the artifacts in the given tree.
		 * This way a reader could keep reading a file after it was changed, map it to the repository, and have the trace information again.
		 * The nodes contain the updated line/col information from the reader, and the marking can still be done on the artifacts in the repository.
		 * This also enables highlighting of selected associations in changed files.
		 *
		 * @param rootNode The root node of the artifact tree to be mapped.
		 */
		default void map(RootNode.Op rootNode) {
			Collection<? extends Association.Op> associations = this.getAssociations();
			for (Association.Op association : associations) {
				Trees.map(association.getRootNode(), rootNode);
			}
		}

		/**
		 * Copies this repository into {@code target}, a repository just initialized: its features,
		 * constraints and parents, its modules and associations with their exact counts, and its commits
		 * with their ids, configurations, dates, messages and associations - what a fork of everything is.
		 * {@link #subset} instead rebuilt each association's counts from its condition, counted once, and a
		 * {@link #merge} into the empty repository recorded a merge commit per association: the fork lost
		 * its commits and counts, and a later pull could neither tell which commits both have nor count
		 * the history they share once (ForkCopyTest, DivergedSyncTest).
		 */
		default void copyInto(Repository.Op target) {
			RepositoryCopy.copyInto(this, target);
		}

		/**
		 * Copies [commits] of this repository into {@code target}, a repository just initialized, as
		 * {@link #copyInto(Repository.Op)} copies all of them: with the associations holding their
		 * content and every feature, but without modules and counts, which a replay derives again - what
		 * a pull or push sends of the commits the other side does not have, to be replayed there
		 * ({@link #replay}). The modules of a repository of many features are millions.
		 */
		default void copyCommitsInto(Repository.Op target, List<? extends Commit> commits) {
			RepositoryCopy.copyCommitsInto(this, target, commits);
		}

		/**
		 * Commits, in this repository, a commit of another repository - its configuration and the
		 * associations that hold its content there - as committing that content here would: the
		 * associations' trees, copied in one context (EccoUtil.TreeCopy) and joined into one, are the
		 * commit's tree as read, every node its own, the children of an ordered artifact in the order
		 * of its order graph there, trimmed to the commit's artifacts (written to files and read again,
		 * their order came back as the checkout's, other than committed). Pull and push replay
		 * the commits one side does not have, so a repository after them is one with the same commits
		 * made in order - merging associations counted the history both share twice
		 * (DivergedSyncTest).
		 *
		 * @return the commit made here, its id and the rest still to be set by the caller
		 */
		default Commit replay(Configuration configuration, Collection<? extends Association.Op> associations, String committer) {
			return RepositoryCopy.replay(this, configuration, associations, committer);
		}

		/**
		 * Whether a commit has no configuration: one a merge recorded for each association it merged -
		 * a fork or pull of an older version - which replaying cannot repeat. Pull and push then merge,
		 * as before.
		 */
		default boolean hasMergeCommits() {
			return this.getCommits().stream().anyMatch(commit -> commit.getConfiguration() == null);
		}

		/**
		 * This repository's commits whose ids are not in [ids], in their order, leaving out those with a
		 * [deselected] revision in their configuration - what a pull or push replays on the side that
		 * has [ids].
		 */
		default List<Commit> commitsMissingIn(Collection<String> ids, Collection<FeatureRevision> deselected) {
			return RepositoryCopy.commitsMissingIn(this, ids, deselected);
		}

		/**
		 * [configuration], of another repository, on this repository's features: a feature this
		 * repository has by id or, made on its own on both sides, by name is the same feature; a
		 * revision it has is the same revision. The rest are added when committed.
		 */
		default Configuration adoptConfiguration(Configuration configuration) {
			return RepositoryCopy.adoptConfiguration(this, configuration);
		}

		default Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
			return RepositoryCopy.subset(this, deselected, maxOrder, entityFactory);
		}

		/**
		 * Creates a copy of this repository using the same entity factory and maximum order of modules. This repository is not changed.
		 *
		 * @param entityFactory The entity factory used for creating the copy of this repository.
		 * @return The copied repository.
		 */
		default Repository.Op copy(EntityFactory entityFactory) {
			return this.subset(new ArrayList<>(), this.getMaxOrder(), entityFactory);
		}

		/**
		 * Fails if the repository holds artifacts an older version of their adapter wrote in a way
		 * the adapter no longer reads, so adding a commit (or merging) would mix both:
		 * <ul>
		 * <li>data that requires ordering (ArtifactData#requiresOrderedArtifact) stored unordered - it
		 * does not equal the ordered artifact read now, and with both, checkouts silently dropped them
		 * (the order selector places only children it finds in the parent's order graph);</li>
		 * <li>data of a retired format (ArtifactData#retiredFormat).</li>
		 * </ul>
		 * Checkouts of the repository as it is still work.
		 */
		default void checkArtifactFormats() {
			RepositoryMerge.checkArtifactFormats(this);
		}

		/**
		 * Takes over the other repository's decisions this one has not made: its accepted and rejected
		 * constraint suggestions and feature parents - what a merge, pull or push brings along besides
		 * the content.
		 */
		default void mergeDecisions(Repository.Op otherRepository) {
			RepositoryMerge.mergeDecisions(this, otherRepository);
		}

		default void merge(Repository.Op otherRepository) {
			RepositoryMerge.merge(this, otherRepository);
		}

	}

}
