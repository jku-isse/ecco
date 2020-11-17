package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;

public interface EntityDao<T extends Persistable> extends GenericDao {

	/**
	 * Returns the entity with the given id.
	 *
	 * @param id of the entity
	 * @return The entity with that has the given id.
	 * @throws IllegalArgumentException If the id does not exist.
	 */
	T load(String id) throws EccoException;

	/**
	 * Removes the entity with the specified id.
	 *
	 * @param id of the entity that should be removed
	 * @throws IllegalArgumentException If the id does not exist.
	 */
	void remove(String id) throws EccoException;

	/**
	 * Removes the given entity from the storage.
	 *
	 * @param entity that should be removed
	 */
	void remove(T entity) throws EccoException;

	/**
	 * Persists the given entity.
	 *
	 * @param entity that should be persisted
	 * @return The saved entity which may be a different instance with the updated id.
	 * @see Persistable The updated id.
	 */
	T save(T entity) throws EccoException;

}
