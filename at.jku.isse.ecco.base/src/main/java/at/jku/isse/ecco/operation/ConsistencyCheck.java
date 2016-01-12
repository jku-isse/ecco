package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.NodeUtil;

/**
 * Makes a sanity check on a tree checking different child and parent relations etc.
 *
 * @author Hannes Thaller
 * @author JKU, ISSE
 * @version 2.0
 */
public class ConsistencyCheck extends AbstractUnaryTreeOperation<Void> {

	@Override
	protected void prefix(Node node) {
		isNotRootNode(node);
		hasNoReplacingArtifact(node);
		hasUniqueChildrenInAllChildren(node);
		isUniqueAndArtifactReferencesNode(node);
		isNotUniqueAndArtifactDoesNotReferenceNode(node);
		hasParent(node);
		parentHasNodeAsChild(node);
		orderedParentHasNodeAsChild(node);

		Node rootNode = node;
		while (rootNode.getParent() != null && !(rootNode instanceof RootNode)) {
			rootNode = rootNode.getParent();
		}

		isRootNode(rootNode);
		if (!node.getArtifact().getUses().isEmpty()) checkUses(node);
	}

	private void checkUses(Node node) {
		for (ArtifactReference ref : node.getArtifact().getUses()) {
			referenceHasNoReplacingArtifact(ref);

			Node targetParent = ref.getTarget().getContainingNode();
			if (targetParent.getArtifact() != ref.getTarget()) {
				throw new IllegalStateException("Expected that the target artifact has the target as containing node.");
			}

			if (targetParent.getParent() == null) {
				throw new IllegalStateException("Expected a non-null parent");
			} else if (!targetParent.getParent().getAllChildren().contains(targetParent)) {
				if (targetParent.getParent() instanceof OrderedNode
						&& !((OrderedNode) targetParent.getParent()).isSequenced()) {
					if (!((OrderedNode) targetParent.getParent()).getOrderedChildren().contains(targetParent)) {
						throw new IllegalStateException("Expected that the target parent conatins the target as ordered child.");
					}
				} else {
					throw new IllegalStateException("Expected a not sequenced ordered node since the parent does not contain the target as child.");
				}
			} else if (!targetParent.getParent().getUniqueChildren().contains(targetParent)) {
				throw new IllegalStateException("Expected that the target parent contains the target as child.");
			}
		}
	}

	private void hasNoReplacingArtifact(Node node) {
		if (!(node instanceof RootNode)
				&& node.getArtifact().getProperty(NodeUtil.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
			throw new IllegalStateException("Expected that there are no replacing artifacts.");
		}
	}

	private void hasParent(Node node) {
		if (node.getParent() == null) {
			throw new IllegalStateException("Expected a parent since the node is not a root node.");
		}
	}

	private void hasUniqueChildrenInAllChildren(Node node) {
		if (!node.getAllChildren().containsAll(node.getUniqueChildren())) {
			throw new IllegalStateException("Expected that all unique children are in the all children.");
		}
	}

	private void isNotRootNode(Node node) {
		if (node instanceof RootNode) {
			throw new IllegalStateException("Expected a non-root node since we are in the middle of the tree.");
		}
	}

	private void isNotUniqueAndArtifactDoesNotReferenceNode(Node node) {
		if (!(node instanceof RootNode) && !node.isUnique() && node.getArtifact().getContainingNode() == node) {
			throw new IllegalStateException("Expected a shared node where the artifacts containing node is not the shared node.");
		}
	}

	private void isRootNode(Node rootNode) {
		if (!(rootNode instanceof RootNode)) {
			throw new IllegalStateException("Expected a root node.");
		}
	}

	private void isUniqueAndArtifactReferencesNode(Node node) {
		if (!(node instanceof RootNode) && node.isUnique() && node.getArtifact().getContainingNode() != node) {
			throw new IllegalStateException("Expected a unique node where the artifacts containing node is the unique node.");
		}
	}

	private void orderedParentHasNodeAsChild(Node node) {
		if (node.getParent() instanceof OrderedNode && !(((OrderedNode) node.getParent()).isSequenced())
				&& !((OrderedNode) node.getParent()).getOrderedChildren().contains(node)) {
			throw new IllegalStateException("Expected that the parent contains the node int he ordered children.");
		}
	}

	private void parentHasNodeAsChild(Node node) {
		if ((!(node.getParent() instanceof OrderedNode) || (((OrderedNode) node.getParent()).isSequenced())) && !node
				.getParent().getAllChildren().contains(node)) {
			throw new IllegalStateException("Expected that the parent contains the node as child.");
		}
	}

	private void referenceHasNoReplacingArtifact(ArtifactReference reference) {
		if (reference.getTarget().getProperty(NodeUtil.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
			throw new IllegalStateException("Expected that the referenced target has no replacing artifact.");
		}
	}

}
