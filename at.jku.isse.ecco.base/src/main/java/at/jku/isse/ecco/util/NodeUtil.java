package at.jku.isse.ecco.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.inject.Inject;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.dao.EntityFactory;

/**
 * Collection of utility functions related to {@link Node}.
 *
 * @author Hannes Thaller, ISSE
 * @version 1.1
 */
public class NodeUtil {

	public static final String PROPERTY_REPLACING_ARTIFACT = "replacingArtifact";

	@Inject
	private static EntityFactory entityFactory;

	/**
	 * Returns a shallow copy of the given node.
	 *
	 * @param node
	 *            that should be copied
	 * @return A shallow copy of the node.
	 */
	public static Node copy(final Node node) {
		checkNotNull(node);

		if (node instanceof OrderedNode) {
			return copyOrderedNode((OrderedNode) node);
		} else if (node instanceof RootNode) {
			return copyRootNode((RootNode) node);
		} else {
			return copyNode(node);
		}
	}

	/**
	 * Copies the node expect of the tree information.
	 *
	 * @param node
	 *            that is copied
	 * @return A copy of the node without the child nodes.
	 */
	public static Node copyWithoutTree(final Node node) {
		checkNotNull(node);

		final Node copy = NodeUtil.copy(node);
		clearTreeInformation(copy);
		return copy;
	}

	/**
	 * Returns the amount of nodes attached to the nodes in the collection.
	 *
	 * @param nodes
	 *            from which the nodes should be counted (node it self counts too).
	 * @return The amount of nodes reachable from the given nodes.
	 */
	public static int countNodes(final Collection<Node> nodes) {
		checkNotNull(nodes);

		int count = 0;
		for (final Node node : nodes) {
			count += countNodes(node);
		}

		return count;
	}

	/**
	 * Returns the amount of nodes attached to the node.
	 *
	 * @param node
	 *            from which the nodes should be counted (node it self counts too).
	 * @return The amount of nodes reachable from the given node.
	 */
	public static int countNodes(final Node node) {
		checkNotNull(node);

		int count = 1;
		for (final Node child : getAllChildren(node)) {
			count += countNodes(child);
		}

		return count;
	}

	/**
	 * Returns the node containing the given artifact by searching through the given nodes (inclusive) and their attached trees.
	 *
	 * @param artifact
	 *            to find
	 * @param nodes
	 *            that contains the artifact
	 * @return The node that contain the artifact or nothing.
	 */
	public static Optional<Node> findNodeContainingArtifact(final Artifact artifact, final Collection<Node> nodes) {
		checkNotNull(artifact);
		checkNotNull(nodes);

		for (final Node node : nodes) {
			final Optional<Node> matchOptional = findNodeContainingArtifact(artifact, node);
			if (matchOptional.isPresent()) {
				return matchOptional;
			}
		}

		return Optional.empty();
	}

	/**
	 * Returns the node containing the given artifact by searching through the given node and its attached trees.
	 *
	 * @param artifact
	 *            to find
	 * @param node
	 *            that contains the artifact
	 * @return The node that contain the artifact or nothing.
	 */
	public static Optional<Node> findNodeContainingArtifact(final Artifact artifact, final Node node) {
		checkNotNull(artifact);
		checkNotNull(node);

		if (artifact.equals(node.getArtifact())) {
			return Optional.of(node);
		}

		for (final Node child : NodeUtil.getAllChildren(node)) {
			final Optional<Node> matchOptional = findNodeContainingArtifact(artifact, child);
			if (matchOptional.isPresent()) {
				return matchOptional;
			}
		}

		return Optional.empty();
	}

	/**
	 * Returns all children from the given parent.
	 *
	 * @param parent
	 *            from which the children should be returned
	 * @return All children from the given <code>parent</code>.
	 */
	public static List<Node> getAllChildren(final Node parent) {
		checkNotNull(parent);

		final List<Node> children = new ArrayList<>();
		if (isOrderedNode(parent) && !((OrderedNode) parent).isSequenced()) {
			children.addAll(((OrderedNode) parent).getOrderedChildren());
		} else {
			children.addAll(parent.getAllChildren());
		}

		return children;
	}

	/**
	 * Returns the next atomic parent if there exists one.
	 *
	 * @param node
	 *            from which the traversal starts
	 * @return The atomic parent or empty.
	 */
	public static Optional<Node> getAtomicPredecessor(final Node node) {
		checkNotNull(node);

		Node currentNode = node;
		while (currentNode != null && !currentNode.isAtomic()) {
			currentNode = currentNode.getParent();
		}

		return Optional.ofNullable(currentNode);
	}

	/**
	 * Returns the depth of the three with respect to the given leaf node. That is, the methods traverse towards the root and counts the levels. The depth starts from 0 to counting
	 * so given only one node, the depth will be 0.
	 *
	 * @param leafNode
	 *            from which to traverse
	 * @return The depth count of the node tree.
	 */
	public static int getDepthFromLeaf(final Node leafNode) {
		checkNotNull(leafNode);

		int depth = -1;
		Node cur = leafNode;
		while (cur != null) {
			cur = cur.getParent();
			depth++;
		}

		return depth;
	}

	/**
	 * Returns all the nodes directly under the root node contained in the associations.
	 *
	 * @param associations
	 *            from which the first level nodes are retrieved
	 * @return List of all first level nodes from the associations.
	 */
	public static List<Node> getFirstLevelNodes(final List<Association> associations) {
		checkNotNull(associations);

		final List<Node> code = new LinkedList<>();
		for (final Association association : associations) {
			code.addAll(getAllChildren(association.getArtifactTreeRoot()));
		}

		return code;
	}

	/**
	 * Returns the unique children of the parent.
	 *
	 * @param node
	 *            from which the unique children should be retrieved
	 * @return The unique children.
	 */
	public static List<Node> getUniqueChildren(final Node node) {
		checkNotNull(node);

		final List<Node> children = new ArrayList<>();
		if (isOrderedNode(node) && !((OrderedNode) node).isSequenced()) {
			children.addAll(((OrderedNode) node).getOrderedChildren());
		} else {
			children.addAll(node.getUniqueChildren());
		}

		return children;
	}

	/**
	 * Returns all the unique sub trees that are connected with the given node.
	 *
	 * @param nodes
	 *            from which the search starts (exclusive)
	 * @return All subtrees that contain a unique root node and are attached to the given nodes.
	 */
	public static List<Node> getUniqueSuccessorTrees(final Collection<Node> nodes) {
		checkNotNull(nodes);

		final List<Node> uniqueSuccessors = new ArrayList<>();
		for (final Node node : nodes) {
			uniqueSuccessors.addAll(getUniqueSuccessorTrees(node));
		}

		return uniqueSuccessors;
	}

	/**
	 * Returns all unique sub trees connected with the node.
	 *
	 * @param node
	 *            from which the search starts (exclusive)
	 * @return All subtrees that contain a unique root node and are attached to the given node.
	 */
	public static List<Node> getUniqueSuccessorTrees(final Node node) {
		checkNotNull(node);

		final List<Node> children = new ArrayList<>();
		if (node.isAtomic()) {
			return children;
		}
		for (final Node child : getAllChildren(node)) {
			if (child.isUnique()) {
				children.add(child);
			} else {
				children.addAll(getUniqueSuccessorTrees(child));
			}
		}

		return children;
	}

	/**
	 * Returns whether the given node is an {@link OrderedNode}.
	 *
	 * @param node
	 *            that should be checked
	 * @return True if the given node is an {@link OrderedNode}, false otherwise.
	 */
	public static boolean isOrderedNode(final Node node) {
		checkNotNull(node);

		return node instanceof OrderedNode;
	}

	/**
	 * Removes the given <code>child</code> from its parent.
	 *
	 * @param child
	 *            that should be removed from its parent.
	 */
	public static void removeNodeFromParent(final Node child) {
		checkNotNull(child);

		final Node parent = child.getParent();

		if (parent != null) {
			if (isOrderedNode(parent) && !((OrderedNode) parent).isSequenced()) {
				((OrderedNode) parent).getOrderedChildren().remove(child);
			} else {
				parent.getAllChildren().remove(child);
				parent.getUniqueChildren().remove(child);
			}
		}
	}

	/**
	 * Intersects the given nodes <code>a</code> and <code>b</code> and returns new intersection results.
	 *
	 * @param a
	 *            that is intersected
	 * @param b
	 *            that is intersected
	 * @return The intersection result from <code>a</code> and <code>b</code>.
	 */
	static Intersection<Node> intersect(final Node a, final Node b) {
		checkNotNull(a);
		checkNotNull(b);
		checkArgument(!a.isAtomic(), "Expected a non atomic node but <a> was atomic. Atomic nodes are leaf nodes and are always unique.");
		checkArgument(!b.isAtomic(), "Expected a non-atomic node but <b> was atomic. Atomic nodes are leaf nodes and are always unique. ");
		checkArgument((a.getArtifact() == null && b.getArtifact() == null) || a.getArtifact() == null || b.getArtifact() == null || a.getArtifact().equals(b.getArtifact()),
				"Expected that the artifact from <a> and <b> are equal but was not.");

		final Node copyA = copy(a), copyB = copy(b);

		// Let all nodes point to the same replacing artifact to avoid loosing
		// references during intersection.
		if (copyA.getArtifact() != null && copyB.getArtifact() != null && copyA.getArtifact() != copyB.getArtifact()) {
			copyB.getArtifact().putProperty(PROPERTY_REPLACING_ARTIFACT, copyA.getArtifact());
			copyB.setArtifact(copyA.getArtifact());
		}

		final Node intersection = intersectChildren(copyA, copyB);

		return new Intersection<>(copyA, intersection, copyB);
	}

	/**
	 * Adds the shared intersection node to the intersection and sets it new parent.
	 *
	 * @param intersection
	 *            to which the node should be added
	 * @param childIntersection
	 *            that is added to the intersection
	 */
	private static void addSharedChildIntersection(final Node intersection, final Node childIntersection) {
		assert intersection != null : "Expected non-null intersection but was null";
		assert childIntersection != null : "Expected non-null childIntersection but was null.";

		// Add shared node with unique children
		intersection.getAllChildren().add(childIntersection);

		childIntersection.setParent(intersection);
	}

	/**
	 * Adds the unique intersection node to the intersection and updates its parent.
	 *
	 * @param intersection
	 *            to which the node should be added
	 * @param childIntersection
	 *            that is added to the intersection
	 */
	private static void addUniqueChildIntersection(final Node intersection, final Node childIntersection) {
		assert intersection != null : "Expected non-null intersection but was null";
		assert childIntersection != null : "Expected non-null childIntersection but was null.";

		// Sets the artifacts containing node correctly
		if (childIntersection.getArtifact() != null) {
			childIntersection.getArtifact().setContainingNode(childIntersection);
		}

		// Intersect unique node
		intersection.getAllChildren().add(childIntersection);
		intersection.getUniqueChildren().add(childIntersection);

		childIntersection.setParent(intersection);
	}

	private static void cleanupUniqueSets(Node left, Node right, Node intersection) {
		left.getUniqueChildren().removeAll(intersection.getUniqueChildren());
		right.getUniqueChildren().removeAll(intersection.getUniqueChildren());

		List<Node> leftUniqueChildrenCopy = left.getUniqueChildren().stream().map(n -> copyNodeWithSpecificParent(n, left)).collect(Collectors.toList());
		List<Node> rightUniqueChildrenCopy = right.getUniqueChildren().stream().map(n -> copyNodeWithSpecificParent(n, right)).collect(Collectors.toList());
		left.getUniqueChildren().clear();
		right.getUniqueChildren().clear();
		left.getUniqueChildren().addAll(leftUniqueChildrenCopy);
		right.getUniqueChildren().addAll(rightUniqueChildrenCopy);
	}

	/**
	 * Clears the tree information of the given node.
	 *
	 * @param node
	 *            from which the tree information should be deleted
	 */
	private static void clearTreeInformation(final Node node) {
		node.getAllChildren().clear();
		node.getUniqueChildren().clear();
		node.setParent(null);

		if (node instanceof OrderedNode) {
			final OrderedNode orderedNode = (OrderedNode) node;
			orderedNode.setSequenced(false);
			orderedNode.getOrderedChildren().clear();
		}
	}

	/**
	 * Returns a shallow copy the given node.
	 *
	 * @param node
	 *            that should be copied.
	 * @return A shallow copy of the given node.
	 */
	private static Node copyNode(final Node node) {
		assert node != null : "Expected a non-null node but was null";
		final Node copy = entityFactory.createNode();

		copy.setArtifact(node.getArtifact());
		copy.setAtomic(node.isAtomic());
		copy.setParent(node.getParent());
		copy.setSequenceNumber(node.getSequenceNumber());
		copy.getAllChildren().addAll(node.getAllChildren());
		copy.getUniqueChildren().addAll(node.getUniqueChildren());

		return copy;
	}

	private static Node copyNodeWithSpecificParent(final Node toCopy, final Node parent) {
		final Node copy = copy(toCopy);
		copy.setParent(parent);
		return copy;
	}

	/**
	 * Makes a shallow copy of the given ordered node.
	 *
	 * @param node
	 *            that should be copied
	 * @return A shallow copy of the given node.
	 */
	private static OrderedNode copyOrderedNode(final OrderedNode node) {
		assert node != null : "Expected non-null node but was null";
		final OrderedNode copy = entityFactory.createOrderedNode();

		copy.setSequenced(node.isSequenced());
		copy.setParent(node.getParent());
		copy.setArtifact(node.getArtifact());
		copy.setSequenceNumber(node.getSequenceNumber());
		copy.setAtomic(node.isAtomic());
		copy.setSequenceGraph(node.getSequenceGraph());
		copy.getAllChildren().addAll(node.getAllChildren());
		copy.getUniqueChildren().addAll(node.getUniqueChildren());
		copy.getOrderedChildren().addAll(node.getOrderedChildren());
		copy.setAligned(node.isAligned());

		return copy;
	}

	/**
	 * Returns a shallow copy of the given root node.
	 *
	 * @param node
	 *            that should be copied
	 * @return A shallow copy of the given node.
	 */
	private static RootNode copyRootNode(final RootNode node) {
		assert node != null : "Expected a non-null node but was null";
		final RootNode root = entityFactory.createRootNode();

		root.setArtifact(node.getArtifact());
		root.setAtomic(node.isAtomic());
		root.setParent(node.getParent());
		root.setSequenceNumber(node.getSequenceNumber());
		root.getAllChildren().addAll(node.getAllChildren());
		root.getUniqueChildren().addAll(node.getUniqueChildren());
		root.setContainingAssociation(node.getContainingAssociation());

		return root;
	}

	/**
	 * Intersects a atomic node.
	 *
	 * @param left
	 *            to intersect
	 * @param right
	 *            to intersect
	 * @return The intersected node.
	 */
	private static Node intersectAtomic(Node left, Node right) {
		Node intersectionChild;
		intersectionChild = copyWithoutTree(left);
		right.getArtifact().putProperty(PROPERTY_REPLACING_ARTIFACT, left.getArtifact());
		intersectionChild.setSequenceNumber(left.getSequenceNumber());
		return intersectionChild;
	}

	/**
	 * Intersects the child nodes from <code>a</code> and <code>b</code>.
	 *
	 * @param left
	 *            which children are intersected
	 * @param right
	 *            which children are intersected
	 * @return A new node containing the intersected children.
	 */
	private static Node intersectChildren(final Node left, final Node right) {
		assert left != null : "Expected non-null <a> but was null";
		assert right != null : "Expected non-null <b> but was null";

		final Node intersection = copyWithoutTree(left);

		for (int li = 0, ri; li < left.getAllChildren().size(); li++) {
			Node leftChild = left.getAllChildren().get(li);

			// not in both
			if ((ri = right.getAllChildren().indexOf(leftChild)) == -1)
				continue;

			Node rightChild = right.getAllChildren().get(ri);

			Node intersectionChild;
			if (leftChild.isAtomic()) {
				intersectionChild = intersectAtomic(leftChild, rightChild);
				intersectionChild.setParent(intersection);
			} else {
				final Intersection<Node> result = (leftChild instanceof OrderedNode) && (rightChild instanceof OrderedNode)
						? intersectOrderedNode((OrderedNode) leftChild, (OrderedNode) rightChild) : intersect(leftChild, rightChild);

				intersectionChild = result.intersection;
				leftChild = result.left;
				rightChild = result.right;
				left.getAllChildren().set(li, leftChild);
				right.getAllChildren().set(ri, rightChild);

				leftChild.setParent(left);
				rightChild.setParent(right);
				intersectionChild.setParent(intersection);

				intersectionChild.setSequenceNumber(result.left.getSequenceNumber());
			}

			if (isUniqueIntersection(left, right, intersectionChild)) {
				addUniqueChildIntersection(intersection, intersectionChild);
			} else if (!intersectionChild.getAllChildren().isEmpty()) {
				addSharedChildIntersection(intersection, intersectionChild);
			}

			removeFromParent(left, right, leftChild, intersectionChild, rightChild);
		}

		cleanupUniqueSets(left, right, intersection);

		return intersection;
	}

	/**
	 * Intersects the given ordered nodes.
	 *
	 * @param a
	 *            that is intersected
	 * @param b
	 *            that is intersected
	 * @return The intersection result containing <code>a</code> and <code>b</code> remainder and the intersection node.
	 */
	private static Intersection<Node> intersectOrderedNode(final OrderedNode a, final OrderedNode b) {
		assert a != null : "Expected non-null <a> but was null";
		assert b != null : "Expected non-null <b> but was null";
		assert a.getArtifact().equals(b.getArtifact()) : "Expected that the artifact from <a> and <b> are equal but was not.";
		assert!(a.isSequenced() && b.isSequenced())
				|| a.getSequenceGraph() == b.getSequenceGraph() : "Expected that if both are sequenced that the corresponding graphs are equal.";

		final OrderedNode aCopy = copyOrderedNode(a), bCopy = copyOrderedNode(b);

		sequenceForIntersection(aCopy, bCopy);

		final Intersection<Node> intersectionResult = intersect(aCopy, bCopy);

		final OrderedNode intersection = (OrderedNode) intersectionResult.intersection;
		intersection.setSequenced(true);
		intersection.setSequenceGraph(aCopy.getSequenceGraph());

		return intersectionResult;
	}

	private static boolean isUniqueIntersection(final Node left, final Node right, Node intersectionChild) {
		return left.getUniqueChildren().contains(intersectionChild) && right.getUniqueChildren().contains(intersectionChild);
	}

	/**
	 * Removes the left/right child from its parent if they are not needed anymore.
	 *
	 * @param left
	 *            parent of the leftChild
	 * @param right
	 *            parent of the rightChild
	 * @param leftChild
	 *            child of the left
	 * @param intersectionChild
	 *            intersection of the left and right child
	 * @param rightChild
	 *            child of the right
	 */
	private static void removeFromParent(final Node left, final Node right, final Node leftChild,
			Node intersectionChild, final Node rightChild) {
		assert left != null : "Expected non-null <left> but was null.";
		assert right != null : "Expected non-null <right> but was null.";
		assert leftChild != null : "Expected non-null <leftChild> but was null.";
		assert rightChild != null : "Expected non-null <rightChild> but was null.";
		assert intersectionChild != null : "Expected non-null <intersectionChild> but was null.";

		if (leftChild.getAllChildren().size() == 0 || leftChild.isAtomic()) {
			if (!leftChild.isAtomic())
				leftChild.setParent(null);
			left.getAllChildren().remove(leftChild);
		}

		if ((isUniqueIntersection(left, right, intersectionChild) || !right.getUniqueChildren().contains(rightChild))
				&& (rightChild.getAllChildren().size() == 0 || rightChild.isAtomic())) {
			if (!rightChild.isAtomic())
				rightChild.setParent(null);
			right.getAllChildren().remove(rightChild);
		}
	}

	/**
	 * Sequences the given ordered nodes for the intersection.
	 *
	 * @param a
	 *            that should be sequenced
	 * @param b
	 *            that should be sequenced
	 */
	private static void sequenceForIntersection(final OrderedNode a, final OrderedNode b) {
		assert a != null : "Expected non-null <a> but was null";
		assert b != null : "Expected non-null <b> but was null";

		// Sequence <a> with initial empty graph
		if (!a.isSequenced() && !b.isSequenced()) {
			a.sequence();
		}
		if (a.isSequenced() && !b.isSequenced()) {
			a.getSequenceGraph().sequence(b);
		} else if (!a.isSequenced() && b.isSequenced()) {
			b.getSequenceGraph().sequence(a);
		}

		a.getOrderedChildren().clear();
		b.getOrderedChildren().clear();
	}
}
