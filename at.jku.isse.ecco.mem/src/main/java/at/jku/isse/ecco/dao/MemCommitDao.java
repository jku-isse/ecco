package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;

import java.util.List;

public class MemCommitDao implements CommitDao {

	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		return null;
	}

	@Override
	public void open() throws EccoException {

	}

	@Override
	public void close() throws EccoException {

	}

	@Override
	public void init() throws EccoException {

	}

	@Override
	public Commit load(String id) throws EccoException {
		return null;
	}

	@Override
	public void remove(String id) throws EccoException {

	}

	@Override
	public void remove(Commit entity) throws EccoException {

	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		return null;
	}

}
