package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.core.NeoCommit;
import at.jku.isse.ecco.storage.neo4j.domain.core.NeoVariant;
import org.eclipse.collections.impl.factory.Maps;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@NodeEntity
public class NeoDatabase extends NeoEntity implements Serializable {

    @Relationship
	private final Repository.Op repository;

    @Relationship
	private final Map<String, NeoCommit> commitIndex;

    @Relationship("HAS")
	private final Map<String, NeoVariant> variantIndex;

    @Relationship("HAS")
	private final Map<String, NeoRemote> remoteIndex;


	public NeoDatabase() {
		this.repository = new NeoRepository();

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
