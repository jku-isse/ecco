package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.Set;

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
		 * Returns a (not backed) collection of modules in the repository.
		 *
		 * @return
		 */
		public Collection<? extends Module> getModules();


		// operations

		/**
		 * Extracts new associations and refines existing associations in this repository based on the given configuration and artifact tree.
		 *
		 * @param configuration The configuration describing the given artifact tree.
		 * @param nodes         The root node of the artifact tree representing the implementation of the given configuration.
		 * @return The commit object.
		 */
		public Commit extract(Configuration configuration, Set<Node.Op> nodes);

		/**
		 * Composes an artifact tree from the associations stored in this repository that implements the given configuration.
		 *
		 * @param configuration The configuration for which the implementing artifact tree shall be retrieved.
		 * @return The checkout object.
		 */
		public Checkout compose(Configuration configuration);

		/**
		 * Creates a subset repository of this repository by (optionally) deselecting (i.e. explicity setting to <i>false</i>) some feature versions and (optionally) reducing the maximum order of modules.
		 * The subset repository is created with the given entity factory.
		 *
		 * @param deselected    The collection of deselected feature versions.
		 * @param maxOrder      The maximum order of modules to be copied over into the subset repository.
		 * @param entityFactory The entity factory used for creating the subset repository.
		 * @return The subset repository.
		 */
		public Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory);

		/**
		 * Makes a copy of this repository.
		 *
		 * @param entityFactory The entity factory used for creating the copy of this repository.
		 * @return The copied repository.
		 */
		public Op copy(EntityFactory entityFactory);

		/**
		 * Merges another repository into this repository. The two repositories must have been created from the same entity factory (i.e. must use the same data backend)
		 *
		 * @param repository The repository to be merged into this repository.
		 */
		public void merge(Op repository);


		// features

		/**
		 * Retrieves the feature stored in this repository with the given id. If no such feature exists in this repository, null is returned.
		 *
		 * @param id The id of the feature.
		 * @return The feature with the given id, or null if there is no such feature.
		 */
		public Feature getFeature(String id);

		/**
		 * Returns the collection of features with the given name. A feature is uniquely identified by its id. A name should be, but does not need to be, unique.
		 *
		 * @param name The name of the feature(s)
		 * @return The collection of features with the given name.
		 */
		public Collection<Feature> getFeaturesByName(String name);


		// TODO: document these! make clear where a check is performed for "already existing" or "null" etc.

		public Feature addFeature(String id, String name, String description);


		// associations

		public void addAssociation(Association.Op association);

		public void removeAssociation(Association.Op association);


		public int getMaxOrder();

		public void setMaxOrder(int maxOrder);


		public EntityFactory getEntityFactory();


		/**
		 * Checks if a module with given positive and negative features is contained in the repository.
		 *
		 * @param pos
		 * @param neg
		 * @return
		 */
		public boolean containsModule(Feature[] pos, Feature[] neg);

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


		/**
		 * Checks if a module revision with given positive feature revisions and negative features is contained in the repository.
		 *
		 * @param pos
		 * @param neg
		 * @return
		 */
		public boolean containsModuleRevision(FeatureRevision[] pos, Feature[] neg);

		/**
		 * Retrieves the module revision instance with given positive feature revisions and negative features from the repository.
		 *
		 * @param pos
		 * @param neg
		 * @return The module revision or null if it does not exist.
		 */
		public ModuleRevision getModuleRevision(FeatureRevision[] pos, Feature[] neg);

		/**
		 * Checks if the given module revision already exists and throws an exception if it does.
		 * If such a module revision does not already exist it is created.
		 * If the corresponding module does not already exist it is created.
		 *
		 * @param pos
		 * @param neg
		 * @return The module instance that was added to the repository.
		 */
		public ModuleRevision addModuleRevision(FeatureRevision[] pos, Feature[] neg);

	}

}
