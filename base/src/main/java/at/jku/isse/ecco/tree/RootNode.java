package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.core.Association;

/**
 * Public interface for the root node of an artifact tree containing a reference to the association containing the tree.
 */
public interface RootNode extends Node {

	/**
	 * Private interface for root nodes.
	 */
	public interface Op extends RootNode, Node.Op {
		/**
		 * Sets the association that contains this node.
		 *
		 * @param containingAssociation that contains this node
		 */
		public void setContainingAssociation(Association.Op containingAssociation);
	}

}
