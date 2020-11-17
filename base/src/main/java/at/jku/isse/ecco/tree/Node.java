package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.util.Trees;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Public interface for a node in the artifact tree.
 */
public interface Node extends Persistable {

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);


	public default String getNodeString() {
		return Objects.toString(this.getArtifact());
	}

	@Override
	public String toString();


	public default void traverse(NodeVisitor visitor) {
		visitor.visit(this);

		for (Node child : this.getChildren()) {
			child.traverse(visitor);
		}
	}

	public interface NodeVisitor {
		public void visit(Node node);
	}


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
	 * @return The stored artifact.
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
	 *
	 * @return The number of artifacts contained in the tree.
	 */
	public default int countArtifacts() {
		return Trees.countArtifacts(this);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#computeDepth(Node)}
	 *
	 * @return The depth of the tree.
	 */
	public default int computeDepth() {
		return Trees.computeDepth(this);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifactsPerDepth(Node)}
	 *
	 * @return A map containing the number of artifacts (value) per depth (key).
	 */
	public default Map<Integer, Integer> countArtifactsPerDepth() {
		return Trees.countArtifactsPerDepth(this);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#print(Node)}
	 */
	public default void print() {
		Trees.print(this);
	}


	// PROPERTIES

	/**
	 * Returns the transient properties of this artifact.
	 *
	 * @return The properties map of this artifact.
	 */
	public Map<String, Object> getProperties();


	/**
	 * Returns the property with the given name in form of an optional. The optional will only contain a result if the name and the type are correct. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name The name of the property that should be retrieved.
	 * @param <T>  The type of the property.
	 * @return An optional which contains the actual property or nothing.
	 */
	public default <T> Optional<T> getProperty(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");

		Optional<T> result = Optional.empty();
		if (this.getProperties().containsKey(name)) {
			final Object obj = this.getProperties().get(name);
			try {
				@SuppressWarnings("unchecked") final T item = (T) obj;
				result = Optional.of(item);
			} catch (final ClassCastException e) {
				System.err.println("Expected a different type of the property.");
			}
		}

		return result;
	}

	/**
	 * Adds a new property. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name     The name of the property.
	 * @param property The object to be added as a property of the given name.
	 * @param <T>      The type of the property to be added.
	 */
	public default <T> void putProperty(final String name, final T property) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");
		checkNotNull(property);

		this.getProperties().put(name, property);
	}

	/**
	 * Removes the property with the given name. If the name could not be found in the map it does nothing.
	 *
	 * @param name of the property that should be removed
	 */
	public default void removeProperty(String name) {
		checkNotNull(name);

		this.getProperties().remove(name);
	}


	/**
	 * Private interface for node operands that are used internally and not passed outside.
	 */
	public interface Op extends Node {

		public default void traverse(NodeVisitor visitor) {
			visitor.visit(this);

			for (Node.Op child : this.getChildren()) {
				child.traverse(visitor);
			}
		}

		public interface NodeVisitor {
			public void visit(Node.Op node);
		}


		public Association.Op getContainingAssociation();

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
		public List<? extends Op> getChildren();

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
		 * Creates a new instance of this type of node.
		 *
		 * @param artifact The artifact to set for this node.
		 * @return The new node instance.
		 */
		public Op createNode(Artifact.Op<?> artifact);


		/**
		 * Returns the list of artifacts of the child nodes of this node.
		 *
		 * @return The list of artifacts.
		 */
		public default List<? extends Artifact.Op<?>> getChildrenArtifacts() {
			return this.getChildren().stream().map((Function<at.jku.isse.ecco.tree.Node.Op, ? extends Artifact.Op<?>>) at.jku.isse.ecco.tree.Node.Op::getArtifact).collect(Collectors.toList());
		}


		/**
		 * See {@link at.jku.isse.ecco.util.Trees#slice(Node.Op, Node.Op)}
		 *
		 * @param node The other node to slice with this one.
		 */
		public default void slice(Op node) {
			Trees.slice(this, node);
		}

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#merge(Node.Op, Node.Op)}
		 *
		 * @param node The other node to merge with this one.
		 */
		public default void merge(Op node) {
			Trees.merge(this, node);
		}

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#sequence(Node.Op)}
		 */
		public default void sequence() {
			Trees.sequence(this);
		}

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#updateArtifactReferences(Node.Op)}
		 */
		public default void updateArtifactReferences() {
			Trees.updateArtifactReferences(this);
		}

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#extractMarked(Node.Op)}
		 *
		 * @return The tree containing the marked nodes of this tree.
		 */
		public default Node extractMarked() {
			return Trees.extractMarked(this);
		}

		/**
		 * See {@link at.jku.isse.ecco.util.Trees#checkConsistency(Node.Op)}
		 */
		public default void checkConsistency() {
			Trees.checkConsistency(this);
		}

	}

}
