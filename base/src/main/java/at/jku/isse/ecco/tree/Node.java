package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public interface for a node in the artifact tree.
 */
public interface Node {

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	@Override
	public String toString();


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
	public Artifact<?> getArtifact();

	/**
	 * Returns the parent node.
	 *
	 * @return The parent.
	 */
	public Node getParent();

	/**
	 * Returns whether this node is unique or not.
	 *
	 * @return True if the node is unique, false otherwise.
	 */
	public boolean isUnique();

	/**
	 * Returns all children of this node.
	 *
	 * @return all children
	 */
	public List<? extends Node> getChildren();


	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifacts(Node)}
	 */
	public int countArtifacts();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifacts(Node)}
	 */
	public int computeDepth();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifactsPerDepth(Node)}
	 */
	public Map<Integer, Integer> countArtifactsPerDepth();

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#print(Node)}
	 */
	public void print();


	// PROPERTIES

	/**
	 * Returns the property with the given name in form of an optional. The optional will only contain a result if the name and the type are correct. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name of the property that should be retrieved
	 * @return An optional which contains the actual property or nothing.
	 */
	public <T> Optional<T> getProperty(String name);

	/**
	 * Adds a new property. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param property that should be added
	 */
	public <T> void putProperty(String name, T property);

	/**
	 * Removes the property with the given name. If the name could not be found in the map it does nothing.
	 *
	 * @param name of the property that should be removed
	 */
	public void removeProperty(String name);


	// OPERAND INTERFACE

	/**
	 * Private interface for node operands that are used internally and not passed outside.
	 */
	public interface Op extends Node {
		/**
		 * Returns the transient properties of this artifact.
		 *
		 * @return The properties map of this artifact.
		 */
		public Map<String, Object> getProperties();

		/**
		 * Sets the node to be unique or not.
		 *
		 * @param unique Whether the node is unique or not.
		 */
		public void setUnique(boolean unique);

		@Override
		public Artifact.Op<?> getArtifact();

		/**
		 * Sets the artifact that should be stored in the node.
		 *
		 * @param artifact that should be stored in the node
		 */
		public void setArtifact(Artifact.Op<?> artifact);

		/**
		 * Returns the parent node.
		 *
		 * @return The parent.
		 */
		public Op getParent();

		/**
		 * Sets the parent of the node.
		 *
		 * @param parent the parent of the node
		 */
		public void setParent(Node.Op parent);

		@Override
		public List<Op> getChildren();

		/**
		 * Adds a new child node to this node.
		 *
		 * @param child Child node to be added.
		 */
		public void addChild(Op child);

		/**
		 * Adds a list of children to this node.
		 *
		 * @param children List of child nodes to be added.
		 */
		public void addChildren(Op... children);

		/**
		 * Removes the given child from the node.
		 *
		 * @param child Child node that is to be removed.
		 */
		public void removeChild(Op child);

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#slice(Op, Op)}
		 */
		public void slice(Op node);

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#merge(Op, Op)}
		 */
		public void merge(Op node);

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#sequence(Node.Op)}
		 */
		public void sequence();

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#updateArtifactReferences(Op)}
		 */
		public void updateArtifactReferences();

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#extractMarked(Op)}
		 */
		public Node extractMarked();

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#checkConsistency(Op)}
		 */
		public void checkConsistency();


		/**
		 * Creates a new instance of this type of node.
		 *
		 * @return The new node instance.
		 */
		public Op createNode();

	}

}
