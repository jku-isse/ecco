package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

/**
 * Memory implementation of {@link Commit}.
 */

@NodeEntity
public class NeoCommit extends NeoEntity implements Commit {

    @Property("id")
	private String id;

    @Property("committer")
	private String committer;

    // no incoming from Config
    @Relationship("hasConfigurationCm")
	private Configuration configuration;

	public NeoCommit() {
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public Configuration getConfiguration() {
		return this.configuration;
	}

	@Override
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

}
