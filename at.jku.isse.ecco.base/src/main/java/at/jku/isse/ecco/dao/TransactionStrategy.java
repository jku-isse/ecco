package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;

public interface TransactionStrategy {

	public void init() throws EccoException;

	public void close() throws EccoException;


	public void begin() throws EccoException;

	public void commit() throws EccoException;

	public void rollback() throws EccoException;

}
