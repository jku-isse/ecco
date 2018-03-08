package at.jku.isse.ecco.storage.perst.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.storage.perst.counter.PerstAssociationCounter;
import at.jku.isse.ecco.storage.perst.module.PerstCondition;
import at.jku.isse.ecco.tree.RootNode;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Association}.
 */
public class PerstAssociation extends Persistent implements Association, Association.Op {

	private String id;
	private String name;
	private RootNode.Op artifactTreeRoot;
	private AssociationCounter associationCounter;


	public PerstAssociation() {
		this.id = "";
		this.name = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new PerstAssociationCounter(this);
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
	public AssociationCounter getCounter() {
		return this.associationCounter;
	}

	@Override
	public Condition createCondition() {
		return new PerstCondition();
	}


	@Override
	public String toString() {
		return this.getAssociationString();
	}

}
