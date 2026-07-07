package at.jku.isse.ecco.storage.ser.core;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;

/**
 * Memory implementation of {@link Variant}.
 */
public class SerVariant implements Variant {

	public static final long serialVersionUID = 1L;


	private String name;
	private String description;
	private Configuration configuration;
	private String id;

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

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

	public SerVariant(String name, Configuration configuration, String id) {
		this.name = name;
		this.configuration = configuration;
		this.id = id;
	}
}
