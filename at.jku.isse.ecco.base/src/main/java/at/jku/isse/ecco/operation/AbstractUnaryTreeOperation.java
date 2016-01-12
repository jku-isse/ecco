package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.NodeUtil;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implements an abstract {@link UnaryTreeOperation} by providing hooks before and
 * after a node and before returning the result.
 * <p>
 * The template contains the following hooks and fields:
 * <li>Result - The result returned and which is accessible for real implementations.</li>
 * <li>preFix - {@link #prefix(Node)} which is executed before each node. </li>
 * <li>postFix - {@link #postfix(Node)} which is executed after the nodes children have been visited.</li>
 * <li>beforeTraversal - {@link #beforeTraversal(Node)} which is executed just before the actual traversal.</li>
 * <li>finalizeResult - {@link #finalizeResult()}} which is executed just before returning the result.</li>
 * <p>
 * Furthermore it provides the actual traverse method which implements the tree traversal.
 * If the {@link #apply}-method is a call to {@link #traverse} must be included such that the tree is actually
 * traversed.
 * <p>
 * The traversal is a iterative depth first traversal with prefix and postfix
 * hooks without using a stack, thus needs a O(1) additional memory during
 * computation.
 *
 * @param <T> The type of the result return from the operation.
 * @author Hannes Thaller
 * @version 1.0
 */
public abstract class AbstractUnaryTreeOperation<T> implements UnaryTreeOperation<T> {

	protected T result;

	@Override
	public T apply(final Node root) {
		checkNotNull(root);

		beforeTraversal(root);
		traverse(root);
		finalizeResult();

		return result;
	}

	/**
	 * The hook that is executed just before the traversal begins allowing certain initializations.
	 *
	 * @param root of the tree which is traversed
	 */
	protected void beforeTraversal(final Node root) {
	}

	/**
	 * The hook for finalizing the end result. The function may be used if the
	 * computation itself needs different fields and types and somehow needs to
	 * be aggregated at the end.
	 */
	protected void finalizeResult() {
	}

	/**
	 * The hook operation that is executed after visiting all the child nodes
	 * and on exiting the subtree with the given parameter as node.
	 *
	 * @param node The root node of the subtree we currently leaving.
	 */
	protected void postfix(final Node node) {
	}

	/**
	 * The hook operation that is executed before visiting the given node.
	 *
	 * @param node The node that is currently visited.
	 */
	protected void prefix(final Node node) {
	}

	/**
	 * Executes a depth first traversal from the given root and executes the
	 * hooks during the traversal.
	 *
	 * @param root from which the traversal should start.
	 */
	protected void traverse(final Node root) {
		assert root != null;

		Node currentNode = getFirstChild(root);

		while (currentNode != null) {

			prefix(currentNode);

			if (hasChildNodes(currentNode)) { // descent
				currentNode = getFirstChild(currentNode);
			} else { // ascent
				postfix(currentNode);
				currentNode = getNextNode(root, currentNode);
			}
		}
	}

	/**
	 * Returns the first child of the node.
	 *
	 * @param node the parent of the first child
	 * @return The first child of the node.
	 */
	private Node getFirstChild(final Node node) {
		assert node != null;

		List<Node> children = NodeUtil.getAllChildren(node);

		return children.isEmpty() ? null : children.get(0);
	}

	/**
	 * Returns the next adult which is available. If the adults are unsorted the
	 * function always uses the same method to retrieve them thus inserting new
	 * elements in between two calls may alter the result.
	 *
	 * @param root  the root of the entire tree needed to find the end if reached
	 * @param child the child from which the upward search begins
	 * @return The next adult or null if only the root node is left.
	 */
	private Node getNextNode(final Node root, final Node child) {
		assert root != null;
		assert child != null;

		Node currentNode = child;
		Node nextSibling = getNextSibling(currentNode);

		while (nextSibling == null) {
			currentNode = currentNode.getParent();

			if (currentNode.equals(root)) {
				nextSibling = null;
				break;
			} else {
				nextSibling = getNextSibling(currentNode);
				postfix(currentNode);
			}
		}

		return nextSibling;
	}

	/**
	 * Returns the next sibling or null if there is no sibling left. If the
	 * siblings are unsorted the function always uses the same method to
	 * retrieve them thus inserting new elements in between two calls may alter
	 * the result.
	 *
	 * @param node from which the next sibling should be returned
	 * @return The next sibling of the node.
	 */
	private Node getNextSibling(final Node node) {
		assert node != null;
		assert NodeUtil.getAllChildren(node.getParent()).contains(node) : "The artifact tree is inconsistent since the parent does not contain the node!";

		final List<Node> siblings = new ArrayList<>(NodeUtil.getAllChildren(node.getParent()));

		final int nodeIndex = siblings.indexOf(node);
		return nodeIndex + 1 < siblings.size() ? siblings.get(nodeIndex + 1) : null;
	}

	/**
	 * Returns whether the given node has children.
	 *
	 * @param node that may have children
	 * @return True if the node has children, false if not.
	 */
	private boolean hasChildNodes(final Node node) {
		assert node != null;

		return !NodeUtil.getAllChildren(node).isEmpty();
	}

}
