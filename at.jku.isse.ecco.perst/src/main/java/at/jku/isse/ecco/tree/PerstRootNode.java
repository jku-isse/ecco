package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.core.Association;

/**
 * A perst implementation of the Root node.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstRootNode extends PerstNode implements RootNode {

	private Association containingAssociation;


	public PerstRootNode() {
		super();
	}


	public Node createNode() {
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
		return this.containingAssociation;
	}

	@Override
	public void setContainingAssociation(Association containingAssociation) {
		this.containingAssociation = containingAssociation;
	}


	@Override
	public String toString() {
		return "root";
	}


	@Override
	public boolean recursiveLoading() {
		return false;
	}

}
