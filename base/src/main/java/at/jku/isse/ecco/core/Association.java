package at.jku.isse.ecco.core;

import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
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
	 * Returns the root node of the artifact tree or null if no artifacts are stored.
	 *
	 * @return The root of the artifact tree.
	 */
	public RootNode getRootNode();


	// TODO: make use of this! use it to check added modules that do not already exist in counter.
	public Repository getContainingRepository();


	public Condition computeCondition();


	public default String getAssociationString() {
		return this.getId().substring(0, Math.min(this.getId().length(), 7));
	}

	@Override
	public String toString();


	/**
	 * Private association operand interface.
	 */
	public interface Op extends Association {

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


		public Repository.Op getContainingRepository();


		public AssociationCounter getCounter();


		public Condition createCondition();


		public boolean isVisible();

		public void setVisible(boolean visible);


		public default Condition computeCondition() {
			Condition moduleCondition = this.computeLikelyCondition();

			// if the module condition is empty
			if (moduleCondition.getModules().isEmpty()) {
				moduleCondition = this.computeCertainCondition();
			}

			return moduleCondition;
		}

		public default Condition computeLikelyCondition() {
			AssociationCounter associationCounter = this.getCounter();
			Condition moduleCondition = this.createCondition();
			moduleCondition.setType(Condition.TYPE.AND);
			// for every module check if it traces uniquely
			for (ModuleCounter moduleCounter : associationCounter.getChildren()) {
				// a module M traces uniquely to artifacts in association A iff
				// 1) M was always present when A was present
				if (moduleCounter.getCount() == associationCounter.getCount()) {
					// 2) A was always present when M_r was present for all revisions r of M
					if (moduleCounter.getChildren().stream().noneMatch(moduleRevisionCounter -> moduleRevisionCounter.getCount() != moduleRevisionCounter.getObject().getCount())) {
						for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
							moduleCondition.addModuleRevision(moduleRevisionCounter.getObject());
						}
					}
				}
			}
			return moduleCondition;
		}

		public default Condition computeCertainCondition() {
			AssociationCounter associationCounter = this.getCounter();
			Condition moduleCondition = this.createCondition();
			moduleCondition.setType(Condition.TYPE.OR);
			// for every module check if it traces disjunctively
			for (ModuleCounter moduleCounter : associationCounter.getChildren()) {
				// 1) M was present at least once when A was present
				if (moduleCounter.getCount() > 0) { // it is in ALL
					for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
						// 2) A was always present when M_r was present
						if (moduleRevisionCounter.getCount() > 0 && moduleRevisionCounter.getCount() == moduleRevisionCounter.getObject().getCount()) { // it is in ALL AND it is not in NOT
							moduleCondition.addModuleRevision(moduleRevisionCounter.getObject());
						}
					}
				}
			}
			return moduleCondition;
		}


		/**
		 * Adds another association's observations to this association.
		 *
		 * @param other The other association whose observations are to be added to this association's observations.
		 */
		public default void add(Association.Op other) {
			this.getCounter().add(other.getCounter());
//			AssociationCounter thisCounter = this.getCounter();
//			AssociationCounter otherCounter = other.getCounter();
//			thisCounter.incCount(otherCounter.getCount());
//			// add every module in other association to this association
//			for (ModuleCounter otherModuleCounter : otherCounter.getChildren()) {
//				ModuleCounter thisModuleCounter = thisCounter.getChild(otherModuleCounter.getObject());
//				// if the counter for this module does not exist yet add it
//				if (thisModuleCounter == null) {
//					thisModuleCounter = thisCounter.addChild(otherModuleCounter.getObject());
//				}
//				thisModuleCounter.incCount(otherModuleCounter.getCount());
//				// add every module revision in other module to this module
//				for (ModuleRevisionCounter otherModuleRevisionCounter : otherModuleCounter.getChildren()) {
//					ModuleRevisionCounter thisModuleRevisionCounter = thisModuleCounter.getChild(otherModuleRevisionCounter.getObject());
//					if (thisModuleRevisionCounter == null) {
//						thisModuleRevisionCounter = thisModuleCounter.addChild(otherModuleRevisionCounter.getObject());
//					}
//					thisModuleRevisionCounter.incCount(otherModuleRevisionCounter.getCount());
//				}
//			}
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
				moduleCounter = associationCounter.addChild(module); // TODO: get module instance from containing repository here?
			}
			// increase module counter
			moduleCounter.incCount(count);
			// look for module revision
			ModuleRevisionCounter moduleRevisionCounter = moduleCounter.getChild(moduleRevision);
			// if module revision counter does not exist yet add it
			if (moduleRevisionCounter == null) {
				moduleRevisionCounter = moduleCounter.addChild(moduleRevision); // TODO: get module revision instance from containing repository here?
			}
			// increase module revision counter
			moduleRevisionCounter.incCount(count);
		}

		public default void addObservation(ModuleRevision moduleRevision) {
			this.addObservation(moduleRevision, 1);
		}

	}

}
