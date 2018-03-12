package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.storage.mem.core.BaseCommit;
import at.jku.isse.ecco.storage.mem.core.BaseRemote;
import at.jku.isse.ecco.storage.mem.core.BaseVariant;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import at.jku.isse.ecco.repository.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Database {

	private final Repository.Op repository;

	private final Map<Integer, BaseCommit> commitIndex;
	private final Map<String, BaseVariant> variantIndex;
	private final Map<String, BaseRemote> remoteIndex;

	private final Set<String> ignorePatterns = new HashSet<>();
	private final Map<String, String> pluginMap = new HashMap<>();

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


	public Map<String, BaseRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<Integer, BaseCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, BaseVariant> getVariantIndex() {
		return this.variantIndex;
	}


	public Repository.Op getRepository() {
		return this.repository;
	}


	public Set<String> getIgnorePatterns() {
		return this.ignorePatterns;
	}

	public Map<String, String> getPluginMap() {
		return this.pluginMap;
	}

}
