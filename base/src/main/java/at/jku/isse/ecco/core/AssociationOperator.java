package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

public class AssociationOperator {

	private Association.Op association;
	private EntityFactory entityFactory;

	public AssociationOperator(Association.Op association) {
		this.association = association;
		this.entityFactory = association.getEntityFactory();
	}


	/**
	 * Adds an observation of the given revision module either with the artifacts present or not present.
	 *
	 * @param revisionModule
	 */
	public void addObservation(ModuleRevision revisionModule) {
		Module tempFeatureModule = null; // TODO: create temporary feature module from revision module
		Association.Op.SubTable subTable = this.association.getFeatureModules().get(tempFeatureModule);
		subTable.getCounter().inc();
		subTable.getRevisionModules().get(revisionModule).inc();
	}

	public Module[] getTracingFeatureModules() {
		return null;
	}

	public ModuleRevision[] getTracingRevisionModules() {
		return null;
	}


}
