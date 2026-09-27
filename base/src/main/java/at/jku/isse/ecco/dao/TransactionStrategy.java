package at.jku.isse.ecco.dao;

public interface TransactionStrategy {

	public enum TRANSACTION {
		READ_ONLY, READ_WRITE
	}


	public void open();

	/**
	 * Whether the repository location holds repository data, checked before open() - which, like
	 * the rest of opening a repository, may write into the location. True by default, for storage
	 * without a location of its own.
	 */
	public default boolean containsRepository() {
		return true;
	}

	public void close();


	public void begin(TRANSACTION transaction);

	public void end();

	public void rollback();

}
