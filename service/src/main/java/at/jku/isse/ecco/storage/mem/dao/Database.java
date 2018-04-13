package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final Repository.Op repository;

	private final Map<Integer, MemCommit> commitIndex;
	private final Map<String, MemVariant> variantIndex;
	private final Map<String, MemRemote> remoteIndex;

	private int currentCommitId = 0;


	public Database() {
		this.repository = new MemRepository();

		this.commitIndex = new HashMap<>();
		this.variantIndex = new HashMap<>();
		this.remoteIndex = new HashMap<>();
	}


	public int nextCommitId() {
		this.currentCommitId++;
		return this.currentCommitId;
	}


	public Repository.Op getRepository() {
		return this.repository;
	}


	public Map<Integer, MemCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, MemRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<String, MemVariant> getVariantIndex() {
		return this.variantIndex;
	}


}
