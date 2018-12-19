package at.jku.isse.ecco.storage.neo4j.tree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.RootNode;

public class MemRootNode extends MemNode implements RootNode, RootNode.Op {

	public static final long serialVersionUID = 1L;


	private Association containingAssociation;


	public MemRootNode() {
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
		return new MemRootNode();
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
