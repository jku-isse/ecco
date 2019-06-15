package at.jku.isse.ecco.storage.jackson.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.jackson.core.JacksonCommit;
import at.jku.isse.ecco.storage.jackson.core.JacksonRemote;
import at.jku.isse.ecco.storage.jackson.core.JacksonVariant;
import at.jku.isse.ecco.storage.jackson.repository.JacksonRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final JacksonRepository repository;

	private final Map<String, JacksonCommit> commitIndex;
	private final Map<String, JacksonVariant> variantIndex;
	private final Map<String, JacksonRemote> remoteIndex;


	public Database() {
		this.repository = new JacksonRepository();

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


	public Map<String, JacksonCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, JacksonRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<String, JacksonVariant> getVariantIndex() {
		return this.variantIndex;
	}

}
