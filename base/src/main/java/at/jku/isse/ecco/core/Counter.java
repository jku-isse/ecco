package at.jku.isse.ecco.core;

public interface Counter<T> {

	public T getObject();

	public int getCount();

	public void inc();

}
