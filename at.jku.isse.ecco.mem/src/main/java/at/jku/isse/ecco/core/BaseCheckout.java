package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;

import java.util.ArrayList;
import java.util.Collection;

public class BaseCheckout implements Checkout {

	private Configuration configuration;
	private Collection<Warning> warnings;

	public BaseCheckout() {
		this.warnings = new ArrayList<>();
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	@Override
	public Configuration getConfiguration() {
		return this.configuration;
	}

	@Override
	public Collection<Warning> getWarnings() {
		return this.warnings;
	}

}
