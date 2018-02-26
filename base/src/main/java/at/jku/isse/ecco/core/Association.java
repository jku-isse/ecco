package at.jku.isse.ecco.core;

import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.CounterNode;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleCondition;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.RootNode;

/**
 * Represents a trace between a presence condition and an artifact tree. An association can have a set of parents.
 */
public interface Association extends Persistable {

	/**
	 * Returns the id of the association or the empty string if it does not have an id yet.
	 *
	 * @return The id of the association.
	 */
	public String getId();

	/**
	 * Sets the id of the association.
	 *
	 * @param id The id of the association.
	 */
	public void setId(String id);

	/**
	 * Returns the name of the association or the empty string if it does not have a name yet.
	 *
	 * @return The name of the association.
	 */
	public String getName();

	/**
	 * Sets the name of the association.
	 *
	 * @param name The name of the association.
	 */
	public void setName(String name);

	/**
	 * Returns the root node of the artifact tree or null if no artifacts are stored.
	 *
	 * @return The root of the artifact tree.
	 */
	public RootNode getRootNode();


	/**
	 * Private association interface.
	 */
	public interface Op extends Association {

		public AssociationCounter getCounter();


		public CounterNode<Association, Module> getCounterNode();


		/**
		 * Adds another association's observations to this association.
		 *
		 * @param association
		 */
		public void add(Association.Op association);

		/**
		 * Adds an observation of the given revision module either with the artifacts present or not present.
		 *
		 * @param revisionModule
		 */
		public void addObservation(ModuleRevision revisionModule);

//		public Module[] getTracingFeatureModules();
//
//		public ModuleRevision[] getTracingRevisionModules();

		public ModuleCondition getModuleCondition();


		public EntityFactory getEntityFactory();


		/**
		 * Returns the root node operand.
		 *
		 * @return The root node operand.
		 */
		public RootNode.Op getRootNode();

		/**
		 * Sets the root node of the artifact tree.
		 *
		 * @param root The root of the artifact tree (may be null).
		 */
		public void setRootNode(RootNode.Op root);
	}

}
