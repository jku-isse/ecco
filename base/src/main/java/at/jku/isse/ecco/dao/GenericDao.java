package at.jku.isse.ecco.dao;

/**
 * A data access object that handles {@link Persistable}s of a certain type.
 *
 * @author Hannes Thaller
 * @version 1.0
 * @see Persistable Persistable that can be persisted.
 */
public interface GenericDao {

	public void open();

	public void close();

	public void init();

}
