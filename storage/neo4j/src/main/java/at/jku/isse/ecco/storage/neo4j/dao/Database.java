package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.core.NeoCommit;
import at.jku.isse.ecco.storage.neo4j.core.NeoRemote;
import at.jku.isse.ecco.storage.neo4j.core.NeoVariant;
import at.jku.isse.ecco.storage.neo4j.repository.MemRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class Database implements Serializable {

	public static final long serialVersionUID = 1L;


	private final Repository.Op repository;

	private final Map<String, NeoCommit> commitIndex;
	private final Map<String, NeoVariant> variantIndex;
	private final Map<String, NeoRemote> remoteIndex;


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


	public Map<String, NeoCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public Map<String, NeoRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public Map<String, NeoVariant> getVariantIndex() {
		return this.variantIndex;
	}

}
