package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.Module;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PerstCheckout extends Persistent implements Checkout {

	private Configuration configuration;
	private Collection<Warning> warnings;

	private Set<Module> missing;
	private Set<Module> surplus;

	public PerstCheckout() {
		this.warnings = new ArrayList<>();
		this.missing = new HashSet<>();
		this.surplus = new HashSet<>();
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

	@Override
	public Set<Module> getSurplus() {
		return this.surplus;
	}

	@Override
	public Set<Module> getMissing() {
		return this.missing;
	}

}
