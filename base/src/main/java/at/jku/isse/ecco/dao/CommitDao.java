package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Commit;

import java.util.List;

public interface CommitDao extends EntityDao<Commit> {

	List<Commit> loadAllCommits();

}
