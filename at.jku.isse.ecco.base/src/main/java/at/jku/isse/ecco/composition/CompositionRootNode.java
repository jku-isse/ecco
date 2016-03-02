package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

/**
 * A lazy composition root node.
 */
public class CompositionRootNode extends CompositionNode implements RootNode {

	public CompositionRootNode() {
		this(new DefaultOrderSelector());
	}

	public CompositionRootNode(OrderSelector orderSelector) {
		super(orderSelector);
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
	public Node createNode() {
		return new CompositionRootNode();
	}


	@Override
	public void setContainingAssociation(Association containingAssociation) {
		// do nothing
	}

	@Override
	public Association getContainingAssociation() {
		return null;
	}

	@Override
	public String toString() {
		return "root";
	}

}
