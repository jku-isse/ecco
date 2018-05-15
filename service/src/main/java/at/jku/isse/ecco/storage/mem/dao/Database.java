package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final Repository.Op repository;

	private final Map<String, MemCommit> commitIndex;
	private final Map<String, MemVariant> variantIndex;
	private final Map<String, MemRemote> remoteIndex;


	public Database() {
		this.repository = new MemRepository();

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


	public Map<String, MemCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, MemRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<String, MemVariant> getVariantIndex() {
		return this.variantIndex;
	}

}
