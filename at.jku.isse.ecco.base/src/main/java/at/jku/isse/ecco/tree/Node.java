package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;

import java.util.List;

/**
 * A node in the artifact tree.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface Node {

	public Node createNode();

	public boolean isAtomic();


	/**
	 * Returns the association that contains this node.
	 *
	 * @return The association that contains this node.
	 */
	public Association getContainingAssociation();


	/**
	 * Returns the artifact stored in the node.
	 *
	 * @return The stored artifact.s
	 */
	Artifact<?> getArtifact();

	/**
	 * Sets the artifact that should be stored in the node.
	 *
	 * @param artifact that should be stored in the node
	 */
	void setArtifact(Artifact<?> artifact);

	/**
	 * Returns the parent node.
	 *
	 * @return The parent.
	 */
	Node getParent();

	/**
	 * Sets the parent of the node.
	 *
	 * @param parent the parent of the node
	 */
	void setParent(Node parent);

	/**
	 * Returns whether this node is unique or not describing if it was uniquely or shared within a product.
	 *
	 * @return The node is unique or not.
	 */
	boolean isUnique();

	void setUnique(boolean unique);

	/**
	 * Adds a new child node to this node.
	 *
	 * @param child that should be added
	 */
	void addChild(Node child);

	/**
	 * Removes the given child from the node.
	 *
	 * @param child that should be removed
	 */
	void removeChild(Node child);

	/**
	 * Returns all children of this node.
	 *
	 * @return all children
	 */
	List<Node> getChildren();


	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);

}
