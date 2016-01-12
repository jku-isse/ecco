package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.NodeUtil;

import java.util.List;

/**
 * Merges the given trees with the containing trees. The first call of apply
 * will only copy the structure if the first tree as it is merged with an empty
 * tree. Each consecutive call will be merged with the current tree stored in
 * the operation.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class Merge extends AbstractUnaryTreeOperation<Node> {

	private Node currentNode;

	@Override
	protected void beforeTraversal(final Node root) {
		if (currentNode == null) currentNode = NodeUtil.copyWithoutTree(root);
		if (currentNode instanceof RootNode) ((RootNode) currentNode).setContainingAssociation(null);
	}

	@Override
	protected void finalizeResult() {
		result = currentNode;
	}

	@Override
	protected void prefix(final Node node) {
		assert node != null;

		final List<Node> currentChildren = currentNode.getAllChildren();
		if (!currentChildren.contains(node)) {
			final Node mergeNode = NodeUtil.copyWithoutTree(node);
			currentChildren.add(mergeNode);

			if (node.isUnique()) {
				currentNode.getUniqueChildren().add(mergeNode);
			}

			mergeNode.setParent(currentNode);

			currentNode = mergeNode;
		} else {
			currentNode = currentChildren.get(currentChildren.indexOf(node));
		}
	}

	@Override
	protected void postfix(final Node node) {
		assert currentNode.equals(node) : "Expected the given node to be the current node but was not.";
		currentNode = currentNode.getParent();
	}

}
