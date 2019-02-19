package at.jku.isse.ecco.storage.mem.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.RootNode;

public class MemRootNode extends MemNode implements RootNode, RootNode.Op {

	public static final long serialVersionUID = 1L;


	private Association.Op containingAssociation;


	public MemRootNode() {
		//super(new MemArtifact<>(new StringArtifactData("ROOT")));
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
	public RootNode.Op createNode(Artifact.Op<?> artifact) {
		return new MemRootNode();
	}


	@Override
	public void setContainingAssociation(Association.Op containingAssociation) {
		this.containingAssociation = containingAssociation;
	}

	@Override
	public Association.Op getContainingAssociation() {
		return this.containingAssociation;
	}

}
