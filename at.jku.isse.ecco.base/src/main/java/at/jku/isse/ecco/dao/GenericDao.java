package at.jku.isse.ecco.dao;

/**
 * A data access object that handles {@link Persistable}s of a certain type.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CorePlugin#EXTENSION_POINT_DAL}.
 *
 * @param <T> type that should be persisted
 * @author Hannes Thaller
 * @version 1.0
 * @see Persistable Persistable that can be persisted.
 */
public interface GenericDao<T extends Persistable> {

	public void open();

	public void close();

	public void init();

	/**
	 * Returns the entity with the given id.
	 *
	 * @param id of the entity
	 * @return The entity with that has the given id.
	 * @throws IllegalArgumentException If the id does not exist.
	 */
	T load(String id);

	/**
	 * Removes the entity with the specified id.
	 *
	 * @param id of the entity that should be removed
	 * @throws IllegalArgumentException If the id does not exist.
	 */
	void remove(String id);

	/**
	 * Removes the given entity from the storage.
	 *
	 * @param entity that should be removed
	 */
	void remove(T entity);

	/**
	 * Persists the given entity.
	 *
	 * @param entity that should be persisted
	 * @return The saved entity which may be a different instance with the updated id.
	 * @see Persistable The updated id.
	 */
	T save(T entity);

}
