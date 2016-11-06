package at.jku.isse.ecco.util;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This static class provides a collection of tree utility functions.
 */
public class Trees {

	private Trees() {
	}


	// # COPY OPERATION ##################################################################################

	/**
	 * Creates a shallow copy of the tree.
	 *
	 * @param node The root node of the tree to copy.
	 */
	public void copy(Node node) {

	}


	// # WRITE OPERATIONS ##############################################################################################

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
					//throw new EccoException("Sequence Graphs did not match!");
					left.getArtifact().getSequenceGraph().sequence(right.getArtifact().getSequenceGraph());
					right.getArtifact().setSequenceGraph(left.getArtifact().getSequenceGraph());
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getSequenceGraph().sequence(left);
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().getSequenceGraph().sequence(right);
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					right.getArtifact().getSequenceGraph().sequence(left);
					left.getArtifact().setSequenceGraph(right.getArtifact().getSequenceGraph());
					throw new EccoException("Left node was not sequenced but right node was!");
				}
			}


			if (left.getArtifact().isAtomic()) {
				Trees.matchAtomicArtifacts(left, right);
				return left;
			} else if (left.getArtifact() != right.getArtifact()) {
				right.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, left.getArtifact());

				// merge artifact references
				for (ArtifactReference ar : right.getArtifact().getUses()) {
					if (!left.getArtifact().getUses().contains(ar)) {
						left.getArtifact().addUses(ar);
						ar.setSource(left.getArtifact());
					}
				}
				for (ArtifactReference ar : right.getArtifact().getUsedBy()) {
					if (!left.getArtifact().getUsedBy().contains(ar)) {
						left.getArtifact().addUsedBy(ar);
						ar.setTarget(left.getArtifact());
					}
				}

				right.setArtifact(left.getArtifact());
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
		} else {
			intersection.setUnique(false);
		}

//		if (intersection.getArtifact() != null && intersection.getArtifact().isAtomic()) {
//			return intersection;
//		}


		Iterator<Node> leftChildrenIterator = left.getChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node rightChild = right.getChildren().get(ri);

			Node intersectionChild = slice(leftChild, rightChild);

			if (intersectionChild != null && (intersectionChild.isUnique() || (!intersectionChild.getChildren().isEmpty() && !intersectionChild.isAtomic()))) {
				intersection.addChild(intersectionChild);
			}

			if (intersectionChild != null && intersectionChild.isAtomic()) { // left child becomes the intersection child
				intersectionChild.setParent(intersection);

				rightChild.setParent(null);

				leftChildrenIterator.remove();
				right.getChildren().remove(rightChild);
			} else {
				if (!leftChild.isUnique() && leftChild.getChildren().isEmpty()) {
					leftChild.setParent(null);
					leftChildrenIterator.remove();
				}

				if (!rightChild.isUnique() && rightChild.getChildren().isEmpty()) {
					rightChild.setParent(null);
					right.getChildren().remove(rightChild);
				}
			}
		}


		return intersection;
	}

	private static void matchAtomicArtifacts(Node left, Node right) {
		right.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, left.getArtifact());

		// merge artifact references
		for (ArtifactReference ar : right.getArtifact().getUses()) {
			if (!left.getArtifact().getUses().contains(ar)) {
				left.getArtifact().addUses(ar);
				ar.setSource(left.getArtifact());
			}
		}
		for (ArtifactReference ar : right.getArtifact().getUsedBy()) {
			if (!left.getArtifact().getUsedBy().contains(ar)) {
				left.getArtifact().addUsedBy(ar);
				ar.setTarget(left.getArtifact());
			}
		}

		right.setArtifact(left.getArtifact());

		if (left.getChildren().size() != right.getChildren().size()) {
			throw new EccoException("Equal atomic nodes must have identical children!");
		}

		for (Node leftChild : left.getChildren()) {
			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1) {
				throw new EccoException("Equal atomic nodes must have identical children!");
				//continue;
			}

			Node rightChild = right.getChildren().get(ri);

			Trees.matchAtomicArtifacts(leftChild, rightChild);
		}
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

			// update "uses" artifact references
			for (ArtifactReference uses : node.getArtifact().getUses()) {
				if (uses.getSource() != node.getArtifact())
					throw new EccoException("Source of uses artifact reference must be identical to artifact.");

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

			// update "used by" artifact references
			for (ArtifactReference usedBy : node.getArtifact().getUsedBy()) {
				if (usedBy.getTarget() != node.getArtifact())
					throw new EccoException("Target of usedBy artifact reference must be identical to artifact.");

				if (usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact replacingArtifact = usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					if (replacingArtifact != null) {
						usedBy.setSource(replacingArtifact);
						if (!replacingArtifact.getUses().contains(usedBy)) {
							replacingArtifact.addUses(usedBy);
						}
					}
				}
			}

			// update sequence graph symbols (which are artifacts)
			if (node.getArtifact().getSequenceGraph() != null) {
				node.getArtifact().getSequenceGraph().updateArtifactReferences();
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
		if (left.isUnique() && left.getArtifact() != null && left.getArtifact().getProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION).isPresent()) { // the node itself is unique/marked
			// deal with left node
			left.setUnique(false);

			// deal with right node
			right.setUnique(true);
			right.getArtifact().setContainingNode(right);

			// remove mark
			right.getArtifact().removeProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION);

			return right;
		} else if (!right.getChildren().isEmpty()) { // there are unique/marked successors
			right.setUnique(false);
			return right;
		} else { // neither the node itself nor any of its successors are unique/marked
			return null;
		}
	}


	/**
	 * Subtracts the right tree from the left tree. The right tree is not modified.
	 *
	 * @param left  The left tree to be subtracted from, which is modified.
	 * @param right The right tree to subtract, which is not modified.
	 */
	public void subtract(Node left, Node right) {
		// do some basic checks
		if (left.getArtifact() != null && !left.getArtifact().equals(right.getArtifact()))
			throw new EccoException("Artifacts must be equal.");

		// deal with current node
		if (right.isUnique())
			left.setUnique(false);

		// deal with children
		Iterator<Node> iterator = left.getChildren().iterator();
		while (iterator.hasNext()) {
			Node leftChild = iterator.next();
			int ri = right.getChildren().indexOf(leftChild);
			if (ri != -1) {
				Node rightChild = right.getChildren().get(ri);

				subtract(leftChild, rightChild);

				if (!leftChild.isUnique() && leftChild.getChildren().isEmpty())
					iterator.remove();
			}
		}
	}


	// # READ ONLY OPERATIONS ##################################################################################


	/**
	 * Maps artifacts in tree rooted at right to artifacts in tree rooted at left. Does not merge or update artifact references. The left tree is not modified.
	 * Mapped artifacts can be found in the property {@link Artifact#PROPERTY_MAPPED_ARTIFACT} of the right artifacts.
	 *
	 * @param left  Root node of the first tree.
	 * @param right Root node of the second tree.
	 */
	public static void map(Node left, Node right) {
		if (!left.equals(right))
			throw new EccoException("Mapping of non-equal nodes is not allowed!");


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getSequenceGraph() != right.getArtifact().getSequenceGraph()) {
					throw new EccoException("Sequence Graphs did not match!");
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getSequenceGraph().sequence(left);
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					List<Artifact<?>> rightArtifacts = right.getChildren().stream().map((Node n) -> n.getArtifact()).collect(Collectors.toList());
					left.getArtifact().getSequenceGraph().align(rightArtifacts);
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					throw new EccoException("Left node was not sequenced but right node was!");
				}
			}


			if (left.getArtifact().isAtomic()) {
				Trees.mapAtomicArtifacts(left, right);
			} else if (left.getArtifact() != right.getArtifact()) {
				right.getArtifact().putProperty(Artifact.PROPERTY_MAPPED_ARTIFACT, left.getArtifact());
			}
		}


		Iterator<Node> leftChildrenIterator = left.getChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node rightChild = right.getChildren().get(ri);

			map(leftChild, rightChild);
		}


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					right.getChildren().stream().forEach((Node n) -> n.getArtifact().setSequenceNumber(0));
				}
			}
		}
	}

	private static void mapAtomicArtifacts(Node left, Node right) {
		right.getArtifact().putProperty(Artifact.PROPERTY_MAPPED_ARTIFACT, left.getArtifact());

		if (left.getChildren().size() != right.getChildren().size()) {
			throw new EccoException("Equal atomic nodes must have identical children!");
		}

		for (Node leftChild : left.getChildren()) {
			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1) {
				throw new EccoException("Equal atomic nodes must have identical children!");
			}

			Node rightChild = right.getChildren().get(ri);

			Trees.mapAtomicArtifacts(leftChild, rightChild);
		}
	}


	/**
	 * Composes a new tree from the given trees using clone/add/merge/etc. operations on the given trees without modifying them.
	 *
	 * @param nodes The root nodes of the trees to be composed.
	 * @return The root node of the composed tree.
	 */
	public Node compose(Collection<Node> nodes) {
		return null; // TODO
	}


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
		if (node.isAtomic())
			return;
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
			//throw new IllegalStateException("Expected a unique node where the artifact's containing node is the unique node.");
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
