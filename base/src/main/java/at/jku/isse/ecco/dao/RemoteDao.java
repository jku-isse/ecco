package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Remote;

import java.util.Collection;

public interface RemoteDao extends GenericDao {

	public Collection<Remote> loadAllRemotes();

	public Remote loadRemote(String name);

	public Remote storeRemote(Remote remote);

	public void removeRemote(String name);

}
