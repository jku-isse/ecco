package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Association}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseAssociation implements Association, Association.Op {

	private String id;
	private String name = "";
	private RootNode.Op artifactTreeRoot;
	private PresenceCondition presenceCondition;

	/**
	 * Constructs a new association.
	 */
	public BaseAssociation() {
		//this.id = id;
		//this.id = UUID.randomUUID().toString();
	}

	@Override
	public PresenceCondition getPresenceCondition() {
		return this.presenceCondition;
	}

	@Override
	public void setPresenceCondition(PresenceCondition presenceCondition) {
		this.presenceCondition = presenceCondition;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(final String id) {
		this.id = id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(final String name) {
		checkNotNull(name);

		this.name = name;
	}

	@Override
	public RootNode.Op getRootNode() {
		return artifactTreeRoot;
	}

	@Override
	public void setRootNode(final RootNode.Op root) {
		this.artifactTreeRoot = root;
		root.setContainingAssociation(this);
	}

	@Override
	public String toString() {
		return String.format("Id: %d, Name: %s, Artifact Tree: %s", this.id, this.name, artifactTreeRoot.toString());
	}

}
