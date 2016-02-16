package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;

import java.util.List;

public interface CommitDao extends GenericDao<Commit> {

	List<Commit> loadAllCommits() throws EccoException;

}
