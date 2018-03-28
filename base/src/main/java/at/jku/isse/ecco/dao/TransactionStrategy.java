package at.jku.isse.ecco.dao;

public interface TransactionStrategy {

	public void open();

	public void close();


	public void begin();

	public void end();

	public void rollback();

}
