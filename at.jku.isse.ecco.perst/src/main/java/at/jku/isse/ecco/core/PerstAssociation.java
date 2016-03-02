package at.jku.isse.ecco.core;

import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Association}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstAssociation extends Persistent implements Association {

	private int id;
	private String name = "";
	private RootNode rootNode;
	private PresenceCondition presenceCondition;
	private List<Association> parents = new ArrayList<Association>();

	public PerstAssociation() {

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
	public List<Association> getParents() {
		return this.parents;
	}

	@Override
	public void addParent(Association parent) {
		this.parents.add(parent);
	}

	@Override
	public void removeParent(Association parent) {
		this.parents.remove(parent);
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public void setId(final int id) {
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
	public RootNode getRootNode() {
		return rootNode;
	}

	@Override
	public void setRootNode(final RootNode root) {
		this.rootNode = root;
		root.setContainingAssociation(this);
	}

	@Override
	public String toString() {
		return String.format("Id: %d, Name: %s, Artifact Tree: %s", this.id, this.name, rootNode.toString());
	}

}
