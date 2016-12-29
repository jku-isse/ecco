package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.repository.Repository;

public interface RepositoryDao extends GenericDao {

	public Repository.Op load();

	public void store(Repository.Op repository);

}
