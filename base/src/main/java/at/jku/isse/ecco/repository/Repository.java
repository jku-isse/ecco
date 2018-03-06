package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
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
import at.jku.isse.ecco.util.Trees;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Public repository interface. A repository contains {@link at.jku.isse.ecco.feature.Feature}s and {@link at.jku.isse.ecco.core.Association}s.
 */
public interface Repository {

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
		public Collection<? extends Module> getModules();


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
		 * @param pos
		 * @param neg
		 * @return The module or null if it does not exist.
		 */
		public Module getModule(Feature[] pos, Feature[] neg);

		/**
		 * Checks if the given module already exists and throws an exception if it does.
		 * Otherwise the new module is added to the repository.
		 *
		 * @param pos
		 * @param neg
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
			for (Module module : this.getModules()) {
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

		/**
		 * Adds all features and feature revisions in the given configuration that are not already contained in the repository to the repository.
		 * In the process, all associations in the repository are updated with new modules containing the new features negatively.
		 * Returns a collection of all feature revisions instances in the repository that are contained in the repository, those that already existed and those that were added.
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
		 * @return All module revision instances of the repository that are contained in the configuration.
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
			Collection<ModuleRevision> modulesRevisions = new ArrayList<>();

			// add empty module initially
			Module emptyModule = new EmptyModule();
			ModuleRevision emptyModuleRevision = emptyModule.getRevision(new FeatureRevision[0], new Feature[0]);
			modulesRevisions.add(emptyModuleRevision); // add empty module revision to power set

			// compute powerset
			for (final FeatureRevision featureRevision : pos) {
				final Collection<ModuleRevision> toAdd = new ArrayList<>();

				for (final ModuleRevision moduleRevision : modulesRevisions) {
					if (moduleRevision.getOrder() < this.getMaxOrder()) {
						FeatureRevision[] posFeatureRevisions = Arrays.copyOf(moduleRevision.getPos(), moduleRevision.getPos().length + 1);
						posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;
						Feature[] posFeatures = Arrays.stream(posFeatureRevisions).map(FeatureRevision::getFeature).toArray(Feature[]::new);

						// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
						Module newModule = this.getModule(posFeatures, moduleRevision.getNeg());
						if (newModule == null) {
							newModule = this.addModule(posFeatures, moduleRevision.getNeg());
						}
						ModuleRevision newModuleRevision = newModule.getRevision(posFeatureRevisions, moduleRevision.getNeg());
						if (newModuleRevision == null) {
							newModuleRevision = newModule.addRevision(posFeatureRevisions, moduleRevision.getNeg());
						}
						newModuleRevision.incCount();

						toAdd.add(newModuleRevision);
					}
				}

				modulesRevisions.addAll(toAdd);
			}

			// remove the empty module again
			modulesRevisions.remove(emptyModuleRevision);

			for (final Feature feature : neg) {
				final Collection<ModuleRevision> toAdd = new ArrayList<>();

				for (final ModuleRevision moduleRevision : modulesRevisions) {
					if (moduleRevision.getOrder() < this.getMaxOrder() && moduleRevision.getPos().length > 0) {
						Feature[] negFeatures = Arrays.copyOf(moduleRevision.getNeg(), moduleRevision.getNeg().length + 1);
						negFeatures[negFeatures.length - 1] = feature;
						Feature[] posFeatures = Arrays.stream(moduleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);

						// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
						Module newModule = this.getModule(posFeatures, negFeatures);
						if (newModule == null) {
							newModule = this.addModule(posFeatures, negFeatures);
						}
						ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), negFeatures);
						if (newModuleRevision == null) {
							newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
						}
						newModuleRevision.incCount();

						toAdd.add(newModuleRevision);
					}
				}

				modulesRevisions.addAll(toAdd);
			}

			return modulesRevisions;
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

			// add configuration features and revisions
			Collection<FeatureRevision> repoFeatureRevisions = this.addConfigurationFeatures(configuration);

			// create configuration with repo feature revisions
			Configuration repoConfiguration = this.getEntityFactory().createConfiguration(repoFeatureRevisions.toArray(new FeatureRevision[repoFeatureRevisions.size()]));

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

		public default Checkout compose(Collection<Association.Op> selectedAssociations, boolean lazy) {
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
		public default Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
			// TODO
			throw new UnsupportedOperationException("Not yet implemented.");
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

		/**
		 * Merges other repository into this repository. The other repository is destroyed in the process.
		 * Merges another repository into this repository. The two repositories must have been created from the same entity factory (i.e. must use the same data backend).
		 *
		 * @param other The other repository to be merged into this repository.
		 */
		public default void merge(Repository.Op other) {
			// TODO
			throw new UnsupportedOperationException("Not yet implemented.");
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
