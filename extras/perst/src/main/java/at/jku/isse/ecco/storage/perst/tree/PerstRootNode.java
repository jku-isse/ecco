package at.jku.isse.ecco.storage.perst.tree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.RootNode;

/**
 * A perst implementation of the Root node.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstRootNode extends PerstNode implements RootNode, RootNode.Op {

	private Association.Op containingAssociation;


	public PerstRootNode() {
		super();
	}


	public RootNode.Op createNode() {
		return new PerstRootNode();
	}


	@Override
	public boolean isAtomic() {
		return false;
	}

	@Override
	public boolean isUnique() {
		return true;
	}


	@Override
	public Association getContainingAssociation() {
		this.load();
		return this.containingAssociation;
	}

	@Override
	public void setContainingAssociation(Association.Op containingAssociation) {
		this.load();
		this.containingAssociation = containingAssociation;
	}


	@Override
	public String toString() {
		return "root";
	}


	@Override
	public boolean recursiveLoading() {
		return true;
	}

}
