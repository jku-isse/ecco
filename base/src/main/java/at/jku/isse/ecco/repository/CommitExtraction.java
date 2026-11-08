package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureLiterals;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.EmptyModule;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;
import java.util.*;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * What a commit does to the repository: the feature revisions and modules of its configuration, and the extraction of its associations from those already there - moved out of {@link Repository.Op}, whose default methods delegate here.
 */
final class CommitExtraction {

	private CommitExtraction() {
	}

	/** See {@link Repository.Op#addFeatureRevisions}. */
	static Collection<FeatureRevision> addFeatureRevisions(Repository.Op repository, FeatureRevision[] featureRevisions) {
		FeatureLiterals.checkDistinct(repository.getFeatures(), Arrays.asList(featureRevisions));
		Collection<FeatureRevision> repoFeatureRevisions = new ArrayList<>();
		for (FeatureRevision featureRevision : featureRevisions) {
			Feature feature = featureRevision.getFeature();
			// get/add feature from/to repository
			Feature repoFeature = repository.getFeature(feature.getId());
			if (repoFeature == null) {
				repoFeature = repository.addFeature(feature.getId(), feature.getName());
				repoFeature.setDescription(feature.getDescription());

				repository.addNegativeFeatureModules(repoFeature);
			}
			// get/add feature revision from/to repository
			FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureRevision.getId());
			if (repoFeatureRevision == null) {
				repoFeatureRevision = repoFeature.addRevision(featureRevision.getId());
				repoFeatureRevision.setDescription(featureRevision.getDescription());
			}
			repoFeatureRevisions.add(repoFeatureRevision);
		}
		return repoFeatureRevisions;
	}

	/** See {@link Repository.Op#addNegativeFeatureModules}. */
	static void addNegativeFeatureModules(Repository.Op repository, Feature feature) {
		checkNotNull(feature);

		// add new modules to the repository that contain the new feature negatively. copies every existing module and adds the new feature negatively.
		for (int currentOrder = repository.getMaxOrder() - 1; currentOrder >= 0; currentOrder--) {
			Collection<? extends Module> modules = repository.getModules(currentOrder);
			for (Module module : modules) {
				// only add modules that do not exceed the maximum order of modules in the repository
				if (module.getOrder() < repository.getMaxOrder()) {
					// create array of negative features. to be reused also by every revision module.
					Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
					negFeatures[negFeatures.length - 1] = feature;
					// create copy of module with new feature negative
					Module newModule = repository.addModule(module.getPos(), negFeatures);
					newModule.setCount(module.getCount());

					// do the same for the revision modules
					for (ModuleRevision moduleRevision : module.getRevisions()) {
						// create copy of module revision with new feature negative
						ModuleRevision newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
						newModuleRevision.setCount(moduleRevision.getCount());
						// update existing associations that have matching old module with the new module
						for (Association.Op association : repository.getAssociations()) {
							ModuleCounter existingModuleCounter = association.getCounter().getChild(moduleRevision.getModule());
							if (existingModuleCounter != null) {
								ModuleRevisionCounter existingModuleRevisionCounter = existingModuleCounter.getChild(moduleRevision);
								if (existingModuleRevisionCounter != null) {
									association.addObservation(newModuleRevision, existingModuleRevisionCounter.getCount());
									// this mutates an EXISTING association's counter (one that may not
									// otherwise be touched at all by the commit that introduced the new
									// feature) - nothing else marks it dirty for re-persistence, so
									// without this the retroactive observation is visible for the rest
									// of the live session but silently lost on the next reload, leaving
									// the reloaded condition missing valid "!newFeature" terms (too
									// broad, not just shorter). Safe to call again on an
									// already-tracked association - see SerRepository.addAssociation().
									repository.addAssociation(association);
								}
							}
						}
					}
				}
			}
		}
	}

	/** See {@link Repository.Op#addConfigurationModules}. */
	static Collection<ModuleRevision> addConfigurationModules(Repository.Op repository, Configuration configuration) {
		checkNotNull(configuration);

		// collect positive feature revisions
		Collection<FeatureRevision> pos = new ArrayList<>();
		for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
			// get feature from repository
			Feature repoFeature = repository.getFeature(featureRevision.getFeature().getId());
			if (repoFeature == null)
				throw new EccoException("ERROR: feature does not exist in repository: " + featureRevision.getFeature());
			// get feature revision from repository
			FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureRevision.getId());
			if (repoFeatureRevision == null)
				throw new EccoException("ERROR: feature revision does not exist in repository: " + featureRevision);
			pos.add(repoFeatureRevision);
		}

		// the module revisions of the repository, created if they do not exist yet, and counted
		return enumerateModules(repository, pos, (posFeatures, posFeatureRevisions, negFeatures) -> {
			Module module = repository.getModule(posFeatures, negFeatures);
			if (module == null) {
				module = repository.addModule(posFeatures, negFeatures);
			}
			module.incCount();
			ModuleRevision moduleRevision = module.getRevision(posFeatureRevisions, negFeatures);
			if (moduleRevision == null) {
				moduleRevision = module.addRevision(posFeatureRevisions, negFeatures);
			}
			moduleRevision.incCount();
			return moduleRevision;
		});
	}

	/** How {@link #enumerateModules} obtains the module revision of positive and negative features. */
	@FunctionalInterface
	interface ModuleRevisionSource {
		ModuleRevision get(Feature[] posFeatures, FeatureRevision[] posFeatureRevisions, Feature[] negFeatures);
	}

	/**
	 * The module revisions of a configuration with the feature revisions [pos] (all other features of
	 * this repository negative): every combination of up to max order positive feature revisions,
	 * then of those with negative features, each obtained from [source]. The one enumeration commit
	 * ({@link #addConfigurationModules}) and checkout ({@link #getOrphanedConfigurationModules}) share -
	 * two copies of it had to be kept the same (formula review H8).
	 */
	static Collection<ModuleRevision> enumerateModules(Repository.Op repository, Collection<FeatureRevision> pos, ModuleRevisionSource source) {
		// collect negative features
		Collection<Feature> neg = new ArrayList<>();
		for (Feature repoFeature : repository.getFeatures()) {
			if (pos.stream().noneMatch(featureRevision -> featureRevision.getFeature().equals(repoFeature))) {
				neg.add(repoFeature);
			}
		}

		// collection of modules
		Collection<ModuleRevision> moduleRevisions = new ArrayList<>();
		Collection<ModuleRevision> finalModuleRevisions = new ArrayList<>();

		// add empty module initially
		Module emptyModule = new EmptyModule();
		ModuleRevision emptyModuleRevision = emptyModule.getRevision(new FeatureRevision[0], new Feature[0]);
		moduleRevisions.add(emptyModuleRevision); // add empty module revision to power set

		// compute powerset
		for (final FeatureRevision featureRevision : pos) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision moduleRevision : moduleRevisions) {
				if (moduleRevision.getOrder() < repository.getMaxOrder()) {
					FeatureRevision[] posFeatureRevisions = Arrays.copyOf(moduleRevision.getPos(), moduleRevision.getPos().length + 1);
					posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;
					Feature[] posFeatures = Arrays.stream(posFeatureRevisions).map(FeatureRevision::getFeature).toArray(Feature[]::new);

					ModuleRevision newModuleRevision = source.get(posFeatures, posFeatureRevisions, moduleRevision.getNeg());

					if (newModuleRevision.getOrder() >= repository.getMaxOrder()) {
						finalModuleRevisions.add(newModuleRevision); // TODO: ???
					} else {
						toAdd.add(newModuleRevision);
					}
				}
			}

			moduleRevisions.addAll(toAdd);
		}

		// remove the empty module again
		moduleRevisions.remove(emptyModuleRevision);

		for (final Feature feature : neg) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision moduleRevision : moduleRevisions) {
				if (moduleRevision.getOrder() < repository.getMaxOrder() && moduleRevision.getPos().length > 0) {
					Feature[] negFeatures = Arrays.copyOf(moduleRevision.getNeg(), moduleRevision.getNeg().length + 1);
					negFeatures[negFeatures.length - 1] = feature;
					Feature[] posFeatures = Arrays.stream(moduleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);

					ModuleRevision newModuleRevision = source.get(posFeatures, moduleRevision.getPos(), negFeatures);

					if (newModuleRevision.getOrder() >= repository.getMaxOrder()) {
						finalModuleRevisions.add(newModuleRevision); // TODO: ???
					} else {
						toAdd.add(newModuleRevision);
					}
				}
			}

			moduleRevisions.addAll(toAdd);
		}

		finalModuleRevisions.addAll(moduleRevisions);

		return finalModuleRevisions;
	}

	/** See {@link Repository.Op#extract}. */
	static Commit extract(Repository.Op repository, Configuration configuration, Set<Node.Op> nodes, String committer) {
		checkNotNull(configuration);
		checkNotNull(nodes);

		{ // only one revision per feature is allowed in a configuration for commit
			FeatureRevision[] configurationFeatureRevisions = configuration.getFeatureRevisions();
			for (int i = 0; i < configurationFeatureRevisions.length; i++) {
				for (int j = i + 1; j < configurationFeatureRevisions.length; j++) {
					if (configurationFeatureRevisions[i].getFeature().equals(configurationFeatureRevisions[j].getFeature()))
						throw new EccoException("ERROR: For the commit operation only one revision per feature is allowed.");
				}
			}
		}

		// add configuration features and revisions
		Collection<FeatureRevision> repoFeatureRevisions = repository.addConfigurationFeatures(configuration);

		// create configuration with repo feature revisions
		Configuration repoConfiguration = repository.getEntityFactory().createConfiguration(repoFeatureRevisions.toArray(new FeatureRevision[0]));

		// set visibility of associations in repository
		for (Association.Op association : repository.getAssociations()) {
			association.setVisible(association.computeCertainCondition().holds(repoConfiguration));
			//association.setVisible(true);
		}

		// add configuration modules and module revisions
		Collection<ModuleRevision> moduleRevisions = repository.addConfigurationModules(repoConfiguration);

		// create and initialize new association
		Association.Op association = repository.getEntityFactory().createAssociation(nodes);
		association.setId(UUID.randomUUID().toString());
		association.getCounter().setCount(1);
		for (ModuleRevision moduleRevision : moduleRevisions) {
			association.addObservation(moduleRevision);
		}

		Commit commit = repository.getEntityFactory().createCommit(committer);
		commit.setConfiguration(repoConfiguration);
		repository.addCommit(commit);

		// do actual extraction
		repository.extract(association, commit);

		return commit;
	}

	/** See {@link Repository.Op#extract}. */
	static void extract(Repository.Op repository, Association.Op association, Commit commit) {
		checkNotNull(association);
		Trees.checkConsistency(association.getRootNode());

		Collection<? extends Association.Op> originalAssociations = repository.getAssociations();

		Collection<Association.Op> toAdd = new ArrayList<>();
		Collection<Association.Op> toRemove = new ArrayList<>();

		// Reverse index (association -> commits currently referencing it), built once up
		// front instead of rescanning every commit for every original association: that
		// old "for origA : originalAssociations { for c : getCommits() { if
		// c.containsAssociation(origA) ... } }" pattern is O(commits * associations) per
		// extract() call - i.e. O(n^2) per commit, O(n^3) across a sequence of n commits -
		// since both collections grow with the number of commits made so far. Associations
		// use identity equals/hashCode (no override), so an IdentityHashMap here is
		// equivalent to the old commit.containsAssociation()'s ArrayList#contains() checks.
		Map<Association, List<Commit>> commitsByAssociation = new IdentityHashMap<>();
		for (Commit c : repository.getCommits()) {
			for (Association a : c.getAssociations()) {
				commitsByAssociation.computeIfAbsent(a, k -> new ArrayList<>()).add(c);
			}
		}

		// slice new association with every original association
		for (Association.Op origA : originalAssociations) {
			// ASSOCIATION
			// slice the associations. the order matters here! the "left" association's features and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
			Association.Op intA = repository.getEntityFactory().createAssociation();
			intA.setId(UUID.randomUUID().toString());

			// ARTIFACT TREE
			//intA.setRootNode(origA.getRootNode().slice(association.getRootNode()));
			intA.setRootNode((RootNode.Op) Trees.slice(origA.getRootNode(), association.getRootNode()));

			// INTERSECTION
			boolean origAWasSliced = !intA.getRootNode().getChildren().isEmpty();
			if (origAWasSliced) { // if the intersection association has artifacts store it
				toAdd.add(intA);

				commit.addAssociation(intA);        // add association to new commit
				for (Commit c : commitsByAssociation.getOrDefault(origA, Collections.emptyList())) {        // updates associations in previous commits
					c.addAssociation(intA);
				}

				Trees.checkConsistency(intA.getRootNode());
				intA.getCounter().add(origA.getCounter());
				intA.getCounter().add(association.getCounter());
			}

			// ORIGINAL
			if (!origA.getRootNode().getChildren().isEmpty()) { // if the original association has artifacts left
				Trees.checkConsistency(origA.getRootNode());
				// origA's own tree just had the intersected part sliced out of it in place - if
				// that happened (origAWasSliced) it's now a different, smaller tree than what's on
				// disk, but nothing else marks an already-existing (not newly added, not fully
				// emptied) association dirty for re-persistence. Without this, closing and
				// reopening the repository loses the reduction: the next commit reloads origA at
				// its old, pre-slice size and redundantly re-slices content it already gave away,
				// producing duplicate associations. addAssociation() is safe to call again on an
				// already-tracked association - id/dirty-set membership are all idempotent (see
				// SerRepository.addAssociation()).
				if (origAWasSliced) {
					repository.addAssociation(origA);
				}
			} else {
				toRemove.add(origA);

				commit.deleteAssociation(origA);            // delete association from new commit
				for (Commit c : commitsByAssociation.getOrDefault(origA, Collections.emptyList())) {                // updates associations in previous commits
					c.deleteAssociation(origA);
				}

			}
		}

		// REMAINDER
		if (!association.getRootNode().getChildren().isEmpty()) { // if the remainder is not empty store it
			toAdd.add(association);
			commit.addAssociation(association);

			Trees.sequence(association.getRootNode());
			Trees.updateArtifactReferences(association.getRootNode());
			Trees.checkConsistency(association.getRootNode());
		}

		// remove associations from repository
		for (Association.Op origA : toRemove) {
			repository.removeAssociation(origA);
		}

		// add associations to repository
		for (Association.Op newA : toAdd) {
			repository.addAssociation(newA);
		}
	}

	/** See {@link Repository.Op#getOrphanedConfigurationModules}. */
	static Collection<ModuleRevision> getOrphanedConfigurationModules(Repository.Op repository, Configuration configuration) {
		checkNotNull(configuration);

		// collect positive feature revisions
		Collection<FeatureRevision> pos = new ArrayList<>();
		for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
			// get feature from repository
			Feature repoFeature = repository.getOrphanedFeature(featureRevision.getFeature().getId(), featureRevision.getFeature().getName());
			// get feature revision from repository
			FeatureRevision repoFeatureRevision = repoFeature.getOrphanedRevision(featureRevision.getId());
			pos.add(repoFeatureRevision);
		}

		// the module revisions of the repository where it has them, temporary ones otherwise
		return enumerateModules(repository, pos, (posFeatures, posFeatureRevisions, negFeatures) ->
				repository.getOrphanedModule(posFeatures, negFeatures).getOrphanedRevision(posFeatureRevisions, negFeatures));
	}
}
