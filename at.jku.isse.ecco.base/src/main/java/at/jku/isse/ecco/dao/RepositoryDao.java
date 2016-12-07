package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.repository.RepositoryOperand;

public interface RepositoryDao extends GenericDao {

	public RepositoryOperand load();

	public void store(RepositoryOperand repository);

}
