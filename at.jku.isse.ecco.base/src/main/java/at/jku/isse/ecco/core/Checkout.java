package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;

import java.util.Collection;

public interface Checkout {

	public Configuration getConfiguration();

	public Collection<Warning> getWarnings();

}
