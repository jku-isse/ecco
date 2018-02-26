package at.jku.isse.ecco.counter;

import java.util.Collection;

public interface CounterNode<I, O> {

	/**
	 * Adds other counter to this counter. Recursively adds up all child counters.
	 *
	 * @param other
	 */
	public void add(CounterNode<I, O> other);


	/**
	 * Returns the wrapped object.
	 *
	 * @return
	 */
	public I getObject();


	public int getCount();

	public void incCount();

	public void incCount(int value);


	public <N> CounterNode<O, N> addChild(O child);

	public <N> CounterNode<O, N> getChild(O child);

	public <N> Collection<CounterNode<O, N>> getChildren();

}
