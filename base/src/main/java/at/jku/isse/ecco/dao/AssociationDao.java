package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Association;

import java.util.List;

/**
 * A data access object that handles {@link Association} entities.
 *
 * @see Association Association
 */
public interface AssociationDao extends EntityDao<Association> {

	/**
	 * Loads all associations from the storage.
	 *
	 * @return Returns all stored associations.
	 */
	List<Association.Op> loadAllAssociations();

}
