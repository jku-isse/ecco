package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;

/**
 * A data access object that handles {@link Persistable}s of a certain type.
 *
 * @param <T> type that should be persisted
 * @author Hannes Thaller
 * @version 1.0
 * @see Persistable Persistable that can be persisted.
 */
public interface GenericDao<T extends Persistable> {

	public void open() throws EccoException;

	public void close() throws EccoException;

	public void init() throws EccoException;

}
