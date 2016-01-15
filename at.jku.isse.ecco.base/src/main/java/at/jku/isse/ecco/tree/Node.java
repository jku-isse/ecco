package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.EccoException;
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

	<T extends Node> T slice(T other) throws EccoException;

	Association getContainingAssociation();

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
	 * Returns the sequence number that describes the order in the statement was found.
	 *
	 * @return The sequence number.
	 */
	int getSequenceNumber();

	/**
	 * Sets the sequence number that describes the order in the statement was found.
	 *
	 * @param sequenceNumber of this node
	 */
	void setSequenceNumber(int sequenceNumber);

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
	 * Returns whether the node is a leaf node.
	 *
	 * @return True if the node is a leaf node, false otherwise.
	 */
	boolean isAtomic();

	/**
	 * Sets whether the node is a leaf node.
	 *
	 * @param atomic true if it is a leaf node, false otherwise
	 */
	void setAtomic(boolean atomic);

	/**
	 * Returns whether this node is unique or not describing if it was uniquely or shared within a product.
	 *
	 * @return The node is unique or not.
	 */
	boolean isUnique();

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
	List<Node> getAllChildren();

	/**
	 * Returns the unique children that where not found in other products.
	 *
	 * @return The unique children.
	 */
	List<Node> getUniqueChildren();

	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);

}
