package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.EntityFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class AssociationOperator {

	private Association.Op association;
	private EntityFactory entityFactory;

	public AssociationOperator(Association.Op association) {
		checkNotNull(association);
		this.association = association;
		this.entityFactory = association.getEntityFactory();
	}


}
