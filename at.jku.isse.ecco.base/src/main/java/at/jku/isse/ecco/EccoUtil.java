package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;

import java.util.Iterator;

public class EccoUtil {


	/**
	 * Retreives the ID of the plugin that created the given node. If the node was created by an artifact plugin the plugin's ID is returned. If the node was not creaetd by a plugin, as is for example the case with directories, null is returned.
	 *
	 * @param node The node for which the plugin ID shall be retreived.
	 * @return The plugin ID of the given node or null if the node was not created by a plugin.
	 */
	public static String getPluginIdForNode(Node node) {
		if (node == null || node.getArtifact() == null)
			return null;
		else {
			if (node.getArtifact().getData() instanceof PluginArtifactData)
				return ((PluginArtifactData) node.getArtifact().getData()).getPluginId();
			else {
				return getPluginIdForNode(node.getParent());
			}
		}
	}


	/**
	 * Counts the number of artifacts (i.e. unique nodes) that are contained in the given association.
	 *
	 * @param association The association whose artifacts shall be counted.
	 * @return The number of artifacts in the given association.
	 */
	public static int countArtifactsInAssociation(Association association) {
		return countArtifactsInAssociationRecursively(association.getArtifactTreeRoot(), 0);
	}

	private static int countArtifactsInAssociationRecursively(Node node, int currentCount) {
		if (node.getArtifact() != null && node.isUnique()) {
			currentCount++;
		}
		for (Node child : node.getAllChildren()) {
			currentCount = countArtifactsInAssociationRecursively(child, currentCount);
		}
		return currentCount;
	}


	/**
	 * Updates uses and usedBy references of artifacts (unique as well as non-unique) contained in the tree rooted at the given node.
	 *
	 * @param node THe root of the tree.
	 */
	public static void updateArtifactReferences(Node node) throws EccoException {
		if (node.getArtifact() != null) {
			if (node.getArtifact().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
				throw new EccoException("Artifact should have been replaced.");
			}

			// update uses
			for (ArtifactReference uses : node.getArtifact().getUses()) {
				Artifact replacingArtifact = uses.getTarget().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
				if (replacingArtifact != null) {
					uses.setTarget(replacingArtifact);
					if (!replacingArtifact.getUsedBy().contains(uses)) {
						replacingArtifact.addUsedBy(uses);
					}
				}
			}

			// update used by
			for (ArtifactReference usedBy : node.getArtifact().getUsedBy()) {
				Artifact replacingArtifact = usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
				if (replacingArtifact != null) {
					usedBy.setSource(replacingArtifact);
				}
				if (!replacingArtifact.getUses().contains(usedBy)) {
					replacingArtifact.addUses(usedBy);
				}
			}
		}

		// traverse into children
		if (node instanceof OrderedNode && !((OrderedNode) node).isSequenced()) {
			for (Node child : ((OrderedNode) node).getOrderedChildren()) {
				updateArtifactReferences(child);
			}
		} else {
			for (Node child : node.getAllChildren()) {
				updateArtifactReferences(child);
			}
		}
	}


	/**
	 * Sequences all ordered nodes in the tree rooted at the given node.
	 *
	 * @param node The root of the tree.
	 */
	public static void sequenceOrderedNodes(Node node) {
		if (node instanceof OrderedNode) {
			OrderedNode onode = (OrderedNode) node;
			if (!onode.isSequenced()) {
				onode.sequence();
			}
		}
		for (Node child : node.getAllChildren()) {
			sequenceOrderedNodes(child);
		}
	}


	/**
	 * Slices (i.e. intersects) the two given nodes. It returns the intersection node and removes the intersection from the left and right nodes.
	 *
	 * @param left  The left (original) node.
	 * @param right The right (new) node.
	 * @return The created intersection node.
	 * @throws EccoException
	 */
	public static Node sliceNodes(Node left, Node right) throws EccoException {
		if (left instanceof OrderedNode && right instanceof OrderedNode) {
			return sliceOrderedNodes((OrderedNode) left, (OrderedNode) right);
		} else if (left instanceof Node && right instanceof Node) {
			return sliceGenericNodes(left, right);
		} else {
			throw new EccoException("Only nodes of the same type can be sliced.");
		}
	}

	private static Node sliceGenericNodes(Node left, Node right) throws EccoException {
		if (!left.equals(right))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");

		if (left.getArtifact() != null && left.getArtifact() != right.getArtifact()) {
			right.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, left.getArtifact());
			right.setArtifact(left.getArtifact());
		}

		if (left.isAtomic()) {
			return left;
		}

		Node intersection = left.createNode();
		intersection.setArtifact(left.getArtifact());
		intersection.setAtomic(left.isAtomic());
		intersection.setSequenceNumber(left.getSequenceNumber());

		Iterator<Node> leftChildrenIterator = left.getAllChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = right.getAllChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node rightChild = right.getAllChildren().get(ri);

			Node intersectionChild = sliceNodes(leftChild, rightChild);

			if (left.getUniqueChildren().contains(intersectionChild) && right.getUniqueChildren().contains(intersectionChild)) {
				intersection.getAllChildren().add(intersectionChild);
				intersection.getUniqueChildren().add(intersectionChild);
				intersectionChild.setParent(intersection);
				if (intersectionChild.getArtifact() != null) {
					intersectionChild.getArtifact().setContainingNode(intersectionChild);
				}
			} else if (intersectionChild != null && (intersectionChild.getAllChildren().size() > 0 || intersectionChild.isAtomic())) {
				intersection.getAllChildren().add(intersectionChild);
				intersectionChild.setParent(intersection);
			}

			if ((leftChild.getAllChildren().size() == 0 || leftChild.isAtomic())) {
				if (!leftChild.isAtomic())
					leftChild.setParent(null);
				leftChildrenIterator.remove();
			}

			if ((intersection.getUniqueChildren().contains(rightChild) || !right.getUniqueChildren().contains(rightChild))
					&& (rightChild.getAllChildren().size() == 0 || rightChild.isAtomic())) {
				if (!rightChild.isAtomic())
					rightChild.setParent(null);
				right.getAllChildren().remove(rightChild);
			}
		}

		left.getUniqueChildren().removeAll(intersection.getUniqueChildren());
		right.getUniqueChildren().removeAll(intersection.getUniqueChildren());

		return intersection;
	}

	private static OrderedNode sliceOrderedNodes(OrderedNode left, OrderedNode right) throws EccoException {
		if (left.isSequenced() && right.isSequenced()) {
			if (left.getSequenceGraph() != right.getSequenceGraph())
				throw new EccoException("Sequence Graphs did not match!");
		} else if (!left.isSequenced() && !right.isSequenced()) {
			left.setSequenceGraph(left.createSequenceGraph());
			left.getSequenceGraph().sequence(left);
		}

		if (left.isSequenced() && !right.isSequenced()) {
			left.getSequenceGraph().sequence(right);
		} else if (!left.isSequenced() && right.isSequenced()) {
			right.getSequenceGraph().sequence(left);
			throw new EccoException("Left node was not sequenced but right node was.");
		}

		left.getOrderedChildren().clear();
		right.getOrderedChildren().clear();

		OrderedNode intersection = (OrderedNode) sliceGenericNodes(left, right);
		intersection.setSequenced(true);
		intersection.setSequenceGraph(left.getSequenceGraph());

		return intersection;
	}


	public static Node mergeTrees(Node left, Node right) {
		return null; // TODO
	}


	public static void printTree(Node node) {
		// TODO
	}


	public static void checkConsistency(Node node) {
		// TODO
	}


}
