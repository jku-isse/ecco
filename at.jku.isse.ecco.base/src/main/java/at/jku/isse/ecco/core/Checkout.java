package at.jku.isse.ecco.core;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.Module;

import java.util.Collection;
import java.util.Set;

public interface Checkout {

	public Configuration getConfiguration();

	public Collection<Warning> getWarnings();

	public Set<Module> getSurplus();

	public Set<Module> getMissing();

	public Collection<Artifact<?>> getOrderWarnings();

	public String getMessage();

}
