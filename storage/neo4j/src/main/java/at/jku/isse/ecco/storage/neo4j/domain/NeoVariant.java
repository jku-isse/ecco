package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.storage.neo4j.domain.NeoEntity;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class NeoVariant extends NeoEntity implements Variant {

	public NeoVariant() {}

    @Property("name")
	private String name;

    @Property("description")
	private String description;

    // no backref from NC
    @Relationship("hasConfigurationVr")
    private Configuration configuration;

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
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
