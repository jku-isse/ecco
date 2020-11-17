package at.jku.isse.ecco.dao;

public interface TransactionStrategy {

	public enum TRANSACTION {
		READ_ONLY, READ_WRITE
	}


	public void open();

	public void close();


	public void begin(TRANSACTION transaction);

	public void end();

	public void rollback();

}
