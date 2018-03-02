package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.EmptyModule;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Associations;
import at.jku.isse.ecco.util.Trees;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RepositoryOperator {

	private Repository.Op repository;
	private EntityFactory entityFactory;

	public RepositoryOperator(Repository.Op repository) {
		checkNotNull(repository);
		this.repository = repository;
		this.entityFactory = repository.getEntityFactory();
	}


	/**
	 * Returns a collection of all features in this repository with the given name.
	 *
	 * @param name Name of the features.
	 * @return Collection of features with given name.
	 */
	public Collection<Feature> getFeaturesByName(String name) {
		Collection<Feature> features = new ArrayList<>();
		for (Feature feature : this.repository.getFeatures()) {
			if (feature.getName().equals(name))
				features.add(feature);
		}
		return features;
	}


	/**
	 * Adds all features and feature revisions in the given configuration that are not already contained in the repository to the repository.
	 * In the process, all associations in the repository are updated with new modules containing the new features negatively.
	 * Returns a collection of all feature revisions instances in the repository that are contained in the repository, those that already existed and those that were added.
	 *
	 * @param configuration The configuration whose features are added to the repository.
	 * @return Collection of all feature revision instances of the repository that are contained in the configuration.
	 */
	private Collection<FeatureRevision> addConfigurationFeatures(Configuration configuration) {
		checkNotNull(configuration);

		// add new features and feature revisions from configuration to this repository
		Collection<FeatureRevision> repoFeatureRevisions = new ArrayList<>();
		for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
			Feature feature = featureRevision.getFeature();
			// get/add feature from/to repository
			Feature repoFeature = this.repository.getFeature(feature.getId());
			if (repoFeature == null) {
				repoFeature = this.repository.addFeature(feature.getId(), feature.getName(), feature.getDescription());

				// add new modules to the repository that contain the new feature negatively. copies every existing module and adds the new feature negatively.
				for (Module module : this.repository.getModules()) {
					// only add modules that do not exceed the maximum order of modules in the repository
					if (module.getOrder() < this.repository.getMaxOrder()) {
						// create array of negative features. to be reused also by every revision module.
						Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
						negFeatures[negFeatures.length - 1] = repoFeature;
						// create copy of module with new feature negative
						Module newModule = this.repository.addModule(module.getPos(), negFeatures);
						newModule.setCount(module.getCount());

						// do the same for the revision modules
						for (ModuleRevision moduleRevision : module.getRevisions()) {
							// create copy of module revision with new feature negative
							ModuleRevision newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
							newModuleRevision.setCount(moduleRevision.getCount());
							// update existing associations that have matching old module with the new module
							for (Association.Op association : this.repository.getAssociations()) {
								ModuleCounter existingModuleCounter = association.getCounter().getChild(moduleRevision.getModule());
								if (existingModuleCounter != null) {
									ModuleRevisionCounter existingModuleRevisionCounter = existingModuleCounter.getChild(moduleRevision);
									if (existingModuleRevisionCounter != null) {
										association.addObservation(newModuleRevision, existingModuleRevisionCounter.getCount());
									}
								}
							}
						}
					}
				}
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
	 * @return All module revision instances of the repository that are contained in the configuration.
	 */
	private Collection<ModuleRevision> addConfigurationModules(Configuration configuration) {
		checkNotNull(configuration);

		// collect positive feature revisions
		Collection<FeatureRevision> pos = new ArrayList<>();
		for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
			// get feature from repository
			Feature repoFeature = this.repository.getFeature(featureRevision.getFeature().getId());
			if (repoFeature == null)
				throw new EccoException("ERROR: feature does not exist in repository: " + featureRevision.getFeature());
			// get feature revision from repository
			FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureRevision.getId());
			if (repoFeatureRevision == null)
				throw new EccoException("ERROR: feature revision does not exist in repository: " + featureRevision);
			pos.add(repoFeatureRevision);
		}

		// collect negative features
		Collection<Feature> neg = new ArrayList<>();
		for (Feature repoFeature : this.repository.getFeatures()) {
			if (pos.stream().noneMatch(featureRevision -> featureRevision.getFeature().equals(repoFeature))) {
				neg.add(repoFeature);
			}
		}

		// collection of modules
		Collection<ModuleRevision> modules = new ArrayList<>();

		// add empty module initially
		Module emptyModule = new EmptyModule();
		ModuleRevision emptyModuleRevision = emptyModule.getRevision(new FeatureRevision[0], new Feature[0]);
		modules.add(emptyModuleRevision); // add empty module revision to power set

		// compute powerset
		for (final FeatureRevision featureRevision : pos) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision module : modules) {
				if (module.getOrder() < this.repository.getMaxOrder()) {
					FeatureRevision[] posFeatureRevisions = Arrays.copyOf(module.getPos(), module.getPos().length + 1);
					posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;

					// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
					ModuleRevision newModuleRevision = this.repository.getModuleRevision(posFeatureRevisions, module.getNeg());
					if (newModuleRevision == null) {
						newModuleRevision = this.repository.addModuleRevision(posFeatureRevisions, module.getNeg());
					}
					newModuleRevision.incCount();

					toAdd.add(newModuleRevision);
				}
			}

			modules.addAll(toAdd);
		}

		// remove the empty module again
		modules.remove(emptyModuleRevision);

		for (final Feature feature : neg) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision module : modules) {
				if (module.getOrder() < this.repository.getMaxOrder() && module.getPos().length > 0) {
					Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
					negFeatures[negFeatures.length - 1] = feature;

					// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
					ModuleRevision newModuleRevision = this.repository.getModuleRevision(module.getPos(), negFeatures);
					if (newModuleRevision == null) {
						newModuleRevision = this.repository.addModuleRevision(module.getPos(), negFeatures);
					}
					newModuleRevision.incCount();

					toAdd.add(newModuleRevision);
				}
			}

			modules.addAll(toAdd);
		}

		return modules;
	}


	/**
	 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration that is committed.
	 * @param nodes         The artifact nodes that implement the given configuration.
	 * @return The resulting commit object or null in case of an error.
	 */
	public Commit extract(Configuration configuration, Set<Node.Op> nodes) {
		checkNotNull(configuration);
		checkNotNull(nodes);

		// add configuration features and revisions
		Collection<FeatureRevision> repoFeatureRevisions = this.addConfigurationFeatures(configuration);

		// create configuration with repo feature revisions
		Configuration repoConfiguration = this.entityFactory.createConfiguration(repoFeatureRevisions.toArray(new FeatureRevision[repoFeatureRevisions.size()]));

		// add configuration modules and module revisions
		Collection<ModuleRevision> moduleRevisions = this.addConfigurationModules(repoConfiguration);

		// create and initialize new association
		Association.Op association = this.entityFactory.createAssociation(nodes);
		association.setId(UUID.randomUUID().toString());
		association.getCounter().setCount(1);
		for (ModuleRevision moduleRevision : moduleRevisions) {
			association.addObservation(moduleRevision);
		}

		// do actual extraction
		this.extract(association);

		// create commit object
		Commit commit = this.entityFactory.createCommit();
		commit.setConfiguration(repoConfiguration);

		return commit;
	}

	/**
	 * When associations are committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param inputAs The collection of associations to be committed.
	 */
	private void extract(Collection<? extends Association.Op> inputAs) {
		checkNotNull(inputAs);

		for (Association.Op inputA : inputAs) {
			this.extract(inputA);
		}
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association The association to be committed.
	 */
	private void extract(Association.Op association) {
		checkNotNull(association);

		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();

		Collection<Association.Op> toAdd = new ArrayList<>();
		Collection<Association.Op> toRemove = new ArrayList<>();

		// slice new association with every original association
		for (Association.Op origA : originalAssociations) {
			// ASSOCIATION
			// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
			Association.Op intA = this.entityFactory.createAssociation();
			intA.setId(UUID.randomUUID().toString());

			// ARTIFACT TREE
			//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
			intA.setRootNode((RootNode.Op) Trees.slice(origA.getRootNode(), association.getRootNode()));

			// INTERSECTION
			if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
				toAdd.add(intA);

				Trees.checkConsistency(intA.getRootNode());

				intA.add(origA);
				intA.add(association);
			}

			// ORIGINAL
			if (!origA.getRootNode().getChildren().isEmpty()) { // if the original association has artifacts left
				Trees.checkConsistency(origA.getRootNode());
			} else {
				toRemove.add(origA);
			}
		}

		// REMAINDER
		if (!association.getRootNode().getChildren().isEmpty()) { // if the remainder is not empty store it
			toAdd.add(association);

			Trees.sequence(association.getRootNode());
			Trees.updateArtifactReferences(association.getRootNode());
			Trees.checkConsistency(association.getRootNode());
		}

		// remove associations from repository
		for (Association.Op origA : toRemove) {
			this.repository.removeAssociation(origA);
		}

		// add associations to repository
		for (Association.Op newA : toAdd) {
			this.repository.addAssociation(newA);
		}
	}


	public Checkout compose(Configuration configuration) {
		return this.compose(configuration, true);
	}

	private Checkout compose(Configuration configuration, boolean lazy) {
		checkNotNull(configuration);

		Set<Association> selectedAssociations = new HashSet<>();
		for (Association association : this.repository.getAssociations()) {
			if (association.computeCondition().holds(configuration)) {
				selectedAssociations.add(association);
			}
		}

		Checkout checkout = this.compose(selectedAssociations, lazy);
		checkout.setConfiguration(configuration);


		// TODO: compute warnings!

		// for missing check against repository modules: foreach module in repository check configuration.contains(module); if not then add to missing;
		// for surplus only check UNIQUE/AND ModuleConditions modules that are NOT in configuration: foreach module in

		Set<ModuleRevision> desiredModules = configuration.computeModules(this.repository.getMaxOrder());
		Set<ModuleRevision> missingModules = new HashSet<>();
		Set<ModuleRevision> surplusModules = new HashSet<>();

		// compute missing
		for (ModuleRevision desiredModule : desiredModules) {
			if (!this.repository.containsModuleRevision(desiredModule.getPos(), desiredModule.getNeg())) {
				missingModules.add(desiredModule);
			}
		}

		// compute surplus
		for (Association association : selectedAssociations) {
			Condition moduleCondition = association.computeCondition();
			if (moduleCondition.getType() == Condition.TYPE.AND) {
				Map<Module, Collection<ModuleRevision>> moduleMap = moduleCondition.getModules();
				for (Map.Entry<Module, Collection<ModuleRevision>> entry : moduleMap.entrySet()) {
					if (entry.getValue() != null) {
						for (ModuleRevision existingModuleRevision : entry.getValue()) {
							if (!desiredModules.contains(existingModuleRevision)) {
								surplusModules.add(existingModuleRevision);
							}
						}
					}
				}
			}
		}


		checkout.getSurplus().addAll(surplusModules);
		checkout.getMissing().addAll(missingModules);

		return checkout;
	}

	private Checkout compose(Collection<Association> selectedAssociations, boolean lazy) {
		Node compRootNode;
		Collection<Artifact<?>> orderWarnings;
		if (lazy) {
			LazyCompositionRootNode lazyCompRootNode = new LazyCompositionRootNode();

			for (Association association : selectedAssociations) {
				lazyCompRootNode.addOrigNode(association.getRootNode());
			}

			orderWarnings = lazyCompRootNode.getOrderSelector().getUncertainOrders();

			compRootNode = lazyCompRootNode;
		} else {
			// TODO: non-lazy composition and computation of order warnings!
			throw new EccoException("Non-lazy composition not yet implemented!");
		}

		// compute unresolved dependencies
		DependencyGraph dg = new DependencyGraph(selectedAssociations, DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);
		Set<Association> unresolvedAssociations = new HashSet<>(dg.getAssociations());
		unresolvedAssociations.removeAll(selectedAssociations);

		// put together result
		Checkout checkout = new Checkout();
		checkout.setNode(compRootNode);
		checkout.getOrderWarnings().addAll(orderWarnings);
		checkout.getUnresolvedAssociations().addAll(unresolvedAssociations);
		checkout.getSelectedAssociations().addAll(selectedAssociations);

		return checkout;
	}


	/**
	 * Maps the given tree (e.g. result from a reader) to the repository without modifying the repository by replacing the artifacts in the given tree.
	 * With this way a reader could keep reading a file after it was changed, map it to the repository, and have the trace information again.
	 * The nodes contain the updated line/col information from the reader, and the marking can still be done on the artifacts in the repository.
	 * This also enables highlighting of selected associations in changed files.
	 *
	 * @param nodes The tree to be mapped.
	 */
	public void map(Collection<RootNode> nodes) {
		Collection<? extends Association> associations = this.repository.getAssociations();

		for (Node.Op node : nodes) {
			for (Association association : associations) {
				Trees.map(association.getRootNode(), node);
			}
		}
	}


	/**
	 * Diffs the current working copy against the repository and returns a diff object containing all affected associations (and thus all affected features and artifacts).
	 *
	 * @return The diff object.
	 */
	public Diff diff() {
		// TODO
		return null;
	}


	/**
	 * Creates a copy of this repository using the same entity factory and maximum order of modules. This repository is not changed.
	 *
	 * @return The copy of the repository.
	 */
	public Repository.Op copy(EntityFactory entityFactory) {
		return this.subset(new ArrayList<>(), this.repository.getMaxOrder(), entityFactory);
	}


	/**
	 * Creates a subset repository of this repository using the given entity factory. This repository is not changed.
	 *
	 * @param deselected The deselected feature versions (i.e. feature versions that are set to false).
	 * @param maxOrder   The maximum order of modules.
	 * @return The subset repository.
	 */
	public Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
		checkNotNull(deselected);
		checkArgument(maxOrder <= this.repository.getMaxOrder());


		// create empty repository using the given entity factory
		Repository.Op newRepository = entityFactory.createRepository();
		newRepository.setMaxOrder(maxOrder);


		// add all features and versions in this repository to new repository, excluding the deselected feature versions.
		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureRevision> newFeatureVersions = new ArrayList<>();
		for (Feature feature : this.repository.getFeatures()) {
			Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName(), feature.getDescription());

			for (FeatureRevision featureVersion : feature.getRevisions()) {
				if (!deselected.contains(featureVersion)) {
					FeatureRevision newFeatureVersion = newFeature.addRevision(featureVersion.getId());
					newFeatureVersion.setDescription(featureVersion.getDescription());
					newFeatureVersions.add(newFeatureVersion);
					featureVersionReplacementMap.put(featureVersion, newFeatureVersion);
				}
			}

			if (!newFeature.getRevisions().isEmpty()) {
				featureReplacementMap.put(feature, newFeature);
			}
		}
		for (Association newAssociation : newRepository.getAssociations()) {
			for (FeatureRevision newFeatureVersion : newFeatureVersions) {
				newAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
				newAssociation.getPresenceCondition().addFeatureInstance(newFeatureVersion, false, newRepository.getMaxOrder());
			}
		}


		// copy associations in this repository and add them to new repository, but exclude modules or module features that evaluate to false given the deselected feature versions
		Collection<Association.Op> copiedAssociations = new ArrayList<>();
		for (Association association : this.repository.getAssociations()) {
			Association.Op copiedAssociation = entityFactory.createAssociation();
			copiedAssociation.setId(UUID.randomUUID().toString());

			PresenceCondition thisPresenceCondition = association.getPresenceCondition();


			// copy presence condition
			PresenceCondition copiedPresenceCondition = entityFactory.createPresenceCondition();
			copiedAssociation.setPresenceCondition(copiedPresenceCondition);

			Set<at.jku.isse.ecco.module.Module>[][] moduleSetPairs = new Set[][]{{thisPresenceCondition.getMinModules(), copiedPresenceCondition.getMinModules()}, {thisPresenceCondition.getMaxModules(), copiedPresenceCondition.getMaxModules()}, {thisPresenceCondition.getNotModules(), copiedPresenceCondition.getNotModules()}, {thisPresenceCondition.getAllModules(), copiedPresenceCondition.getAllModules()}};

			for (Set<at.jku.isse.ecco.module.Module>[] moduleSetPair : moduleSetPairs) {
				Set<at.jku.isse.ecco.module.Module> fromModuleSet = moduleSetPair[0];
				Set<at.jku.isse.ecco.module.Module> toModuleSet = moduleSetPair[1];

				for (at.jku.isse.ecco.module.Module fromModule : fromModuleSet) {
					at.jku.isse.ecco.module.Module toModule = entityFactory.createModule();
					for (ModuleFeature fromModuleFeature : fromModule) {

						// feature
						Feature fromFeature = fromModuleFeature.getFeature();
						Feature toFeature;
						if (featureReplacementMap.containsKey(fromFeature)) {
							toFeature = featureReplacementMap.get(fromFeature);

							// if a deselected feature version is contained in module feature:
							//  if module feature is positive: remove / do not add feature version from module feature
							//   if module feature is empty: remove it / do not add it
							//  else if module feature is negative: remove module feature from module
							//   if module is empty (should not happen?) then leave it! module is always TRUE (again: should not happen, because at least one positive module feature should be in every module, but that might currently not be the case)

							ModuleFeature toModuleFeature = entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
							boolean addToModule = true;
							for (FeatureRevision fromFeatureVersion : fromModuleFeature) {
								if (deselected.contains(fromFeatureVersion)) { // if a deselected feature version is contained in module feature

									if (fromModuleFeature.getSign()) {  // if module feature is positive
										// do not add feature version to module feature
									} else {
										// do not add module feature to module because it is always true
										addToModule = false;
										break;
									}

								} else { // ordinary copy
									FeatureRevision toFeatureVersion;
									if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
										toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
									} else {
										toFeatureVersion = fromFeatureVersion;

										throw new EccoException("This should not happen!");
									}
									toModuleFeature.add(toFeatureVersion);
								}
							}
							if (!toModuleFeature.isEmpty() && addToModule) { // if module feature is empty: do not add it
								toModule.add(toModuleFeature);
							}
							if (fromModuleFeature.getSign() && toModuleFeature.isEmpty()) { // don't add module because it is false
								toModule.clear();
								break;
							}
						} else {
							//toFeature = fromFeature;
							//throw new EccoException("This should not happen!");
							if (fromModuleFeature.getSign()) {
								toModule.clear();
								break;
							}
						}

					}
					if (!toModule.isEmpty())
						toModuleSet.add(toModule);
				}
			}


			// copy artifact tree
			RootNode.Op copiedRootNode = entityFactory.createRootNode();
			copiedAssociation.setRootNode(copiedRootNode);
			// clone tree
			for (Node.Op parentChildNode : association.getRootNode().getChildren()) {
				Node.Op copiedChildNode = EccoUtil.deepCopyTree(parentChildNode, entityFactory);
				copiedRootNode.addChild(copiedChildNode);
				copiedChildNode.setParent(copiedRootNode);
			}
			//Trees.checkConsistency(copiedRootNode);


			copiedAssociations.add(copiedAssociation);
		}

		for (Association a : copiedAssociations) {
			Trees.checkConsistency(a.getRootNode());
		}


		// remove (fixate) all provided (selected) feature instances in the presence conditions of the copied associations.
		// this is already done in the previous step

		// remove cloned associations with empty PCs.
		Iterator<Association.Op> associationIterator = copiedAssociations.iterator();
		while (associationIterator.hasNext()) {
			Association.Op association = associationIterator.next();
			if (association.getPresenceCondition().isEmpty())
				associationIterator.remove();
		}

		// compute dependency graph for selected associations and check if there are any unresolved dependencies.
		DependencyGraph dg = new DependencyGraph(copiedAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
		if (!dg.getUnresolvedDependencies().isEmpty()) {
			throw new EccoException("Unresolved dependencies in selection.");
		}

		// merge cloned associations with equal PCs.
		Associations.consolidate(copiedAssociations);

		// trim sequence graphs to only contain artifacts from the selected associations.
		EccoUtil.trimSequenceGraph(copiedAssociations);

		for (Association.Op copiedAssociation : copiedAssociations) {
			newRepository.addAssociation(copiedAssociation);
		}

		return newRepository;
	}


	/**
	 * Merges other repository into this repository. The other repository is destroyed in the process.
	 *
	 * @param other The other repository.
	 */
	public void merge(Repository.Op other) {
		checkNotNull(other);
		checkArgument(other.getClass().equals(this.repository.getClass()));

		// step 1: add new features and versions in other repository to associations in this repository,
		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureRevision> newThisFeatureVersions = new ArrayList<>();
		for (Feature otherFeature : other.getFeatures()) {
			Feature thisFeature = this.repository.getFeature(otherFeature.getId()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
			if (thisFeature == null) {
				thisFeature = this.repository.addFeature(otherFeature.getId(), otherFeature.getName(), otherFeature.getDescription());
			}

			for (FeatureRevision otherFeatureVersion : otherFeature.getRevisions()) {
				FeatureRevision thisFeatureVersion = thisFeature.getRevision(otherFeatureVersion.getId());
				if (thisFeatureVersion == null) {
					thisFeatureVersion = thisFeature.addRevision(otherFeatureVersion.getId());
					thisFeatureVersion.setDescription(otherFeatureVersion.getDescription());
					newThisFeatureVersions.add(thisFeatureVersion);
				}
				featureVersionReplacementMap.put(otherFeatureVersion, thisFeatureVersion);
			}

			if (!thisFeature.getRevisions().isEmpty()) {
				featureReplacementMap.put(otherFeature, thisFeature);
			}
		}
		for (Association thisAssociation : this.repository.getAssociations()) {
			for (FeatureRevision newThisFeatureVersion : newThisFeatureVersions) {
				thisAssociation.getPresenceCondition().addFeatureVersion(newThisFeatureVersion);
				thisAssociation.getPresenceCondition().addFeatureInstance(newThisFeatureVersion, false, this.repository.getMaxOrder());
			}
		}

		// step 2: add new features in this repository to associations in other repository.
		Collection<FeatureRevision> newOtherFeatureVersions = new ArrayList<>();
		for (Feature thisFeature : this.repository.getFeatures()) {
			Feature otherFeature = other.getFeature(thisFeature.getId());
			if (otherFeature == null) {
				// add all its versions to list
				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
					newOtherFeatureVersions.add(thisFeatureVersion);
				}
			} else {
				// compare versions and add new ones to list
				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
					FeatureRevision otherFeatureVersion = otherFeature.getRevision(thisFeatureVersion.getId());
					if (otherFeatureVersion == null) {
						newOtherFeatureVersions.add(thisFeatureVersion);
					}
				}
			}
		}
		for (Association otherAssociation : other.getAssociations()) {
			for (FeatureRevision newOtherFeatureVersion : newOtherFeatureVersions) {
				otherAssociation.getPresenceCondition().addFeatureVersion(newOtherFeatureVersion);
				otherAssociation.getPresenceCondition().addFeatureInstance(newOtherFeatureVersion, false, other.getMaxOrder());
			}
		}

		// step 3: commit associations in other repository to this repository.
		this.extract(other.getAssociations());
	}


	/**
	 * Splits all marked artifacts in the repository from their previous association into a new one.
	 *
	 * @return The commit object containing the affected associations.
	 */
	public Commit split() { // TODO: the presence condition must also somehow be marked and extracted! otherwise the repo becomes inconsistent.
		Commit commit = this.entityFactory.createCommit();

		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
		Collection<Association.Op> newAssociations = new ArrayList<>();

		// extract from every  original association
		for (Association.Op origA : originalAssociations) {

			// ASSOCIATION
			Association.Op extractedA = this.entityFactory.createAssociation();
			extractedA.setId(UUID.randomUUID().toString());


			// PRESENCE CONDITION
			//extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition())); // copy presence condition
			extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition()); // new empty presence condition


			// ARTIFACT TREE
			RootNode.Op extractedTree = (RootNode.Op) Trees.extractMarked(origA.getRootNode());
			if (extractedTree != null)
				extractedA.setRootNode(extractedTree);


			// if the intersection association has artifacts or a not empty presence condition store it
			if (extractedA.getRootNode() != null && (extractedA.getRootNode().getChildren().size() > 0 || !extractedA.getPresenceCondition().isEmpty())) {
				// set parents for intersection association (and child for parents)
				extractedA.setName("EXTRACTED " + origA.getId());

				// store association
				newAssociations.add(extractedA);
			}

			Trees.checkConsistency(origA.getRootNode());
			if (extractedA.getRootNode() != null)
				Trees.checkConsistency(extractedA.getRootNode());
		}

		for (Association.Op newA : newAssociations) {
			this.repository.addAssociation(newA);

//			commit.addAssociation(newA);
		}

		return commit;
	}


	/**
	 * Merges all associations that have the same presence condition.
	 */
	protected void consolidateAssociations() {
		Collection<Association.Op> toRemove = new ArrayList<>();

		Map<PresenceCondition, Association.Op> pcToAssocMap = new HashMap<>();

		Collection<? extends Association.Op> associations = this.repository.getAssociations();
		Iterator<? extends Association.Op> it = associations.iterator();
		while (it.hasNext()) {
			Association.Op association = it.next();
			Association.Op equalAssoc = pcToAssocMap.get(association.getPresenceCondition());
			if (equalAssoc == null) {
				pcToAssocMap.put(association.getPresenceCondition(), association);
			} else {
				Trees.merge(equalAssoc.getRootNode(), association.getRootNode());
				toRemove.add(association);
				it.remove();
			}
		}

		// delete removed associations
		for (Association.Op a : toRemove) {
			repository.removeAssociation(a);
		}
	}

	protected void mergeEmptyAssociations() {
		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
		Collection<Association.Op> toRemove = new ArrayList<>();
		Association emptyAssociation = null;

		// look for empty association
		for (Association originalAssociation : originalAssociations) {
			if (originalAssociation.getRootNode().getChildren().isEmpty()) {
				emptyAssociation = originalAssociation;
				break;
			}
		}
		if (emptyAssociation == null) { // if no empty association was found we are done
			return;
		}


		// TODO


		// delete removed associations
		for (Association.Op a : toRemove) {
			this.repository.removeAssociation(a);
		}
	}

}
