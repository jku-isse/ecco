package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;

import java.util.List;

/**
 * A data access object that handles {@link Association} entities.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CoreModule}.
 *
 * @author Hannes Thaller
 * @version 1.0
 * @see Association Association
 */
public interface AssociationDao extends EntityDao<Association> {

	/**
	 * Loads all associations from the storage.
	 *
	 * @return Returns all stored associations.
	 */
	List<Association> loadAllAssociations() throws EccoException;

}
