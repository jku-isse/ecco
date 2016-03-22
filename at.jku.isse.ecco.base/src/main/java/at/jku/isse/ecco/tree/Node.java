package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A node in the artifact tree.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface Node {

	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);

	@Override
	String toString();


	/**
	 * Creates a new instance of this type of node.
	 *
	 * @return The new node instance.
	 */
	public Node createNode();


	// node

	/**
	 * Returns whether this node is atomic or not.
	 *
	 * @return True if the node is unique, false otherwise.
	 */
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
	 * Returns whether this node is unique or not.
	 *
	 * @return True if the node is unique, false otherwise.
	 */
	boolean isUnique();

	/**
	 * Sets the node to be unique or not.
	 *
	 * @param unique Whether the node is unique or not.
	 */
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


	// properties

	/**
	 * Returns the property with the given name in form of an optional. The optional will only contain a result if the name and the type are correct. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name of the property that should be retrieved
	 * @return An optional which contains the actual property or nothing.
	 */
	<T> Optional<T> getProperty(String name);

	/**
	 * Adds a new property. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param property that should be added
	 */
	<T> void putProperty(String name, T property);

	/**
	 * Removes the property with the given name. If the name could not be found in the map it does nothing.
	 *
	 * @param name of the property that should be removed
	 */
	void removeProperty(String name);


	// operations

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#slice(Node, Node)}
	 */
	public void slice(Node node);

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#merge(Node, Node)}
	 */
	public void merge(Node node);

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#sequence(Node)}
	 */
	public void sequence();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#updateArtifactReferences(Node)}
	 */
	public void updateArtifactReferences();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#extractMarked(Node)}
	 */
	public Node extractMarked();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifacts(Node)}
	 */
	public int countArtifacts();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifactsPerDepth(Node)}
	 */
	public Map<Integer, Integer> countArtifactsPerDepth();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#print(Node)}
	 */
	public void print();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#checkConsistency(Node)}
	 */
	public void checkConsistency();

}
