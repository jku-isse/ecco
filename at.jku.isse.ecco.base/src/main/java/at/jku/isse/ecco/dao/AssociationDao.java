package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;

import java.util.List;
import java.util.Map;

/**
 * A data access object that handles {@link Association} entities.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CoreModule}.
 *
 * @author Hannes Thaller
 * @version 1.0
 * @see Association Association
 */
public interface AssociationDao extends GenericDao<Association> {

	/**
	 * Loads all associations from the storage.
	 *
	 * @return Returns all stored associations.
	 */
	List<Association> loadAllAssociations() throws EccoException;

	/**
	 * Loads a map containing the depending associations.
	 *
	 * @return The dependency map.
	 */
	Map<Association, Map<Association, Integer>> loadDependencyMap() throws EccoException;

	/**
	 * Loads a map containing the conflicting associations.
	 *
	 * @return The conflicts map.
	 */
	Map<Association, Map<Association, Integer>> loadConflictsMap() throws EccoException;

	/**
	 * Stores the dependency map.
	 *
	 * @param dependencyMap to store.
	 */
	void storeDependencyMap(Map<Association, Map<Association, Integer>> dependencyMap) throws EccoException;

	void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) throws EccoException;

}
