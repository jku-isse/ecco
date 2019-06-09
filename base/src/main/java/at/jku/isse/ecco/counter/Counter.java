package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.dao.Persistable;

public interface Counter<T> extends Persistable {

	/**
	 * Returns the wrapped object.
	 *
	 * @return The wrapped object.
	 */
	public T getObject();


	public int getCount();

	public void setCount(int count);

	public void incCount();

	public void incCount(int count);

}
