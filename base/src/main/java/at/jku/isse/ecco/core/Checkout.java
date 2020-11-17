package at.jku.isse.ecco.core;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Checkout {

	private Configuration configuration;
	private Collection<Warning> warnings;

	private Set<ModuleRevision> missing;
	private Set<ModuleRevision> surplus;

	private Collection<Artifact<?>> orderWarnings;

	private Set<Association> unresolvedAssociations;
	private Set<Association> selectedAssociations;

	private Node node;

	private String message;

	public Checkout() {
		this.warnings = new ArrayList<>();
		this.missing = new HashSet<>();
		this.surplus = new HashSet<>();
		this.orderWarnings = new ArrayList<>();
		this.unresolvedAssociations = new HashSet<>();
		this.selectedAssociations = new HashSet<>();
		this.node = null;
		this.message = "";
	}

	public Node getNode() {
		return this.node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

	public Collection<Warning> getWarnings() {
		return this.warnings;
	}

	public Set<ModuleRevision> getSurplus() {
		return this.surplus;
	}

	public Set<ModuleRevision> getMissing() {
		return this.missing;
	}

	public Collection<Artifact<?>> getOrderWarnings() {
		return this.orderWarnings;
	}

	public Set<Association> getUnresolvedAssociations() {
		return this.unresolvedAssociations;
	}

	public Set<Association> getSelectedAssociations() {
		return this.selectedAssociations;
	}

	public String getMessage() {
		return this.message;
	}

}
