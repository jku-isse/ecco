package at.jku.isse.ecco.core;

import at.jku.isse.ecco.counter.CounterNode;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import static com.google.common.base.Preconditions.checkNotNull;

public class AssociationOperator {

	private Association.Op association;
	private EntityFactory entityFactory;

	public AssociationOperator(Association.Op association) {
		checkNotNull(association);
		this.association = association;
		this.entityFactory = association.getEntityFactory();
	}


	/**
	 * Adds the observations of the other association to the observation sof this association.
	 *
	 * @param other
	 */
	public void add(Association.Op other) {
		//this.association.getCounter().add(other.getCounter());

		CounterNode<Association, Module> thisCounter = this.association.getCounterNode();
		CounterNode<Association, Module> otherCounter = this.association.getCounterNode();
		thisCounter.incCount(otherCounter.getCount());
		// add every module in other association to this association
		for (CounterNode<Module, ModuleRevision> otherModuleCounter : otherCounter.<ModuleRevision>getChildren()) {
			CounterNode<Module, ModuleRevision> thisModuleCounter = thisCounter.getChild(otherModuleCounter.getObject());
			// if the counter for this module does not exist yet add it
			if (thisModuleCounter == null) {
				thisModuleCounter = thisCounter.addChild(otherModuleCounter.getObject());
			}
			thisModuleCounter.incCount(otherModuleCounter.getCount());
			// add every module revision in other module to this module
			for (CounterNode<ModuleRevision, ?> otherModuleRevisionCounter : otherModuleCounter.getChildren()) {
				CounterNode<ModuleRevision, ?> thisModuleRevisionCounter = thisModuleCounter.getChild(otherModuleRevisionCounter.getObject());
				if (thisModuleRevisionCounter == null) {
					thisModuleRevisionCounter = thisModuleCounter.addChild(otherModuleRevisionCounter.getObject());
				}
				thisModuleRevisionCounter.incCount(otherModuleRevisionCounter.getCount());
			}
		}
	}


	public void addObservation(ModuleRevision moduleRevision) {
		// get module
		Module module = moduleRevision.getModule();
		// get association counter
		CounterNode<Association, Module> associationCounter = this.association.getCounterNode();
		// look for module
		CounterNode<Module, ModuleRevision> moduleCounter = associationCounter.getChild(module);
		// if module counter does not exist yet add it
		if (moduleCounter == null) {
			moduleCounter = associationCounter.addChild(module);
		}
		// increase module counter
		moduleCounter.incCount();
		// look for module revision
		CounterNode<ModuleRevision, Object> moduleRevisionCounter = moduleCounter.getChild(moduleRevision);
		// if module revision counter does not exist yet add it
		if (moduleRevisionCounter == null) {
			moduleRevisionCounter = moduleCounter.addChild(moduleRevision);
		}
		// increase module revision counter
		moduleRevisionCounter.incCount();
	}

}
