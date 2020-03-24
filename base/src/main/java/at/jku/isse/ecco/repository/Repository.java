package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.EmptyModule;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Public repository interface. A repository contains {@link at.jku.isse.ecco.feature.Feature}s and {@link at.jku.isse.ecco.core.Association}s.
 */
public interface Repository extends Persistable {

	public Collection<? extends Feature> getFeatures();

	public Collection<? extends Association> getAssociations();


	/**
	 * Private repository interface.
	 */
	interface Op extends Repository {

		@Override
		public Collection<? extends Feature> getFeatures();

		@Override
		public Collection<? extends Association.Op> getAssociations();

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
		public Collection<? extends Module> getModules(int order);


		// TODO: document these! make clear where a check is performed for "already existing" or "null" etc.

		/**
		 * Retrieves the feature stored in this repository with the given id. If no such feature exists in this repository, null is returned.
		 *
		 * @param id The id of the feature.
		 * @return The feature with the given id, or null if there is no such feature.
		 */
		public Feature getFeature(String id);

		public Feature addFeature(String id, String name);


		public void addAssociation(Association.Op association);

		public void removeAssociation(Association.Op association);


		public int getMaxOrder();

		public void setMaxOrder(int maxOrder);


		public EntityFactory getEntityFactory();


		/**
		 * Retrieves the module instance with given positive and negative features from the repository.
		 *
		 * @param pos Positive features of the module.
		 * @param neg Negative features of the module.
		 * @return The module or null if it does not exist.
		 */
		public Module getModule(Feature[] pos, Feature[] neg);

		/**
		 * Checks if the given module already exists and throws an exception if it does.
		 * Otherwise the new module is added to the repository.
		 *
		 * @param pos Positive features of the module.
		 * @param neg Negative features of the module.
		 * @return The module instance that was added to the repository.
		 */
		public Module addModule(Feature[] pos, Feature[] neg);


//		/**
//		 * Checks if a module revision with given positive feature revisions and negative features is contained in the repository.
//		 *
//		 * @param pos
//		 * @param neg
//		 * @return
//		 */
//		public boolean containsModuleRevision(FeatureRevision[] pos, Feature[] neg);
//
//		/**
//		 * Retrieves the module revision instance with given positive feature revisions and negative features from the repository.
//		 *
//		 * @param pos
//		 * @param neg
//		 * @return The module revision or null if it does not exist.
//		 */
//		public ModuleRevision getModuleRevision(FeatureRevision[] pos, Feature[] neg);
//
//		/**
//		 * Checks if the given module revision already exists and throws an exception if it does.
//		 * If such a module revision does not already exist it is created.
//		 * If the corresponding module does not already exist it is created.
//		 *
//		 * @param pos
//		 * @param neg
//		 * @return The module instance that was added to the repository.
//		 */
//		public ModuleRevision addModuleRevision(FeatureRevision[] pos, Feature[] neg);


		/**
		 * Returns a collection of all features in this repository with the given name.
		 * Returns the collection of features with the given name. A feature is uniquely identified by its id. A name should be, but does not need to be, unique.
		 *
		 * @param name The name of the feature(s)
		 * @return The collection of features with the given name.
		 */
		public default Collection<Feature> getFeaturesByName(String name) {
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
		public default void addNegativeFeatureModules(Feature feature) {
			checkNotNull(feature);

			// add new modules to the repository that contain the new feature negatively. copies every existing module and adds the new feature negatively.
			for (int currentOrder = this.getMaxOrder() - 1; currentOrder >= 0; currentOrder--) {
				Collection<? extends Module> modules = this.getModules(currentOrder);
				for (Module module : modules) {
					// only add modules that do not exceed the maximum order of modules in the repository
					if (module.getOrder() < this.getMaxOrder()) {
						// create array of negative features. to be reused also by every revision module.
						Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
						negFeatures[negFeatures.length - 1] = feature;
						// create copy of module with new feature negative
						Module newModule = this.addModule(module.getPos(), negFeatures);
						newModule.setCount(module.getCount());

						// do the same for the revision modules
						for (ModuleRevision moduleRevision : module.getRevisions()) {
							// create copy of module revision with new feature negative
							ModuleRevision newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
							newModuleRevision.setCount(moduleRevision.getCount());
							// update existing associations that have matching old module with the new module
							for (Association.Op association : this.getAssociations()) {
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
		}

		/**
		 * Adds all features and feature revisions in the given configuration that are not already contained in the repository to the repository.
		 * In the process, all associations in the repository are updated with new modules containing the new features negatively.
		 * Returns a collection of all feature revision instances in the repository that are contained in the repository, those that already existed and those that were added.
		 *
		 * @param configuration The configuration whose features are added to the repository.
		 * @return Collection of all feature revision instances of the repository that are contained in the configuration.
		 */
		public default Collection<FeatureRevision> addConfigurationFeatures(Configuration configuration) {
			checkNotNull(configuration);

			// add new features and feature revisions from configuration to this repository
			Collection<FeatureRevision> repoFeatureRevisions = new ArrayList<>();
			for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
				Feature feature = featureRevision.getFeature();
				// get/add feature from/to repository
				Feature repoFeature = this.getFeature(feature.getId());
				if (repoFeature == null) {
					repoFeature = this.addFeature(feature.getId(), feature.getName());
					repoFeature.setDescription(feature.getDescription());

					this.addNegativeFeatureModules(repoFeature);
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
		 * @return Collection of all module revision instances of the repository that are contained in the configuration.
		 */
		public default Collection<ModuleRevision> addConfigurationModules(Configuration configuration) {
			checkNotNull(configuration);

			// collect positive feature revisions
			Collection<FeatureRevision> pos = new ArrayList<>();
			for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
				// get feature from repository
				Feature repoFeature = this.getFeature(featureRevision.getFeature().getId());
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
			for (Feature repoFeature : this.getFeatures()) {
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
					if (moduleRevision.getOrder() < this.getMaxOrder()) {
						FeatureRevision[] posFeatureRevisions = Arrays.copyOf(moduleRevision.getPos(), moduleRevision.getPos().length + 1);
						posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;
						Feature[] posFeatures = Arrays.stream(posFeatureRevisions).map(FeatureRevision::getFeature).toArray(Feature[]::new);

						// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
						Module newModule = this.getModule(posFeatures, moduleRevision.getNeg());
						if (newModule == null) {
							newModule = this.addModule(posFeatures, moduleRevision.getNeg());
						}
						newModule.incCount();
						ModuleRevision newModuleRevision = newModule.getRevision(posFeatureRevisions, moduleRevision.getNeg());
						if (newModuleRevision == null) {
							newModuleRevision = newModule.addRevision(posFeatureRevisions, moduleRevision.getNeg());
						}
						newModuleRevision.incCount();

						if (newModuleRevision.getOrder() >= this.getMaxOrder()) {
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
					if (moduleRevision.getOrder() < this.getMaxOrder() && moduleRevision.getPos().length > 0) {
						Feature[] negFeatures = Arrays.copyOf(moduleRevision.getNeg(), moduleRevision.getNeg().length + 1);
						negFeatures[negFeatures.length - 1] = feature;
						Feature[] posFeatures = Arrays.stream(moduleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);

						// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
						Module newModule = this.getModule(posFeatures, negFeatures);
						if (newModule == null) {
							newModule = this.addModule(posFeatures, negFeatures);
						}
						newModule.incCount();
						ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), negFeatures);
						if (newModuleRevision == null) {
							newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
						}
						newModuleRevision.incCount();

						if (newModuleRevision.getOrder() >= this.getMaxOrder()) {
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


		/**
		 * Extracts new associations and refines existing associations in this repository based on the given configuration and artifact tree.
		 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
		 *
		 * @param configuration The configuration describing the given artifact tree.
		 * @param nodes         The root node of the artifact tree representing the implementation of the given configuration.
		 * @return The commit object.
		 */
		public default Commit extract(Configuration configuration, Set<Node.Op> nodes) {
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
			Collection<FeatureRevision> repoFeatureRevisions = this.addConfigurationFeatures(configuration);

			// create configuration with repo feature revisions
			Configuration repoConfiguration = this.getEntityFactory().createConfiguration(repoFeatureRevisions.toArray(new FeatureRevision[0]));

			// set visibility of associations in repository
			for (Association.Op association : this.getAssociations()) {
				association.setVisible(association.computeCertainCondition().holds(repoConfiguration));
				//association.setVisible(true);
			}

			// add configuration modules and module revisions
			Collection<ModuleRevision> moduleRevisions = this.addConfigurationModules(repoConfiguration);

			// create and initialize new association
			Association.Op association = this.getEntityFactory().createAssociation(nodes);
			association.setId(UUID.randomUUID().toString());
			association.getCounter().setCount(1);
			for (ModuleRevision moduleRevision : moduleRevisions) {
				association.addObservation(moduleRevision);
			}

			// do actual extraction
			this.extract(association);

			// create commit object
			Commit commit = this.getEntityFactory().createCommit();
			commit.setConfiguration(repoConfiguration);

			return commit;
		}

		/**
		 * When associations are committed directly then the corresponding configuration must be added manually first!
		 *
		 * @param inputAs The collection of associations to be committed.
		 */
		public default void extract(Collection<? extends Association.Op> inputAs) {
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
		public default void extract(Association.Op association) {
			checkNotNull(association);

			Trees.checkConsistency(association.getRootNode());

			Collection<? extends Association.Op> originalAssociations = this.getAssociations();

			Collection<Association.Op> toAdd = new ArrayList<>();
			Collection<Association.Op> toRemove = new ArrayList<>();

			// slice new association with every original association
			for (Association.Op origA : originalAssociations) {
				// ASSOCIATION
				// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
				Association.Op intA = this.getEntityFactory().createAssociation();
				intA.setId(UUID.randomUUID().toString());

				// ARTIFACT TREE
				//intA.setRootNode(origA.getRootNode().slice(association.getRootNode()));
				intA.setRootNode((RootNode.Op) Trees.slice(origA.getRootNode(), association.getRootNode()));

				// INTERSECTION
				if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
					toAdd.add(intA);

					Trees.checkConsistency(intA.getRootNode());

					//intA.add(origA);
					//intA.add(association);
					intA.getCounter().add(origA.getCounter());
					intA.getCounter().add(association.getCounter());
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
				this.removeAssociation(origA);
			}

			// add associations to repository
			for (Association.Op newA : toAdd) {
				this.addAssociation(newA);
			}
		}


		/**
		 * Composes an artifact tree from the associations stored in this repository that implements the given configuration.
		 *
		 * @param configuration The configuration for which the implementing artifact tree shall be retrieved.
		 * @return The checkout object.
		 */
		public default Checkout compose(Configuration configuration) {
			return this.compose(configuration, true);
		}

		public default Checkout compose(Configuration configuration, boolean lazy) {
			checkNotNull(configuration);

			Set<Association.Op> selectedAssociations = new HashSet<>();
			for (Association.Op association : this.getAssociations()) {
				if (association.computeCondition().holds(configuration)) {
					selectedAssociations.add(association);
				}
			}

			Checkout checkout = this.compose(selectedAssociations, lazy);
			checkout.setConfiguration(configuration);

			// TODO: compute set of desired modules from configuration!
			//Set<ModuleRevision> desiredModules = configuration.computeModules(this.repository.getMaxOrder());
			Set<ModuleRevision> desiredModules = new HashSet<>();
			Set<ModuleRevision> missingModules = new HashSet<>();
			Set<ModuleRevision> surplusModules = new HashSet<>();

			// compute missing
			for (ModuleRevision desiredModuleRevision : desiredModules) {
				Feature[] posFeatures = Arrays.stream(desiredModuleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);
				Module desiredModule = this.getModule(posFeatures, desiredModuleRevision.getNeg());
				if (desiredModule == null || desiredModule.getRevision(desiredModuleRevision.getPos(), desiredModuleRevision.getNeg()) == null) {
					missingModules.add(desiredModuleRevision);
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

		public default Checkout compose(Collection<? extends Association.Op> selectedAssociations, boolean lazy) {
			Node compRootNode;
			Collection<Artifact<?>> orderWarnings;
			if (lazy) {
				LazyCompositionRootNode lazyCompRootNode = new LazyCompositionRootNode();

				for (Association.Op association : selectedAssociations) {
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
		 * This way a reader could keep reading a file after it was changed, map it to the repository, and have the trace information again.
		 * The nodes contain the updated line/col information from the reader, and the marking can still be done on the artifacts in the repository.
		 * This also enables highlighting of selected associations in changed files.
		 *
		 * @param rootNode The root node of the artifact tree to be mapped.
		 */
		public default void map(RootNode.Op rootNode) {
			Collection<? extends Association.Op> associations = this.getAssociations();
			for (Association.Op association : associations) {
				Trees.map(association.getRootNode(), rootNode);
			}
		}


		/**
		 * Creates a subset repository of this repository using the given entity factory. This repository is not changed.
		 * Creates a subset repository of this repository by (optionally) deselecting (i.e. explicity setting to <i>false</i>) some feature versions and (optionally) reducing the maximum order of modules.
		 * The subset repository is created with the given entity factory.
		 *
		 * @param deselected    The deselected feature revisions (i.e. feature versions that are set to false).
		 * @param maxOrder      The maximum order of modules to be copied over into the subset repository.
		 * @param entityFactory The entity factory used for creating the subset repository.
		 * @return The subset repository.
		 */
		public default Repository.Op subset_old(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
			checkNotNull(deselected);
			checkArgument(maxOrder <= this.getMaxOrder());
			checkNotNull(entityFactory);

			// create empty repository using the given entity factory
			Repository.Op newRepository = entityFactory.createRepository();
			newRepository.setMaxOrder(maxOrder);

			// add features and revisions in this repository to new repository, excluding deselected
			Map<Feature, Feature> featureReplacementMap = new HashMap<>();
			Map<FeatureRevision, FeatureRevision> featureRevisionReplacementMap = new HashMap<>();
			for (Feature feature : this.getFeatures()) {
				// feature
				Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName());
				newFeature.setDescription(feature.getDescription());
				featureReplacementMap.put(feature, newFeature);
				// add revisions
				for (FeatureRevision featureRevision : feature.getRevisions()) {
					// but only those that are not deselected
					if (!deselected.contains(featureRevision)) {
						FeatureRevision newFeatureRevision = newFeature.addRevision(featureRevision.getId());
						newFeatureRevision.setDescription(featureRevision.getDescription());
						featureRevisionReplacementMap.put(featureRevision, newFeatureRevision);
					}
				}
			}

			// add modules and module revisions to new repository
			// TODO

			// add associations in this repository to new repository (excluding those whose presence condition is always false and merging those whose presence conditions are equal)
			Collection<Association.Op> newAssociations = new ArrayList<>();
			for (Association.Op association : this.getAssociations()) {
				// TODO: use "repository.createAssociation" here? and let association have a backpointer to repository?
				Association.Op newAssociation = entityFactory.createAssociation();
				newAssociation.setId(association.getId());
				newAssociations.add(newAssociation);

				// copy counters (excluding deselected)
				for (ModuleCounter moduleCounter : association.getCounter().getChildren()) {
					Module module = moduleCounter.getObject();
					for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
						boolean excluded = false;
						ModuleRevision moduleRevision = moduleRevisionCounter.getObject();
						for (FeatureRevision featureRevision : moduleRevision.getPos()) {
							if (deselected.contains(featureRevision)) {
								excluded = true;
								break;
							}
						}
						if (!excluded) {
							// retrieve module revision instance from repository
							// TODO: make "addObservation" take care of using repository instance?! (i.e. the following two lines should happen by the third?!)
							Module newModule = newRepository.getModule(module.getPos(), module.getNeg());
							ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), moduleRevision.getNeg());
							newAssociation.addObservation(newModuleRevision, moduleRevisionCounter.getCount());

							// TODO: it is not as easy as this! we somehow need to "merge" e.g. "A-10", "A,!B-9" !!!
						}
					}
				}

				// copy artifact tree
				RootNode.Op copiedRootNode = entityFactory.createRootNode();
				newAssociation.setRootNode(copiedRootNode); // TODO: have association implementation take care of creating root node in constructor.
				// clone tree
				for (Node.Op parentChildNode : association.getRootNode().getChildren()) {
					Node.Op copiedChildNode = EccoUtil.deepCopyTree(parentChildNode, entityFactory);
					copiedRootNode.addChild(copiedChildNode);
					copiedChildNode.setParent(copiedRootNode);
				}
			}

			// check consistency of copied trees
			for (Association.Op newAssociation : newAssociations) {
				Trees.checkConsistency(newAssociation.getRootNode());
			}

			// remove cloned associations with empty conditions
			newAssociations.removeIf(newAssociation -> newAssociation.computeCondition().getModules().isEmpty());

			// compute dependency graph for selected associations and check if there are any unresolved dependencies.
			DependencyGraph dg = new DependencyGraph(newAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
			if (!dg.getUnresolvedDependencies().isEmpty()) {
				throw new EccoException("Unresolved dependencies in selection.");
			}

			// merge cloned associations that have equal counters/conditions? -> NO

			// trim sequence graphs to only contain artifacts from the selected associations
			for (Association.Op newAssociation : newAssociations) {
				newAssociation.getRootNode().traverse((Node.Op node) -> {
					if (node.getArtifact() != null && node.getArtifact().isOrdered() && node.getArtifact().isSequenced() && node.getArtifact().getSequenceGraph() != null) {
						if (node.isUnique() && node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
							// get all symbols from sequence graph
							Collection<? extends Artifact.Op<?>> symbols = node.getArtifact().getSequenceGraph().collectNodes().stream().map(PartialOrderGraph.Node.Op::getArtifact).collect(Collectors.toList());

							// remove symbols that are not contained in the given associations
							symbols.removeIf(symbol -> !newAssociations.contains(symbol.getContainingNode().getContainingAssociation()));

							// trim sequence graph
							node.getArtifact().getSequenceGraph().trim(symbols);
						}
					}
				});
			}

			// add new associations to new repository
			for (Association.Op newAssociation : newAssociations) {
				newRepository.addAssociation(newAssociation);
			}

			return newRepository;
		}

		public default Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
			checkNotNull(deselected);
			checkArgument(maxOrder <= this.getMaxOrder());
			checkNotNull(entityFactory);

			// create empty repository using the given entity factory
			Repository.Op newRepository = entityFactory.createRepository();
			newRepository.setMaxOrder(maxOrder);

			// add features (that have at least one revision) and feature revisions to subset repository, excluding deselected
			for (Feature feature : this.getFeatures()) {
				if (!feature.getRevisions().isEmpty() && feature.getRevisions().stream().anyMatch(featureRevision -> !deselected.contains(featureRevision))) {
					// feature
					Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName());
					newFeature.setDescription(feature.getDescription());
					// revisions
					for (FeatureRevision featureRevision : feature.getRevisions()) {
						// but only those that are not deselected
						if (!deselected.contains(featureRevision)) {
							FeatureRevision newFeatureRevision = newFeature.addRevision(featureRevision.getId());
							newFeatureRevision.setDescription(featureRevision.getDescription());
						}
					}
				}
			}

			// for every association in this repository: trim condition, use it to check if matching association already exists, if not create it, add observations based on trimmed condition, copy artifact tree and trim order graphs. (basically current merge implementation)
			Map<Set<ModuleRevision>, Association.Op> andConditionAssociationMap = new HashMap<>();
			Map<Set<ModuleRevision>, Association.Op> orConditionAssociationMap = new HashMap<>();
			for (Association.Op association : this.getAssociations()) {
				Condition condition = association.computeCondition();

				// compute set of module revisions that need to be added to the new repository and associations
				Set<ModuleRevision> newModuleRevisions = new HashSet<>();
				for (Map.Entry<Module, Collection<ModuleRevision>> entry : condition.getModules().entrySet()) {
					Module module = entry.getKey();
					// exclude modules that are above max order or that contain positive features that are not in subset repository
					if (module.getOrder() <= newRepository.getMaxOrder() && Arrays.stream(module.getPos()).noneMatch(feature -> newRepository.getFeature(feature.getId()) == null)) {

						// exclude negative features that are not in subset repository
						Feature[] newNegFeatures = Arrays.stream(module.getNeg()).map(feature -> newRepository.getFeature(feature.getId())).filter(Objects::nonNull).toArray(Feature[]::new);

						for (ModuleRevision moduleRevision : entry.getValue()) {
							// exclude module revisions that contain positive feature revisions that are not in subset repository
							if (Arrays.stream(moduleRevision.getPos()).noneMatch(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId()) == null)) {

								// get/add module from/to subset repository and increase its counter
								Module newModule = newRepository.getModule(module.getPos(), newNegFeatures);
								if (newModule == null) {
									newModule = newRepository.addModule(Arrays.stream(module.getPos()).map(feature -> newRepository.getFeature(feature.getId())).toArray(Feature[]::new), newNegFeatures);
									newModule.incCount();
								}

								ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), newModule.getNeg());
								if (newModuleRevision == null) {
									newModuleRevision = newModule.addRevision(Arrays.stream(moduleRevision.getPos()).map(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), newModule.getNeg());
									newModuleRevision.incCount();
								}

								// add module revision as observations to new association
								newModuleRevisions.add(newModuleRevision);
							}
						}
					}
				}

				// check if association has at least one module, if not exclude it
				if (!newModuleRevisions.isEmpty()) {

					// check if a new association with equal condition (ignoring negative features without revisions) already exists
					Association.Op newAssociation = null;
					boolean newAssoc = false;
					if (condition.getType() == Condition.TYPE.AND) {
						// for an AND condition set association, module and combined count to 1
						newAssociation = andConditionAssociationMap.get(newModuleRevisions);
						// if not create a new association
						if (newAssociation == null) {
							newAssoc = true;
							newAssociation = this.getEntityFactory().createAssociation();
							newAssociation.setId(UUID.randomUUID().toString());
							andConditionAssociationMap.put(newModuleRevisions, newAssociation);
							newAssociation.getCounter().setCount(1);
						}
					} else if (condition.getType() == Condition.TYPE.OR) {
						// for an OR condition set association=2, module=1 and combined count to 1
						newAssociation = orConditionAssociationMap.get(newModuleRevisions);
						// if not create a new association
						if (newAssociation == null) {
							newAssoc = true;
							newAssociation = this.getEntityFactory().createAssociation();
							newAssociation.setId(UUID.randomUUID().toString());
							orConditionAssociationMap.put(newModuleRevisions, newAssociation);
							newAssociation.getCounter().setCount(2);
						}
					}
					if (newAssoc) {
						newAssociation.setRootNode(entityFactory.createRootNode());
						// add new association to subset repository
						newRepository.addAssociation(newAssociation);
					}

					// add observations to new association
					for (ModuleRevision newModuleRevision : newModuleRevisions) {
						// add module revisions as observations to new association
						ModuleCounter newModuleCounter = newAssociation.getCounter().getChild(newModuleRevision.getModule());
						// check if observation is already part of this association
						if (!(newModuleCounter != null && newModuleCounter.getChild(newModuleRevision) != null)) {
							newAssociation.addObservation(newModuleRevision, 1);
						}
					}

					// copy artifact tree
					RootNode.Op copiedRootNode = entityFactory.createRootNode();
					// clone tree
					for (Node.Op childNode : association.getRootNode().getChildren()) {
						Node.Op copiedChildNode = EccoUtil.deepCopyTree(childNode, entityFactory);
						copiedRootNode.addChild(copiedChildNode);
						copiedChildNode.setParent(copiedRootNode);
					}

					// merge copied artifact tree into artifact tree of new association
					newAssociation.getRootNode().merge(copiedRootNode);
				}
			}

			Collection<? extends Association.Op> newAssociations = newRepository.getAssociations();

			// trim sequence graphs to only contain artifacts from the selected associations
			for (Association.Op newAssociation : newAssociations) {
				newAssociation.getRootNode().traverse((Node.Op node) -> {
					if (node.getArtifact() != null && node.getArtifact().isOrdered() && node.getArtifact().isSequenced() && node.getArtifact().getSequenceGraph() != null) {
						if (node.isUnique() && node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
							// get all symbols from sequence graph
							Collection<? extends Artifact.Op<?>> symbols = node.getArtifact().getSequenceGraph().collectNodes().stream().map(PartialOrderGraph.Node.Op::getArtifact).collect(Collectors.toList());

							// remove symbols that are not contained in the given associations
							symbols.removeIf(symbol -> symbol != null && !newAssociations.contains(symbol.getContainingNode().getContainingAssociation()));

							// trim sequence graph
							node.getArtifact().getSequenceGraph().trim(symbols);
						}
					}
				});
			}

			// check consistency of copied trees
			for (Association.Op newAssociation : newAssociations) {
				Trees.checkConsistency(newAssociation.getRootNode());
			}

			// compute dependency graph for selected associations and check if there are any unresolved dependencies.
			DependencyGraph dg = new DependencyGraph(newAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
			if (!dg.getUnresolvedDependencies().isEmpty()) {
				throw new EccoException("Unresolved dependencies in selection.");
			}


			return newRepository;
		}

		/**
		 * Creates a copy of this repository using the same entity factory and maximum order of modules. This repository is not changed.
		 *
		 * @param entityFactory The entity factory used for creating the copy of this repository.
		 * @return The copied repository.
		 */
		public default Repository.Op copy(EntityFactory entityFactory) {
			return this.subset(new ArrayList<>(), this.getMaxOrder(), entityFactory);
		}

		public default void merge(Repository.Op otherRepository) {
			checkNotNull(otherRepository);

			// extract every association. treat it as if it was an input product. only that there is no configuration.

			// add features (that have at least one revision) and feature revisions in other repository to this repository
			for (Feature otherFeature : otherRepository.getFeatures()) {
				if (!otherFeature.getRevisions().isEmpty()) {
					Feature repoFeature = this.getFeature(otherFeature.getId());
					if (repoFeature == null) {
						repoFeature = this.addFeature(otherFeature.getId(), otherFeature.getName());
						repoFeature.setDescription(otherFeature.getDescription());

						this.addNegativeFeatureModules(repoFeature);
					}
					for (FeatureRevision otherFeatureRevision : otherFeature.getRevisions()) {
						FeatureRevision repoFeatureRevision = repoFeature.getRevision(otherFeatureRevision.getId());
						if (repoFeatureRevision == null) {
							repoFeatureRevision = repoFeature.addRevision(otherFeatureRevision.getId());
							repoFeatureRevision.setDescription(otherFeatureRevision.getDescription());
						}
					}
				}
			}

			// add features in this repository that are not in other repository negatively to other repository
			Set<Feature> negFeaturesWithoutRevisions = new HashSet<>();
			for (Feature feature : this.getFeatures()) {
				if (otherRepository.getFeature(feature.getId()) == null) {
					Feature otherFeature = otherRepository.addFeature(feature.getId(), feature.getName());
					otherFeature.setDescription(feature.getDescription());
					otherRepository.addNegativeFeatureModules(otherFeature);
					negFeaturesWithoutRevisions.add(otherFeature);
				}
			}

			// add modules and module revisions (i.e. add new ones and increase the counters of existing ones)
			for (int order = 0; order <= this.getMaxOrder(); order++) {
				for (final Module otherModule : otherRepository.getModules(order)) {
					// check if module contains any negative features without any revisions
					if (Arrays.stream(otherModule.getNeg()).noneMatch(feature -> feature.getRevisions().isEmpty() && !negFeaturesWithoutRevisions.contains(feature))) {
						// add module to this repository and increase its counter
						Module repoModule = this.getModule(otherModule.getPos(), otherModule.getNeg());
						if (repoModule == null) {
							repoModule = this.addModule(Arrays.stream(otherModule.getPos()).map(feature -> this.getFeature(feature.getId())).toArray(Feature[]::new), Arrays.stream(otherModule.getNeg()).map(feature -> this.getFeature(feature.getId())).toArray(Feature[]::new));
						}
						repoModule.incCount(otherModule.getCount());
						for (final ModuleRevision otherModuleRevision : otherModule.getRevisions()) {
							ModuleRevision repoModuleRevision = repoModule.getRevision(otherModuleRevision.getPos(), otherModuleRevision.getNeg());
							if (repoModuleRevision == null) {
								repoModuleRevision = repoModule.addRevision(Arrays.stream(otherModuleRevision.getPos()).map(featureRevision -> this.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), repoModule.getNeg());
							}
							repoModuleRevision.incCount(otherModuleRevision.getCount());
						}
					}
				}
			}

			// for every association in other repository
			for (Association.Op otherAssociation : otherRepository.getAssociations()) {
				// prepare new associations for commit
				Association.Op association = this.getEntityFactory().createAssociation();
				association.setId(UUID.randomUUID().toString());

				// copy artifact tree
				RootNode.Op copiedRootNode = this.getEntityFactory().createRootNode();
				association.setRootNode(copiedRootNode); // TODO: have association implementation take care of creating root node in constructor.
				// clone tree
				for (Node.Op otherChildNode : otherAssociation.getRootNode().getChildren()) {
					Node.Op copiedChildNode = EccoUtil.deepCopyTree(otherChildNode, this.getEntityFactory());
					copiedRootNode.addChild(copiedChildNode);
					copiedChildNode.setParent(copiedRootNode);
				}


				// set association counter
				association.getCounter().setCount(otherAssociation.getCounter().getCount());

				for (ModuleCounter otherModuleCounter : otherAssociation.getCounter().getChildren()) {
					// set module counter
					Module otherModule = otherModuleCounter.getObject();
					Module module = this.getModule(otherModule.getPos(), otherModule.getNeg());

					if (module == null)
						throw new EccoException("Association to be merged into this repository contains module " + module + " which is not part of this repository.");

					ModuleCounter moduleCounter = association.getCounter().addChild(module);
					moduleCounter.setCount(otherModuleCounter.getCount());

					for (ModuleRevisionCounter otherModuleRevisionCounter : otherModuleCounter.getChildren()) {
						// set module revision counter
						ModuleRevision otherModuleRevision = otherModuleRevisionCounter.getObject();
						ModuleRevision moduleRevision = module.getRevision(otherModuleRevision.getPos(), otherModuleRevision.getNeg());
						ModuleRevisionCounter moduleRevisionCounter = moduleCounter.addChild(moduleRevision);
						moduleRevisionCounter.setCount(otherModuleRevisionCounter.getCount());
					}
				}

				// commit association to this repository
				this.extract(association);
			}
		}


		/**
		 * Merges other repository into this repository. The other repository is destroyed in the process.
		 * Merges another repository into this repository. The two repositories must have been created from the same entity factory (i.e. must use the same data backend).
		 *
		 * @param other The other repository to be merged into this repository.
		 */
		public default void merge_old(Repository.Op other) {
			checkNotNull(other);
			checkArgument(other.getClass().equals(this.getClass())); // TODO: this might not be necessary. it might be enough to have the artifact trees of the correct type.

			// TODO: MERGE: extract every association. treat it as if it was an input product. only that there is no configuration. instead have something like "addConditionModules"?

			// add features (that have at least one revision) and feature revisions in other repository to this repository
			for (Feature otherFeature : other.getFeatures()) {
				if (!otherFeature.getRevisions().isEmpty()) {
					Feature repoFeature = this.getFeature(otherFeature.getId());
					if (repoFeature == null) {
						repoFeature = this.addFeature(otherFeature.getId(), otherFeature.getName());
						repoFeature.setDescription(otherFeature.getDescription());

						this.addNegativeFeatureModules(repoFeature);
					}
					for (FeatureRevision otherFeatureRevision : otherFeature.getRevisions()) {
						FeatureRevision repoFeatureRevision = repoFeature.getRevision(otherFeatureRevision.getId());
						if (repoFeatureRevision == null) {
							repoFeatureRevision = repoFeature.addRevision(otherFeatureRevision.getId());
							repoFeatureRevision.setDescription(otherFeatureRevision.getDescription());
						}
					}
				}
			}

			// add modules and module revisions (i.e. add new ones and increase the counters of existing ones)
			for (int order = 0; order < this.getMaxOrder(); order++) {
				for (final Module otherModule : other.getModules(order)) {
					// check if module contains any negative features without any revisions
					if (Arrays.stream(otherModule.getNeg()).noneMatch(feature -> feature.getRevisions().isEmpty())) {
						// add module to this repository and increase its counter
						Module repoModule = this.getModule(otherModule.getPos(), otherModule.getNeg());
						if (repoModule == null) {
							repoModule = this.addModule(Arrays.stream(otherModule.getPos()).map(feature -> this.getFeature(feature.getId())).toArray(Feature[]::new), Arrays.stream(otherModule.getNeg()).map(feature -> this.getFeature(feature.getId())).toArray(Feature[]::new));
						}
						repoModule.incCount();
						for (final ModuleRevision otherModuleRevision : otherModule.getRevisions()) {
							ModuleRevision repoModuleRevision = repoModule.getRevision(otherModuleRevision.getPos(), otherModuleRevision.getNeg());
							if (repoModuleRevision == null) {
								repoModuleRevision = repoModule.addRevision(Arrays.stream(otherModuleRevision.getPos()).map(featureRevision -> this.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), repoModule.getNeg());
							}
							repoModuleRevision.incCount();
						}
					}
				}
			}

			// add features in this repository that are not in other repository negatively to other repository
			for (Feature feature : this.getFeatures()) {
				if (other.getFeature(feature.getId()) == null) {
					Feature otherFeature = other.addFeature(feature.getId(), feature.getName());
					otherFeature.setDescription(feature.getDescription());
					other.addNegativeFeatureModules(otherFeature);
				}
			}

			// prepare new associations for commit
			Map<Set<ModuleRevision>, Association.Op> andConditionAssociationMap = new HashMap<>();
			Map<Set<ModuleRevision>, Association.Op> orConditionAssociationMap = new HashMap<>();
			Collection<Association.Op> newAssociations = new ArrayList<>();
			// for every association in other repository
			for (Association.Op otherAssociation : other.getAssociations()) {
				Condition otherCondition = otherAssociation.computeCondition();

				// compute set of module revisions (instances from this repository) that need to be added to the new association
				Set<ModuleRevision> newModuleRevisions = new HashSet<>();
				for (Map.Entry<Module, Collection<ModuleRevision>> entry : otherCondition.getModules().entrySet()) {
					Module oldModule = entry.getKey();
					// exclude modules that contain positive features that are not in this repository
					if (Arrays.stream(oldModule.getPos()).noneMatch(feature -> this.getFeature(feature.getId()) == null)) {
						for (ModuleRevision oldModuleRevision : entry.getValue()) {
							// exclude modules revisions that contain positive feature revisions that are not in this repository
							if (Arrays.stream(oldModuleRevision.getPos()).noneMatch(featureRevision -> this.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId()) == null)) {
								// exclude negative features that have no revisions
								Feature[] negFeatuers = Arrays.stream(oldModuleRevision.getNeg()).filter(feature -> !feature.getRevisions().isEmpty()).toArray(Feature[]::new);
								ModuleRevision newModuleRevision = this.getModule(oldModule.getPos(), negFeatuers).getRevision(oldModuleRevision.getPos(), negFeatuers);
								// add module revision as observations to new association
								newModuleRevisions.add(newModuleRevision);
							}
						}
					} else {
						throw new EccoException("Error during merge: encountered module with feature that does not exist in repository.");
					}
				}


				// check if a new association with equal condition (ignoring negative features without revisions) already exists
				Association.Op newAssociation = null;
				if (otherCondition.getType() == Condition.TYPE.OR) {
					newAssociation = orConditionAssociationMap.get(newModuleRevisions);
					// if not create a new association
					if (newAssociation == null) {
						newAssociation = this.getEntityFactory().createAssociation();
						orConditionAssociationMap.put(newModuleRevisions, newAssociation);
						newAssociation.getCounter().setCount(2);
						newAssociation.setRootNode(getEntityFactory().createRootNode());
						newAssociations.add(newAssociation);
					}
				} else if (otherCondition.getType() == Condition.TYPE.AND) {
					newAssociation = andConditionAssociationMap.get(newModuleRevisions);
					// if not create a new association
					if (newAssociation == null) {
						newAssociation = this.getEntityFactory().createAssociation();
						andConditionAssociationMap.put(newModuleRevisions, newAssociation);
						newAssociation.getCounter().setCount(1);
						newAssociation.setRootNode(getEntityFactory().createRootNode());
						newAssociations.add(newAssociation);
					}
				}

				// merge artifact tree of other association into artifact tree of new association
				newAssociation.getRootNode().merge(otherAssociation.getRootNode());

				// set observations of new association
				for (ModuleRevision newModuleRevision : newModuleRevisions) {
					// add module revisions as observations to new association
					ModuleCounter newModuleCounter = newAssociation.getCounter().getChild(newModuleRevision.getModule());
					// check if observation is already part of this association
					if (!(newModuleCounter != null && newModuleCounter.getChild(newModuleRevision) != null)) {
						newAssociation.addObservation(newModuleRevision);
					}
				}
			}

			// commit associations to this repository
			this.extract(newAssociations);
		}

		/**
		 * Diffs the current working copy against the repository and returns a diff object containing all affected associations (and thus all affected features and artifacts).
		 *
		 * @return The diff object.
		 */
		public default Diff diff() {
			// TODO
			throw new UnsupportedOperationException("Not yet implemented.");
		}

	}

}
