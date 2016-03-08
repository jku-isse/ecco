package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;

import java.util.List;
import java.util.Map;

public class MemAssociationDao implements AssociationDao {

	@Override
	public List<Association> loadAllAssociations() throws EccoException {
		return null;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadDependencyMap() throws EccoException {
		return null;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadConflictsMap() throws EccoException {
		return null;
	}

	@Override
	public void storeDependencyMap(Map<Association, Map<Association, Integer>> dependencyMap) throws EccoException {

	}

	@Override
	public void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) throws EccoException {

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
	public Association load(String id) throws EccoException {
		return null;
	}

	@Override
	public void remove(String id) throws EccoException {

	}

	@Override
	public void remove(Association entity) throws EccoException {

	}

	@Override
	public Association save(Association entity) throws EccoException {
		return null;
	}

}
