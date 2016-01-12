package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;

public class BaseRootNode extends BaseNode implements RootNode {

	private Association containingAssociation;


	@Override
	public RootNode slice(RootNode other) throws EccoException {
		return (RootNode) super.slice(other);
	}

	public Node createNode() {
		return new BaseRootNode();
	}


	@Override
	public void setContainingAssociation(Association containingAssociation) {
		this.containingAssociation = containingAssociation;
	}

	@Override
	public Association getContainingAssociation() {
		return this.containingAssociation;
	}

//	@Override
//	public String toString() {
//		return String.format("Children: %s", Arrays.toString(getAllChildren().toArray()));
//	}

	@Override
	public String toString() {
		return "root";
	}

}
