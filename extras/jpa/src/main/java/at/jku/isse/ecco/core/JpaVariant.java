package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.JpaConfiguration;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.io.Serializable;

@Entity
public class JpaVariant implements Variant, Serializable {

	@Id
	private String name;

	private String description;

	@OneToOne(targetEntity = JpaConfiguration.class)
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
