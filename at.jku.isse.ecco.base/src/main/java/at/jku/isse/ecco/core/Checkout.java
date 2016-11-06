package at.jku.isse.ecco.core;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.Module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Checkout {

	private Configuration configuration;
	private Collection<Warning> warnings;

	private Set<Module> missing;
	private Set<Module> surplus;

	private Collection<Artifact<?>> orderWarnings;

	private Set<Association> unresolvedAssociations;

	private String message;

	public Checkout() {
		this.warnings = new ArrayList<>();
		this.missing = new HashSet<>();
		this.surplus = new HashSet<>();
		this.orderWarnings = new ArrayList<>();
		this.unresolvedAssociations = new HashSet<>();
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
		this.message = "";
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

	public Collection<Warning> getWarnings() {
		return this.warnings;
	}

	public Set<Module> getSurplus() {
		return this.surplus;
	}

	public Set<Module> getMissing() {
		return this.missing;
	}

	public Collection<Artifact<?>> getOrderWarnings() {
		return this.orderWarnings;
	}

	public Set<Association> getUnresolvedAssociations() {
		return this.unresolvedAssociations;
	}

	public String getMessage() {
		return this.message;
	}

}
