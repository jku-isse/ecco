package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.dao.Persistable;

public interface Counter<T> extends Persistable {

	/**
	 * Adds other counter to this counter. Recursively adds up all child counters.
	 *
	 * @param other
	 */
	public default void add(Counter<T> other) {
		this.incCount(other.getCount());
	}


	/**
	 * Returns the wrapped object.
	 *
	 * @return
	 */
	public T getObject();


	public int getCount();

	public void setCount(int count);

	public void incCount();

	public void incCount(int count);

}
