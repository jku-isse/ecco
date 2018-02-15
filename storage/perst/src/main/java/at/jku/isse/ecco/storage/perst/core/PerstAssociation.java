package at.jku.isse.ecco.storage.perst.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;
import org.garret.perst.Persistent;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Association}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstAssociation extends Persistent implements Association, Association.Op {

	private String id;
	private String name = "";
	private RootNode.Op rootNode;
	private PresenceCondition presenceCondition;

	public PerstAssociation() {
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
		return rootNode;
	}

	@Override
	public void setRootNode(final RootNode.Op root) {
		this.rootNode = root;
		root.setContainingAssociation(this);
	}

	@Override
	public String toString() {
		return String.format("Id: %s, Name: %s, Artifact Tree: %s", this.id, this.name, rootNode.toString());
	}

}
