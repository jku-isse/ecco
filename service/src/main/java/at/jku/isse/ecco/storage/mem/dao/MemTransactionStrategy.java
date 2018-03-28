package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MemTransactionStrategy implements TransactionStrategy {

	protected Database database;

	@Inject
	public MemTransactionStrategy() {
		this.database = null;
	}

	protected Database getDatabase() {
		return this.database;
	}

	@Override
	public void open() throws EccoException {
		this.database = new Database();
	}

	@Override
	public void close() throws EccoException {
		this.database = null;
	}

	@Override
	public void begin() throws EccoException {

	}

	@Override
	public void end() throws EccoException {

	}

	@Override
	public void rollback() {
		System.err.println("Rollback not supported by backend.");
		//throw new EccoException("Rollback not supported by backend.");
	}

}
