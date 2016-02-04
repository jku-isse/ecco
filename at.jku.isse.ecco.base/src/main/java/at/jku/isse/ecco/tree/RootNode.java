package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.core.Association;

/**
 * The root of a artifact tree containing a back-pointer to the association containing the tree.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface RootNode extends Node {

	/**
	 * Sets the association that contains this node.
	 *
	 * @param containingAssociation that contains this node
	 */
	void setContainingAssociation(Association containingAssociation);

}
