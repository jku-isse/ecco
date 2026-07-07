package at.jku.isse.ecco.storage.common.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.core.SerRemote;
import at.jku.isse.ecco.storage.ser.core.SerVariant;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final Repository.Op repository;

	private final Map<String, SerCommit> commitIndex;
	private final Map<String, SerVariant> variantIndex;
	private final Map<String, SerRemote> remoteIndex;


	public Database() {
		this.repository = new SerRepository();

		this.commitIndex = Maps.mutable.empty();
		this.variantIndex = Maps.mutable.empty();
		this.remoteIndex = Maps.mutable.empty();
	}


	public String nextCommitId() {
		return UUID.randomUUID().toString();
	}


	public Repository.Op getRepository() {
		return this.repository;
	}


	public Map<String, SerCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, SerRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<String, SerVariant> getVariantIndex() {
		return this.variantIndex;
	}

}
