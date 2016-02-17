package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.SequenceGraphUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
		for (Node child : node.getChildren()) {
			currentCount = countArtifactsInAssociationRecursively(child, currentCount);
		}
		return currentCount;
	}


	/**
	 * Updates uses and usedBy references of artifacts (unique as well as non-unique) contained in the tree rooted at the given node.
	 *
	 * @param node The root of the tree.
	 */
	public static void updateArtifactReferences(Node node) throws EccoException {
		if (node.getArtifact() != null) {
			if (node.getArtifact().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
				throw new EccoException("Artifact should have been replaced.");
			}

			// update uses
			for (ArtifactReference uses : node.getArtifact().getUses()) {
				if (uses.getTarget().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact replacingArtifact = uses.getTarget().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					if (replacingArtifact != null) {
						uses.setTarget(replacingArtifact);
						if (!replacingArtifact.getUsedBy().contains(uses)) {
							replacingArtifact.addUsedBy(uses);
						}
					}
				}
			}

			// update used by
			for (ArtifactReference usedBy : node.getArtifact().getUsedBy()) {
				if (usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact replacingArtifact = usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					if (replacingArtifact != null) {
						usedBy.setSource(replacingArtifact);
					}
					if (!replacingArtifact.getUses().contains(usedBy)) {
						replacingArtifact.addUses(usedBy);
					}
				}
			}
		}

		// traverse into children
		for (Node child : node.getChildren()) {
			updateArtifactReferences(child);
		}
	}


	/**
	 * Sequences all ordered nodes in the tree rooted at the given node.
	 *
	 * @param node The root of the tree.
	 */
	public static void sequenceOrderedNodes(Node node) throws EccoException {
		if (node.getArtifact() != null && node.getArtifact().isOrdered() && !node.getArtifact().isSequenced()) {
			node.getArtifact().setSequenceGraph(node.getArtifact().createSequenceGraph());
			//node.getArtifact().getSequenceGraph().sequence(node);
			SequenceGraphUtil.sequence(node.getArtifact().getSequenceGraph(), node);
		}
		for (Node child : node.getChildren()) {
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
		if (!left.equals(right))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getSequenceGraph() != right.getArtifact().getSequenceGraph()) {
					throw new EccoException("Sequence Graphs did not match!");
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					//left.getArtifact().getSequenceGraph().sequence(left);
					SequenceGraphUtil.sequence(left.getArtifact().getSequenceGraph(), left);
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					//left.getArtifact().getSequenceGraph().sequence(right);
					SequenceGraphUtil.sequence(left.getArtifact().getSequenceGraph(), right);
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					//right.getArtifact().getSequenceGraph().sequence(left);
					SequenceGraphUtil.sequence(right.getArtifact().getSequenceGraph(), left);
					throw new EccoException("Left node was not sequenced but right node was.");
				}
			}


			if (left.getArtifact() != right.getArtifact()) {
				right.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, left.getArtifact());
				right.setArtifact(left.getArtifact());
			}

			if (left.getArtifact().isAtomic()) {
				return left;
			}
		}


		Node intersection = left.createNode();
		intersection.setArtifact(left.getArtifact());
		if (left.isUnique() && right.isUnique()) {
			intersection.setUnique(true);
			left.setUnique(false);
			right.setUnique(false);

			if (intersection.getArtifact() != null)
				intersection.getArtifact().setContainingNode(intersection);
		}


		Iterator<Node> leftChildrenIterator = left.getChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node rightChild = right.getChildren().get(ri);

			Node intersectionChild = sliceNodes(leftChild, rightChild);

			if (intersectionChild != null && (intersectionChild.isUnique() || intersectionChild.getChildren().size() > 0 || intersectionChild.isAtomic())) {
				intersection.addChild(intersectionChild);
			}

			if (!leftChild.isUnique() && (leftChild.getChildren().size() == 0 || leftChild.isAtomic())) {
				if (!leftChild.isAtomic())
					leftChild.setParent(null);
				leftChildrenIterator.remove();
			}

			if (!rightChild.isUnique() && (rightChild.getChildren().size() == 0 || rightChild.isAtomic())) {
				if (!rightChild.isAtomic())
					rightChild.setParent(null);
				right.getChildren().remove(rightChild);
			}
		}


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


	public static Map<Integer, Integer> computeArtifactsPerDepth(Node node) {
		Map<Integer, Integer> artifactsPerDepth = new HashMap<>();
		computeArtifactsPerDepthRecursion(node, artifactsPerDepth, 0);

		return artifactsPerDepth;
	}

	private static void computeArtifactsPerDepthRecursion(Node node, Map<Integer, Integer> map, int depth) {
		if (node.getArtifact() != null && node.isUnique()) {
			Integer count = map.get(depth);
			if (count == null) {
				map.put(depth, 1);
			} else {
				map.put(depth, count + 1);
			}
		}
		for (Node child : node.getChildren()) {
			computeArtifactsPerDepthRecursion(child, map, depth + 1);
		}
	}


	/**
	 * Adds the right node to the left node. The right node remains unmodified.
	 *
	 * @param left  The left node to which is added.
	 * @param right The right node which is assed.
	 */
	public static void addNodes(Node left, Node right) {

	}


}
