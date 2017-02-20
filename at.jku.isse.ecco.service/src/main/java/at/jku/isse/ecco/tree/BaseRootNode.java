package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.core.Association;

public class BaseRootNode extends BaseNode implements RootNode, RootNode.Op {

	private Association containingAssociation;


	public BaseRootNode() {
		super();
	}


	@Override
	public boolean isUnique() {
		return true;
	}

	@Override
	public boolean isAtomic() {
		return false;
	}


	@Override
	public RootNode.Op createNode() {
		return new BaseRootNode();
	}


	@Override
	public void setContainingAssociation(Association.Op containingAssociation) {
		this.containingAssociation = containingAssociation;
	}

	@Override
	public Association getContainingAssociation() {
		return this.containingAssociation;
	}

	@Override
	public String toString() {
		return "root";
	}

}
