package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
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
		this.repository = repository;
		this.entityFactory = repository.getEntityFactory();
	}


	public Collection<Feature> getFeaturesByName(String name) {
		Collection<Feature> features = new ArrayList<Feature>();
		for (Feature feature : this.repository.getFeatures()) {
			if (feature.getName().equals(name))
				features.add(feature);
		}
		return features;
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
	public Repository.Op subset(Collection<FeatureVersion> deselected, int maxOrder, EntityFactory entityFactory) {
		checkNotNull(deselected);
		checkArgument(maxOrder <= this.repository.getMaxOrder());


		// create empty repository using the given entity factory
		Repository.Op newRepository = entityFactory.createRepository();
		newRepository.setMaxOrder(maxOrder);


		// add all features and versions in this repository to new repository, excluding the deselected feature versions.
		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
		Map<FeatureVersion, FeatureVersion> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureVersion> newFeatureVersions = new ArrayList<>();
		for (Feature feature : this.repository.getFeatures()) {
			Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName(), feature.getDescription());

			for (FeatureVersion featureVersion : feature.getVersions()) {
				if (!deselected.contains(featureVersion)) {
					FeatureVersion newFeatureVersion = newFeature.addVersion(featureVersion.getId());
					newFeatureVersion.setDescription(featureVersion.getDescription());
					newFeatureVersions.add(newFeatureVersion);
					featureVersionReplacementMap.put(featureVersion, newFeatureVersion);
				}
			}

			if (!newFeature.getVersions().isEmpty()) {
				featureReplacementMap.put(feature, newFeature);
			}
		}
		for (Association newAssociation : newRepository.getAssociations()) {
			for (FeatureVersion newFeatureVersion : newFeatureVersions) {
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
						} else {
							toFeature = fromFeature;

							throw new EccoException("This should not happen!");
						}


						// if a deselected feature version is contained in module feature:
						//  if module feature is positive: remove / do not add feature version from module feature
						//   if module feature is empty: remove it / do not add it
						//  else if module feature is negative: remove module feature from module
						//   if module is empty (should not happen?) then leave it! module is always TRUE (again: should not happen, because at least one positive module feature should be in every module, but that might currently not be the case)

						ModuleFeature toModuleFeature = entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
						boolean addToModule = true;
						for (FeatureVersion fromFeatureVersion : fromModuleFeature) {
							if (deselected.contains(fromFeatureVersion)) { // if a deselected feature version is contained in module feature

								if (fromModuleFeature.getSign()) {  // if module feature is positive
									// do not add feature version to module feature
								} else {
									// do not add module feature to module because it is always true
									addToModule = false;
									break;
								}

							} else { // ordinary copy
								FeatureVersion toFeatureVersion;
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

					}
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
			Trees.checkConsistency(copiedRootNode);


			copiedAssociations.add(copiedAssociation);
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
		Map<FeatureVersion, FeatureVersion> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureVersion> newThisFeatureVersions = new ArrayList<>();
		for (Feature otherFeature : other.getFeatures()) {
			Feature thisFeature = this.repository.getFeature(otherFeature.getId()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
			if (thisFeature == null) {
				thisFeature = this.repository.addFeature(otherFeature.getId(), otherFeature.getName(), otherFeature.getDescription());
			}

			for (FeatureVersion otherFeatureVersion : otherFeature.getVersions()) {
				FeatureVersion thisFeatureVersion = thisFeature.getVersion(otherFeatureVersion.getId());
				if (thisFeatureVersion == null) {
					thisFeatureVersion = thisFeature.addVersion(otherFeatureVersion.getId());
					thisFeatureVersion.setDescription(otherFeatureVersion.getDescription());
					newThisFeatureVersions.add(thisFeatureVersion);
				}
				featureVersionReplacementMap.put(otherFeatureVersion, thisFeatureVersion);
			}

			if (!thisFeature.getVersions().isEmpty()) {
				featureReplacementMap.put(otherFeature, thisFeature);
			}
		}
		for (Association thisAssociation : this.repository.getAssociations()) {
			for (FeatureVersion newThisFeatureVersion : newThisFeatureVersions) {
				thisAssociation.getPresenceCondition().addFeatureVersion(newThisFeatureVersion);
				thisAssociation.getPresenceCondition().addFeatureInstance(newThisFeatureVersion, false, this.repository.getMaxOrder());
			}
		}

		// step 2: add new features in this repository to associations in other repository.
		Collection<FeatureVersion> newOtherFeatureVersions = new ArrayList<>();
		for (Feature thisFeature : this.repository.getFeatures()) {
			Feature otherFeature = other.getFeature(thisFeature.getId());
			if (otherFeature == null) {
				// add all its versions to list
				for (FeatureVersion thisFeatureVersion : thisFeature.getVersions()) {
					newOtherFeatureVersions.add(thisFeatureVersion);
				}
			} else {
				// compare versions and add new ones to list
				for (FeatureVersion thisFeatureVersion : thisFeature.getVersions()) {
					FeatureVersion otherFeatureVersion = otherFeature.getVersion(thisFeatureVersion.getId());
					if (otherFeatureVersion == null) {
						newOtherFeatureVersions.add(thisFeatureVersion);
					}
				}
			}
		}
		for (Association otherAssociation : other.getAssociations()) {
			for (FeatureVersion newOtherFeatureVersion : newOtherFeatureVersions) {
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
				extractedA.addParent(origA);
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

			commit.addAssociation(newA);
		}

		return commit;
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

		// add new features and versions from configuration to this repository
		Collection<FeatureVersion> newFeatureVersions = new ArrayList<>();
		Configuration newConfiguration = this.entityFactory.createConfiguration();
		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
			Feature feature = featureInstance.getFeature();
			Feature repoFeature = this.repository.getFeature(featureInstance.getFeature().getId());
			if (repoFeature == null) {
				repoFeature = this.repository.addFeature(feature.getId(), feature.getName(), feature.getDescription());
			}
			FeatureVersion featureVersion = featureInstance.getFeatureVersion();
			FeatureVersion repoFeatureVersion = repoFeature.getVersion(featureVersion.getId());
			if (repoFeatureVersion == null) {
				repoFeatureVersion = repoFeature.addVersion(featureVersion.getId());
				repoFeatureVersion.setDescription(featureVersion.getDescription());
				newFeatureVersions.add(repoFeatureVersion);
			}
			FeatureInstance newFeatureInstance = this.entityFactory.createFeatureInstance(repoFeature, repoFeatureVersion, featureInstance.getSign());
			newConfiguration.addFeatureInstance(newFeatureInstance);
		}
		for (Association childAssociation : this.repository.getAssociations()) {
			for (FeatureVersion newFeatureVersion : newFeatureVersions) {
				childAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
				childAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), this.repository.getMaxOrder());
			}
		}

		// create presence condition
		PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(newConfiguration, this.repository.getMaxOrder());

		// create association
		Association.Op association = this.entityFactory.createAssociation(presenceCondition, nodes);
		association.setId(UUID.randomUUID().toString());

		// commit association
		Commit commit = this.extract(association);
		commit.setConfiguration(configuration);

		// TODO: consider this when committing associations that already have a presence table, or when merging repositories!
		// update presence table in all affected associations
		for (Association commitAssociation : commit.getAssociations()) {
			for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
				// find module feature in the map that has same feature and sign
				ModuleFeature moduleFeature = null;
				int count = 0;
				Iterator<Map.Entry<ModuleFeature, Integer>> iterator = commitAssociation.getPresenceTable().entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<ModuleFeature, Integer> entry = iterator.next();

					if (entry.getKey().getSign() == featureInstance.getSign() && entry.getKey().getFeature().equals(featureInstance.getFeature())) {
						moduleFeature = entry.getKey();
						count = entry.getValue();

						iterator.remove();
						break;
					}
				}
				if (moduleFeature == null) {
					moduleFeature = this.entityFactory.createModuleFeature(featureInstance.getFeature(), featureInstance.getSign());
				}
				moduleFeature.add(featureInstance.getFeatureVersion());
				count++;
				commitAssociation.getPresenceTable().put(moduleFeature, count);
			}
			commitAssociation.incPresenceCount();
		}

		return commit;
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association The association to be committed.
	 * @return The resulting commit object or null in case of an error.
	 */
	protected Commit extract(Association.Op association) {
		checkNotNull(association);

		Collection<Association.Op> associations = new ArrayList<>(1);
		associations.add(association);
		return this.extract(associations);
	}

	/**
	 * When associations are committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param inputAs The collection of associations to be committed.
	 * @return The resulting commit object or null in case of an error.
	 */
	protected Commit extract(Collection<? extends Association.Op> inputAs) {
		checkNotNull(inputAs);

		Commit commit = this.entityFactory.createCommit();

		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
		Collection<Association.Op> newAssociations = new ArrayList<>();
		Collection<Association.Op> removedAssociations = new ArrayList<>();

		Association emptyAssociation = null;
		// find initial empty association if there is any
		for (Association origA : originalAssociations) {
			if (origA.getRootNode().getChildren().isEmpty()) {
				emptyAssociation = origA;
				break;
			}
		}

		// process each new association individually
		for (Association.Op inputA : inputAs) {
			Collection<Association.Op> toAdd = new ArrayList<>();
			Collection<Association.Op> toRemove = new ArrayList<>();

			// slice new association with every original association
			for (Association.Op origA : originalAssociations) {

				// ASSOCIATION
				// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
				//Association intA = origA.slice(inputA);
				Association.Op intA = this.entityFactory.createAssociation();
				intA.setId(UUID.randomUUID().toString());


				// PRESENCE CONDITION
				//intA.setPresenceCondition(FeatureUtil.slice(origA.getPresenceCondition(), inputA.getPresenceCondition()));
				intA.setPresenceCondition(origA.getPresenceCondition().slice(inputA.getPresenceCondition()));


				// ARTIFACT TREE
				//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
				intA.setRootNode((RootNode.Op) Trees.slice(origA.getRootNode(), inputA.getRootNode()));

				// INTERSECTION
				if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
					// set parents for intersection association (and child for parents)
					intA.addParent(origA);
					intA.addParent(inputA);
					intA.setName(origA.getId() + " INT " + inputA.getId());

					toAdd.add(intA);

					commit.addUnmodified(intA);
					commit.addAssociation(intA);

					Trees.checkConsistency(intA.getRootNode());
				} else if (!intA.getPresenceCondition().isEmpty()) { // if it has no artifacts but a not empty presence condition merge it with other empty associations
					if (emptyAssociation == null) {
						emptyAssociation = intA;
						emptyAssociation.setName("EMPTY");
						toAdd.add(intA);
					} else if (emptyAssociation != intA) {
						emptyAssociation.getPresenceCondition().merge(intA.getPresenceCondition());
					}
				}

				// ORIGINAL
				if (origA.getRootNode().getChildren().isEmpty()) { // if the original association has no artifacts left
					if (!origA.getPresenceCondition().isEmpty()) { // if presence condition is not empty merge it
						if (emptyAssociation == null) {
							emptyAssociation = origA;
							emptyAssociation.setName("EMPTY");
						} else if (emptyAssociation != origA) {
							emptyAssociation.getPresenceCondition().merge(origA.getPresenceCondition());
							toRemove.add(origA);
						}
					} else {
						toRemove.add(origA);
					}
				} else {
					commit.addRemoved(origA);

					Trees.checkConsistency(origA.getRootNode());
				}


			}

			// REMAINDER
			// if the remainder is not empty store it
			if (!inputA.getRootNode().getChildren().isEmpty()) {
				Trees.sequence(inputA.getRootNode());
				Trees.updateArtifactReferences(inputA.getRootNode());
				Trees.checkConsistency(inputA.getRootNode());

				toAdd.add(inputA);

				commit.addNew(inputA);
				commit.addAssociation(inputA);
			} else if (!inputA.getPresenceCondition().isEmpty()) {
				if (emptyAssociation == null) {
					emptyAssociation = inputA;
					emptyAssociation.setName("EMPTY");
					toAdd.add(inputA);
				} else if (emptyAssociation != inputA) {
					emptyAssociation.getPresenceCondition().merge(inputA.getPresenceCondition());
				}
			}

			//originalAssociations.removeAll(toRemove);
			//originalAssociations.addAll(toAdd); // add new associations to original associations so that they can be sliced with the next input association
			newAssociations.addAll(toAdd);
			removedAssociations.addAll(toRemove);
		}

		// remove associations
		for (Association.Op origA : removedAssociations) {
			this.repository.removeAssociation(origA);
		}

		// add associations
		for (Association.Op newA : newAssociations) {
			this.repository.addAssociation(newA);
		}

		return commit;
	}


	public Checkout compose(Configuration configuration) {
		return this.compose(configuration, true);
	}

	public Checkout compose(Configuration configuration, boolean lazy) {
		checkNotNull(configuration);

		Set<Association> selectedAssociations = new HashSet<>();
		for (Association association : this.repository.getAssociations()) {
			if (association.getPresenceCondition().holds(configuration)) {
				selectedAssociations.add(association);
			}
		}

		Checkout checkout = this.compose(selectedAssociations, lazy);
		checkout.setConfiguration(configuration);


		Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.repository.getMaxOrder());
		Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
		Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();

		for (Association association : selectedAssociations) {
			// compute missing
			for (at.jku.isse.ecco.module.Module desiredModule : desiredModules) {
				if (!association.getPresenceCondition().getMinModules().contains(desiredModule)) {
					missingModules.add(desiredModule);
				}
			}
			// compute surplus
			for (at.jku.isse.ecco.module.Module existingModule : association.getPresenceCondition().getMinModules()) {
				if (!desiredModules.contains(existingModule)) {
					surplusModules.add(existingModule);
				}
			}
		}

		checkout.getSurplus().addAll(surplusModules);
		checkout.getMissing().addAll(missingModules);

		return checkout;
	}

	public Checkout compose(Collection<Association> selectedAssociations, boolean lazy) {
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
