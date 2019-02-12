package at.jku.isse.ecco.storage.neo4j.domain.tree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.RootNode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class NeoRootNode extends NeoNode implements RootNode, RootNode.Op {

    @Relationship("HAS")
	private Association containingAssociation;


	public NeoRootNode() {
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
		return new NeoRootNode();
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
