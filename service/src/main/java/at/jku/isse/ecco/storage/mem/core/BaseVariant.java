package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;

/**
 * Memory implementation of {@link Variant}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseVariant implements Variant {

	private String name;
	private String description;
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
