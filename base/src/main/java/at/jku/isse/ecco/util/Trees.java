package at.jku.isse.ecco.util;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
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
	 */
	//public static <T extends Node.Op> T slice(T left, T right) throws EccoException {
	public static Node.Op slice(Node.Op left, Node.Op right) {
		if (!left.equals(right))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getSequenceGraph() != right.getArtifact().getSequenceGraph()) {
					//throw new EccoException("Sequence Graphs did not match!");
					left.getArtifact().getSequenceGraph().merge(right.getArtifact().getSequenceGraph());
					right.getArtifact().setSequenceGraph(left.getArtifact().getSequenceGraph());
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getSequenceGraph().merge(left.getChildrenArtifacts());
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().getSequenceGraph().merge(right.getChildrenArtifacts());
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					right.getArtifact().getSequenceGraph().merge(left.getChildrenArtifacts());
					left.getArtifact().setSequenceGraph(right.getArtifact().getSequenceGraph());
					throw new EccoException("Left node was not sequenced but right node was!");
				}
			}


			if (left.getArtifact().isAtomic()) {
				Trees.matchAtomicArtifacts(left, right);
				return left;
			} else if (left.getArtifact() != right.getArtifact()) {
				right.getArtifact().setReplacingArtifact(left.getArtifact());

				if (left.getArtifact().hasReplacingArtifact()) {
					throw new EccoException("Replacing artifact should not have a replacing artifact itself!");
				}

				// merge artifact references
				for (ArtifactReference.Op ar : right.getArtifact().getUses()) {
					if (!left.getArtifact().getUses().contains(ar)) {
						left.getArtifact().addUses(ar);
						ar.setSource(left.getArtifact());
					}
				}
				for (ArtifactReference.Op ar : right.getArtifact().getUsedBy()) {
					if (!left.getArtifact().getUsedBy().contains(ar)) {
						left.getArtifact().addUsedBy(ar);
						ar.setTarget(left.getArtifact());
					}
				}

				right.setArtifact(left.getArtifact());
			}
		}


		Node.Op intersection = left.createNode(left.getArtifact());
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


		Iterator<? extends Node.Op> leftChildrenIterator = left.getChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node.Op leftChild = leftChildrenIterator.next();

			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node.Op rightChild = right.getChildren().get(ri);

			Node.Op intersectionChild = slice(leftChild, rightChild);

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

	private static void matchAtomicArtifacts(Node.Op left, Node.Op right) {
		//right.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, left.getArtifact());
		right.getArtifact().setReplacingArtifact(left.getArtifact());

		if (left.getArtifact().hasReplacingArtifact()) {
			throw new EccoException("Replacing artifact should not have a replacing artifact itself!");
		}

		// merge artifact references
		for (ArtifactReference.Op ar : right.getArtifact().getUses()) {
			if (!left.getArtifact().getUses().contains(ar)) {
				left.getArtifact().addUses(ar);
				ar.setSource(left.getArtifact());
			}
		}
		for (ArtifactReference.Op ar : right.getArtifact().getUsedBy()) {
			if (!left.getArtifact().getUsedBy().contains(ar)) {
				left.getArtifact().addUsedBy(ar);
				ar.setTarget(left.getArtifact());
			}
		}

		right.setArtifact(left.getArtifact());

		if (left.getChildren().size() != right.getChildren().size()) {
			throw new EccoException("Equal atomic nodes must have identical children!");
		}

		for (Node.Op leftChild : left.getChildren()) {
			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1) {
				throw new EccoException("Equal atomic nodes must have identical children!");
				//continue;
			}

			Node.Op rightChild = right.getChildren().get(ri);

			Trees.matchAtomicArtifacts(leftChild, rightChild);
		}
	}


	/**
	 * Merges the right node into the left node. The right node is modified!
	 *
	 * @param left  The left node to which is added.
	 * @param right The right node which is added.
	 */
	public static void merge(Node.Op left, Node.Op right) { // TODO: exact behavior of this method?
		// do some basic checks
		if (left.getArtifact() != right.getArtifact())
			throw new EccoException("Artifact instance must be identical, i.e. trees must originate from the same repository.");

		// deal with current node
		if (right.isUnique()) {
			left.setUnique(true); // TODO: the "unique" field is redundant. we could determine uniqueness via the artifact's containing node (i.e. whether node and containing node are identical).
			if (left.getArtifact() != null)
				left.getArtifact().setContainingNode(left);
		}

		// deal with children
		Iterator<? extends Node.Op> iterator = right.getChildren().iterator();
		while (iterator.hasNext()) {
			Node.Op rightChild = iterator.next();
			int li = left.getChildren().indexOf(rightChild);
			if (li != -1) {
				Node.Op leftChild = left.getChildren().get(li);

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
	public static void sequence(Node.Op node) throws EccoException {
		if (node.getArtifact() != null && node.getArtifact().isOrdered() && !node.getArtifact().isSequenced()) {
			node.getArtifact().setSequenceGraph(node.getArtifact().createSequenceGraph());
			node.getArtifact().getSequenceGraph().merge(node.getChildrenArtifacts());
			//SequenceGraphUtil.sequence(node.getArtifact().getSequenceGraph(), node);
		}
		for (Node.Op child : node.getChildren()) {
			sequence(child);
		}
	}


	/**
	 * Updates uses and usedBy references of artifacts (unique as well as non-unique) contained in the tree rooted at the given node.
	 *
	 * @param node The root of the tree.
	 */
	public static void updateArtifactReferences(Node.Op node) throws EccoException {
		if (node.getArtifact() != null) {
			if (node.getArtifact().hasReplacingArtifact()) {
				if (!node.isUnique())
					node.setArtifact(node.getArtifact().getReplacingArtifact());
				else
					throw new EccoException("Artifact should have been replaced.");
			}

			node.getArtifact().updateArtifactReferences();

//			// update "uses" artifact references
//			for (ArtifactReference uses : node.getArtifact().getUses()) {
//				if (uses.getSource() != node.getArtifact())
//					throw new EccoException("Source of uses artifact reference must be identical to artifact.");
//
//				if (uses.getTarget().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
//					Artifact replacingArtifact = uses.getTarget().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
//					if (replacingArtifact != null) {
//						uses.setTarget(replacingArtifact);
//						if (!replacingArtifact.getUsedBy().contains(uses)) {
//							replacingArtifact.addUsedBy(uses);
//						}
//					}
//				}
//			}
//
//			// update "used by" artifact references
//			for (ArtifactReference usedBy : node.getArtifact().getUsedBy()) {
//				if (usedBy.getTarget() != node.getArtifact())
//					throw new EccoException("Target of usedBy artifact reference must be identical to artifact.");
//
//				if (usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
//					Artifact replacingArtifact = usedBy.getSource().<Artifact>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
//					if (replacingArtifact != null) {
//						usedBy.setSource(replacingArtifact);
//						if (!replacingArtifact.getUses().contains(usedBy)) {
//							replacingArtifact.addUses(usedBy);
//						}
//					}
//				}
//			}
//
//			// update sequence graph symbols (which are artifacts)
//			if (node.getArtifact().getSequenceGraph() != null) {
//				node.getArtifact().getSequenceGraph().updateArtifactReferences();
//			}
		}

		// traverse into children
		for (Node.Op child : node.getChildren()) {
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
	public static Node.Op extractMarked(Node.Op left) {
		Node.Op right = extractMarkedRec(left);
		return right;
	}

	private static Node.Op extractMarkedRec(Node.Op left) {
		// create right node
		Node.Op right = left.createNode(left.getArtifact());

		// process children
		Iterator<? extends Node.Op> iterator = left.getChildren().iterator();
		while (iterator.hasNext()) {
			Node.Op leftChild = iterator.next();
			Node.Op rightChild = extractMarkedRec(leftChild);
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
	public void subtract(Node.Op left, Node.Op right) {
		// do some basic checks
		if (left.getArtifact() != null && !left.getArtifact().equals(right.getArtifact()))
			throw new EccoException("Artifacts must be equal.");

		// deal with current node
		if (right.isUnique())
			left.setUnique(false);

		// deal with children
		Iterator<? extends Node.Op> iterator = left.getChildren().iterator();
		while (iterator.hasNext()) {
			Node.Op leftChild = iterator.next();
			int ri = right.getChildren().indexOf(leftChild);
			if (ri != -1) {
				Node.Op rightChild = right.getChildren().get(ri);

				subtract(leftChild, rightChild);

				if (!leftChild.isUnique() && leftChild.getChildren().isEmpty())
					iterator.remove();
			}
		}
	}


	// # READ ONLY OPERATIONS ##################################################################################


	/**
	 * Checks if two trees are equal.
	 *
	 * @param left  Root of the first tree.
	 * @param right Root of the second tree.
	 * @return True if the two given trees are equal, false otherwise.
	 */
	public static boolean equals(Node left, Node right) {
		Iterator<? extends Node> leftChildrenIterator = left.getChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				return false;

			Node rightChild = right.getChildren().get(ri);

			if (!equals(leftChild, rightChild))
				return false;
		}
		return true;
	}


	/**
	 * Maps artifacts in tree rooted at right to artifacts in tree rooted at left.
	 * Does not merge or update artifact references. The left tree is not modified.
	 * Artifacts in the right tree have sequence graphs, sequence numbers and replacing artifacts assigned.
	 * <p>
	 * Mapped artifacts are set as replacing artifact (see {@link Artifact.Op#getReplacingArtifact()}) and can be found in the property {@link Artifact#PROPERTY_MAPPED_ARTIFACT} of the right artifacts.
	 *
	 * @param left  Root node of the first tree.
	 * @param right Root node of the second tree.
	 */
	public static void map(Node.Op left, Node.Op right) {
		if (!left.equals(right))
			throw new EccoException("Mapping of non-equal nodes is not allowed!");


		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getSequenceGraph() != right.getArtifact().getSequenceGraph()) {
					throw new EccoException("Sequence Graphs did not match!");
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					throw new EccoException("Left node was not sequenced but right node was!");
				}

				if (!left.getArtifact().isSequenced()) {
					left.getArtifact().setSequenceGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getSequenceGraph().merge(left.getChildrenArtifacts());
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					List<Artifact.Op<?>> rightArtifacts = right.getChildren().stream().map(Node.Op::getArtifact).collect(Collectors.toList());
					left.getArtifact().getSequenceGraph().align(rightArtifacts);
					right.getArtifact().setSequenceGraph(left.getArtifact().getSequenceGraph());
				}
			}

			if (left.isUnique()) {
				if (left.getArtifact().isAtomic()) {
					Trees.mapAtomicArtifacts(left, right);
				} else if (left.getArtifact() != right.getArtifact() && right.getArtifact().getReplacingArtifact() != left.getArtifact()) {
					right.getArtifact().putProperty(Artifact.PROPERTY_MAPPED_ARTIFACT, left.getArtifact());
					right.getArtifact().setReplacingArtifact(left.getArtifact());
					//right.setArtifact(left.getArtifact());
				}
			}
		}


		for (Node.Op leftChild : left.getChildren()) {
			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node.Op rightChild = right.getChildren().get(ri);

			Trees.map(leftChild, rightChild);
		}


//		if (left.getArtifact() != null && right.getArtifact() != null) {
//			if (left.getArtifact().isOrdered()) {
//				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
//					right.getChildren().forEach((Node.Op n) -> n.getArtifact().setSequenceNumber(PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER));
//				}
//			}
//		}
	}

	private static void mapAtomicArtifacts(Node.Op left, Node.Op right) {
		right.getArtifact().putProperty(Artifact.PROPERTY_MAPPED_ARTIFACT, left.getArtifact());
		right.getArtifact().setReplacingArtifact(left.getArtifact());
		//right.setArtifact(left.getArtifact());

		if (left.getChildren().size() != right.getChildren().size()) {
			throw new EccoException("Equal atomic nodes must have identical children!");
		}

		for (Node.Op leftChild : left.getChildren()) {
			int ri = right.getChildren().indexOf(leftChild);
			if (ri == -1) {
				throw new EccoException("Equal atomic nodes must have identical children!");
			}

			Node.Op rightChild = right.getChildren().get(ri);

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
	 * Computes the depth of a node in the tree.
	 *
	 * @param node The node of the artifact tree.
	 * @return The depth of the node in the tree.
	 */
	public static int computeDepth(Node node) {
		if (node.getParent() == null)
			return 0;
		else
			return 1 + computeDepth(node.getParent());
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
	public static void checkConsistency(Node.Op node) {
		if (node.getArtifact() != null)
			node.getArtifact().checkConsistency();

		checkUses(node);
		hasNoReplacingArtifact(node);
		hasParent(node);
		isNotRootNode(node);
		hasArtifact(node);
		isNotUniqueAndArtifactDoesNotReferenceNode(node);
		isUniqueAndArtifactReferencesNode(node);
		parentHasNodeAsChild(node);

		for (Node.Op child : node.getChildren()) {
			if (child.getParent() != node)
				throw new IllegalStateException("Node is child of a node that is not its parent.");
			checkConsistency(child);
		}
	}

	private static void checkUses(Node.Op node) {
		if (node.getArtifact() != null) {
			for (ArtifactReference.Op ref : node.getArtifact().getUses()) {
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

	private static void hasNoReplacingArtifact(Node.Op node) {
		if (!(node instanceof RootNode) && node.getArtifact().hasReplacingArtifact()) {
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

	private static void referenceHasNoReplacingArtifact(ArtifactReference.Op reference) {
		if (reference.getTarget().hasReplacingArtifact()) {
			throw new IllegalStateException("Expected that the referenced target has no replacing artifact.");
		}
	}

}
