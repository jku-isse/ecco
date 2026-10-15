package at.jku.isse.ecco.storage.common.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerRemote;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;

	/**
	 * The storage format this version writes. Raise it when a repository written in the new format
	 * cannot be read by older versions - they then say so instead of failing somewhere in loading
	 * (versions from before this field ignore it).
	 */
	public static final int STORAGE_FORMAT = 2; // 2: association trees in pieces (gap #48)

	// the format the core was written in: 0 for one written before the format was recorded
	private int storageFormat;


	private final Repository.Op repository;

	private final Map<String, SerRemote> remoteIndex;
	// the commit and variant indexes of an older core (only an unused DAO filled them) are ignored on read


	public Database() {
		this.repository = new SerRepository();
		this.storageFormat = STORAGE_FORMAT;

		this.remoteIndex = Maps.mutable.empty();
	}


	public int getStorageFormat() {
		return this.storageFormat;
	}

	/** Before a write: the core is written in this version's format. */
	public void recordStorageFormat() {
		this.storageFormat = STORAGE_FORMAT;
	}

	public Repository.Op getRepository() {
		return this.repository;
	}


	public Map<String, SerRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

}
