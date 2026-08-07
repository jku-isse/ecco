package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

public class Checkout {

	private Configuration configuration;
	private Collection<Warning> warnings;

	private Set<ModuleRevision> missing;
	private Set<ModuleRevision> surplus;
	private Map<ModuleRevision,String> surplusModules;
	private Map<ModuleRevision,String> missingLocations;


	private Collection<Node> orderWarnings;

	private Set<Association> unresolvedAssociations;
	private Set<Association> selectedAssociations;

	private List<String> constraintWarnings;

	private Node node;

	private String message;

	public Checkout() {
		this.warnings = new ArrayList<>();
		this.missing = new HashSet<>();
		this.surplus = new HashSet<>();
		this.surplusModules = new HashMap<>();
		this.missingLocations = new HashMap<>();
		this.orderWarnings = new ArrayList<>();
		this.unresolvedAssociations = new HashSet<>();
		this.selectedAssociations = new HashSet<>();
		this.constraintWarnings = new ArrayList<>();
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

	public Map<ModuleRevision,String> getSurplusModules() {
		return this.surplusModules;
	}

	public void setSurplusModules(Map<ModuleRevision,String> surplusModules){
		this.surplusModules = surplusModules;
	}

	public Set<ModuleRevision> getMissing() {
		return this.missing;
	}

	public Map<ModuleRevision,String> getMissingLocations() {
		return this.missingLocations;
	}

	public void setMissingLocations(Map<ModuleRevision,String> missingLocations) {
		this.missingLocations = missingLocations;
	}

	public Collection<Node> getOrderWarnings() {
		return this.orderWarnings;
	}

	public Set<Association> getUnresolvedAssociations() {
		return this.unresolvedAssociations;
	}

	public Set<Association> getSelectedAssociations() {
		return this.selectedAssociations;
	}

	public List<String> getConstraintWarnings() {
		return this.constraintWarnings;
	}

	public String getMessage() {
		return this.message;
	}

}
