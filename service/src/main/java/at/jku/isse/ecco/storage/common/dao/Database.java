package at.jku.isse.ecco.storage.common.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerRemote;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final Repository.Op repository;

	private final Map<String, SerRemote> remoteIndex;
	// the commit and variant indexes of an older core (only an unused DAO filled them) are ignored on read


	public Database() {
		this.repository = new SerRepository();

		this.remoteIndex = Maps.mutable.empty();
	}


	public Repository.Op getRepository() {
		return this.repository;
	}


	public Map<String, SerRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

}
