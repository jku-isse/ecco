package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;

/**
 * The root of a artifact tree containing a back-pointer to the association containing the tree.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CorePlugin#EXTENSION_POINT_DAL}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface RootNode extends Node {

	RootNode slice(RootNode other) throws EccoException;

	/**
	 * Returns the association that contains this node.
	 *
	 * @return The association that contains this node.
	 */
	@Override
	Association getContainingAssociation();

	/**
	 * Sets the association that contains this node.
	 *
	 * @param containingAssociation that contains this node
	 */
	void setContainingAssociation(Association containingAssociation);

}
