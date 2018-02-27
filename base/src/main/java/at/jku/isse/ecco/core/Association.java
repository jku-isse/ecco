package at.jku.isse.ecco.core;

import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
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


	public ModuleCondition computeCondition();


	/**
	 * Private association operand interface.
	 */
	public interface Op extends Association {

		public default ModuleCondition computeCondition() {
			ModuleCondition moduleCondition = this.getEntityFactory().createModuleCondition();
			AssociationCounter associationCounter = this.getCounter();

			// for every module check if it traces uniquely
			for (ModuleCounter moduleCounter : associationCounter.getChildren()) {
				// condition for unique trace
				if (moduleCounter.getCount() == moduleCounter.getObject().getCount() && moduleCounter.getCount() == associationCounter.getCount()) {
					moduleCondition.addModule(moduleCounter.getObject());
					// now check to which revisions it traces
					for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
						if (moduleRevisionCounter.getCount() > 0) {
							moduleCondition.addModuleRevision(moduleRevisionCounter.getObject());
						}
					}
				}
			}

			// if the module condition is empty check if it traces disjunctively
			if (moduleCondition.getModules().isEmpty()) {
				for (ModuleCounter moduleCounter : associationCounter.getChildren()) {
					// condition for disjunctive trace
					if (moduleCounter.getCount() == moduleCounter.getObject().getCount()) {
						moduleCondition.addModule(moduleCounter.getObject());
						// now check to which revisions it traces
						for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
							if (moduleRevisionCounter.getCount() > 0) {
								moduleCondition.addModuleRevision(moduleRevisionCounter.getObject());
							}
						}
					}
				}
			}

			return moduleCondition;
		}


		/**
		 * Adds another association's observations to this association.
		 *
		 * @param other
		 */
		public default void add(Association.Op other) {
			AssociationCounter thisCounter = this.getCounter();
			AssociationCounter otherCounter = this.getCounter();
			thisCounter.incCount(otherCounter.getCount());
			// add every module in other association to this association
			for (ModuleCounter otherModuleCounter : otherCounter.<ModuleRevision>getChildren()) {
				ModuleCounter thisModuleCounter = thisCounter.getChild(otherModuleCounter.getObject());
				// if the counter for this module does not exist yet add it
				if (thisModuleCounter == null) {
					thisModuleCounter = thisCounter.addChild(otherModuleCounter.getObject());
				}
				thisModuleCounter.incCount(otherModuleCounter.getCount());
				// add every module revision in other module to this module
				for (ModuleRevisionCounter otherModuleRevisionCounter : otherModuleCounter.getChildren()) {
					ModuleRevisionCounter thisModuleRevisionCounter = thisModuleCounter.getChild(otherModuleRevisionCounter.getObject());
					if (thisModuleRevisionCounter == null) {
						thisModuleRevisionCounter = thisModuleCounter.addChild(otherModuleRevisionCounter.getObject());
					}
					thisModuleRevisionCounter.incCount(otherModuleRevisionCounter.getCount());
				}
			}
		}

		public default void addObservation(ModuleRevision moduleRevision, int count) {
			// get module
			Module module = moduleRevision.getModule();
			// get association counter
			AssociationCounter associationCounter = this.getCounter();
			// look for module
			ModuleCounter moduleCounter = associationCounter.getChild(module);
			// if module counter does not exist yet add it
			if (moduleCounter == null) {
				moduleCounter = associationCounter.addChild(module);
			}
			// increase module counter
			moduleCounter.incCount();
			// look for module revision
			ModuleRevisionCounter moduleRevisionCounter = moduleCounter.getChild(moduleRevision);
			// if module revision counter does not exist yet add it
			if (moduleRevisionCounter == null) {
				moduleRevisionCounter = moduleCounter.addChild(moduleRevision);
			}
			// increase module revision counter
			moduleRevisionCounter.incCount();
		}

		public default void addObservation(ModuleRevision moduleRevision) {
			this.addObservation(moduleRevision, 1);
		}


		public AssociationCounter getCounter();


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
