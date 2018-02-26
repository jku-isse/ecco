package at.jku.isse.ecco.counter;

public interface Counter<T> {

	/**
	 * Adds other counter to this counter. Recursively adds up all child counters.
	 *
	 * @param other
	 */
	public void add(Counter<T> other);


	/**
	 * Returns the wrapped object.
	 *
	 * @return
	 */
	public T getObject();


	public int getCount();

	public void incCount();

	public void incCount(int value);

}
