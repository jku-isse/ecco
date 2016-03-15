package at.jku.isse.ecco.util;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This static class provides a collection of tree utility functions.
 */
public class Trees {

	private Trees() {
	}


	// # WRITE OPERATIONS ##################################################################################


	/**
	 * Slices (i.e. intersects) the two given nodes. It returns the intersection node and removes the intersection from the left and right nodes.
	 *
	 * @param left  The left (original) node.
	 * @param right The right (new) node.
	 * @return The created intersection node.
	 * @throws EccoException
	 */
	//public static <T extends Node> T slice(T left, T right) throws EccoException {
	public static Node slice(Node left, Node right) throws EccoException {
		if (!left.equals(right))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getSequenceGraph() != right.getArtifact().getSequenceGraph()) {
					throw new EccoException("Sequence Graphs did not match!");
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getSequenceGraph().sequence(left);
					//SequenceGraphUtil.sequence(left.getArtifact().getSequenceGraph(), left);
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().getSequenceGraph().sequence(right);
					//SequenceGraphUtil.sequence(left.getArtifact().getSequenceGraph(), right);
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					right.getArtifact().getSequenceGraph().sequence(left);
					//SequenceGraphUtil.sequence(right.getArtifact().getSequenceGraph(), left);
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

			Node intersectionChild = slice(leftChild, rightChild);

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


	/**
	 * Merges the right node into the left node. The right node is modified!
	 *
	 * @param left  The left node to which is added.
	 * @param right The right node which is added.
	 */
	public static void merge(Node left, Node right) {
		// do some basic checks
		if (left.getArtifact() != right.getArtifact())
			throw new EccoException("Artifact instance must be identical.");

		// deal with current node
		if (right.isUnique()) {
			left.setUnique(true); // TODO: the "unique" field is redundant. we could determine uniqueness via the artifact's containing node (i.e. whether node and containing node are identical).
			left.getArtifact().setContainingNode(left);
		}

		// deal with children
		Iterator<Node> iterator = right.getChildren().iterator();
		while (iterator.hasNext()) {
			Node rightChild = iterator.next();
			int li = left.getChildren().indexOf(rightChild);
			if (li != -1) {
				Node leftChild = left.getChildren().get(li);

				merge(leftChild, rightChild);

				// detatch right child from right node. this should not be necessary, but to be safe we clean up here.
				iterator.remove();
				rightChild.setParent(null);
			} else {
				left.addChild(rightChild);
			}
		}
	}


	/**
	 * Sequences all ordered nodes in the tree rooted at the given node.
	 *
	 * @param node The root of the tree.
	 */
	public static void sequence(Node node) throws EccoException {
		if (node.getArtifact() != null && node.getArtifact().isOrdered() && !node.getArtifact().isSequenced()) {
			node.getArtifact().setSequenceGraph(node.getArtifact().createSequenceGraph());
			node.getArtifact().getSequenceGraph().sequence(node);
			//SequenceGraphUtil.sequence(node.getArtifact().getSequenceGraph(), node);
		}
		for (Node child : node.getChildren()) {
			sequence(child);
		}
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
	 * Extracts nodes containing marked artifacts into a new tree.
	 * This removes nodes from the left tree and adds them to the right (new) tree.
	 * The mark is removed from artifacts that have been extracted.
	 *
	 * @param left The root of the artifact tree.
	 * @return The root of the new tree.
	 */
	public static Node extractMarked(Node left) {
		Node right = extractMarkedRec(left);
		return right;
	}

	private static Node extractMarkedRec(Node left) {

		// create right node
		Node right = left.createNode();
		right.setArtifact(left.getArtifact());

		// process children
		Iterator<Node> iterator = left.getChildren().iterator();
		while (iterator.hasNext()) {
			Node leftChild = iterator.next();
			Node rightChild = extractMarkedRec(leftChild);
			if (rightChild != null) { // add to right tree
				right.addChild(rightChild);
			}

			if (!leftChild.isUnique() && leftChild.getChildren().isEmpty()) { // potentially trim left tree
				iterator.remove();
			}
		}

		// deal with current node
		if (left.isUnique() && left.getArtifact() != null && left.getArtifact().getProperty(Artifact.MARKED_FOR_EXTRACTION).isPresent()) { // the node itself is unique/marked
			// deal with left node
			left.setUnique(false);

			// deal with right node
			right.setUnique(true);
			right.getArtifact().setContainingNode(right);

			// remove mark
			right.getArtifact().removeProperty(Artifact.MARKED_FOR_EXTRACTION);

			return right;
		} else if (!right.getChildren().isEmpty()) { // there are unique/marked successors
			return right;
		} else { // neither the node itself nor any of its successors are unique/marked
			return null;
		}
	}


	// # READ ONLY OPERATIONS ##################################################################################


	/**
	 * Counts the number of artifacts (i.e. unique nodes) that are contained in the given tree.
	 *
	 * @param node The root of the artifact tree.
	 * @return The number of artifacts in the given association.
	 */
	public static int countArtifacts(Node node) {
		return countArtifactsInAssociationRec(node, 0);
	}

	private static int countArtifactsInAssociationRec(Node node, int currentCount) {
		if (node.getArtifact() != null && node.isUnique()) {
			currentCount++;
		}
		for (Node child : node.getChildren()) {
			currentCount = countArtifactsInAssociationRec(child, currentCount);
		}
		return currentCount;
	}


	/**
	 * Computes the number of artifacts contained in the tree grouped by depth.
	 *
	 * @param node The root of the artifact tree.
	 * @return A map with the depth as key and the number of artifacts as value.
	 */
	public static Map<Integer, Integer> countArtifactsPerDepth(Node node) {
		Map<Integer, Integer> artifactsPerDepth = new HashMap<>();
		computeArtifactsPerDepthRec(node, artifactsPerDepth, 0);

		return artifactsPerDepth;
	}

	private static void computeArtifactsPerDepthRec(Node node, Map<Integer, Integer> map, int depth) {
		if (node.getArtifact() != null && node.isUnique()) {
			Integer count = map.get(depth);
			if (count == null) {
				map.put(depth, 1);
			} else {
				map.put(depth, count + 1);
			}
		}
		for (Node child : node.getChildren()) {
			computeArtifactsPerDepthRec(child, map, depth + 1);
		}
	}


	/**
	 * Retreives the ID of the plugin that created the given node. If the node was created by an artifact plugin the plugin's ID is returned. If the node was not creaetd by a plugin, as is for example the case with directories, null is returned.
	 *
	 * @param node The node for which the plugin ID shall be retreived.
	 * @return The plugin ID of the given node or null if the node was not created by a plugin.
	 */
	public static String getPluginId(Node node) {
		if (node == null || node.getArtifact() == null)
			return null;
		else {
			if (node.getArtifact().getData() instanceof PluginArtifactData)
				return ((PluginArtifactData) node.getArtifact().getData()).getPluginId();
			else {
				return getPluginId(node.getParent());
			}
		}
	}


	/**
	 * Prints the tree to the standard output.
	 *
	 * @param node The root of the tree.
	 */
	public static void print(Node node) {
		printRec(node, "");
	}

	private static void printRec(Node node, String indent) {
		System.out.println(indent + node.toString());
		String newIndent = indent + " ";
		for (Node child : node.getChildren()) {
			printRec(child, newIndent);
		}
	}


	/**
	 * Checks the state of the artifact tree for inconsistencies.
	 *
	 * @param node The root of the artifact tree.
	 */
	public static void checkConsistency(Node node) {
		checkUses(node);
		hasNoReplacingArtifact(node);
		hasParent(node);
		isNotRootNode(node);
		hasArtifact(node);
		isNotUniqueAndArtifactDoesNotReferenceNode(node);
		isUniqueAndArtifactReferencesNode(node);
		parentHasNodeAsChild(node);

		for (Node child : node.getChildren()) {
			checkConsistency(child);
		}
	}

	private static void checkUses(Node node) {
		if (node.getArtifact() != null) {
			for (ArtifactReference ref : node.getArtifact().getUses()) {
				referenceHasNoReplacingArtifact(ref);

				Node targetParent = ref.getTarget().getContainingNode();
				if (targetParent.getArtifact() != ref.getTarget()) {
					throw new IllegalStateException("Expected that the target artifact has the target as containing node.");
				}

				if (targetParent.getParent() == null) {
					throw new IllegalStateException("Expected a non-null parent");
				} else if (!targetParent.getParent().getChildren().contains(targetParent)) {
					throw new IllegalStateException("Expected that the target parent contains the target as child.");
				}
			}
		}
	}

	private static void hasNoReplacingArtifact(Node node) {
		if (!(node instanceof RootNode) && node.getArtifact().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
			throw new IllegalStateException("Expected that there are no artifacts to be replaced.");
		}
	}

	private static void hasParent(Node node) {
		if (!(node instanceof RootNode) && node.getParent() == null) {
			throw new IllegalStateException("Expected a parent since the node is not a root node.");
		}
	}

	private static void isNotRootNode(Node node) {
		if (node instanceof RootNode && node.getParent() != null) {
			throw new IllegalStateException("Expected a non-root node since we are in the middle of the tree.");
		}
	}

	private static void hasArtifact(Node node) {
		if (!(node instanceof RootNode) && node.getArtifact() == null) {
			throw new IllegalStateException("Expected an artifact since the node is not a root node.");
		}
	}

	private static void isNotUniqueAndArtifactDoesNotReferenceNode(Node node) {
		if (!(node instanceof RootNode) && !node.isUnique() && node.getArtifact().getContainingNode() == node) {
			throw new IllegalStateException("Expected a shared node where the artifacts containing node is not the shared node.");
		}
	}

	private static void isRootNode(Node rootNode) {
		if (!(rootNode instanceof RootNode)) {
			throw new IllegalStateException("Expected a root node.");
		}
	}

	private static void isUniqueAndArtifactReferencesNode(Node node) {
		if (!(node instanceof RootNode) && node.isUnique() && node.getArtifact().getContainingNode() != node) {
			throw new IllegalStateException("Expected a unique node where the artifacts containing node is the unique node.");
		}
	}

	private static void parentHasNodeAsChild(Node node) {
		if (node.getParent() != null && !node.getParent().getChildren().contains(node)) {
			throw new IllegalStateException("Expected that the parent contains the node as child.");
		}
	}

	private static void referenceHasNoReplacingArtifact(ArtifactReference reference) {
		if (reference.getTarget().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
			throw new IllegalStateException("Expected that the referenced target has no replacing artifact.");
		}
	}


}
