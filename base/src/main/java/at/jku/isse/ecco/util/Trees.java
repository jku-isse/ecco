package at.jku.isse.ecco.util;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This static class provides a collection of tree utility functions.
 */
public class Trees {

	// # CHILD MATCHING HELPERS ##########################################################################

	/**
	 * A mutable, hash-based index over a node's children, used in place of repeated
	 * {@code list.indexOf(x)} scans (each O(n), so O(n^2) overall for n children) when matching two
	 * nodes' children by equality during composition. Multiple children can be equal to each other
	 * (e.g. duplicate sibling artifacts under an ordered node), so each equality bucket preserves the
	 * children's original relative order and hands them out in that order, same as repeated
	 * {@code indexOf()} calls would. Matches are found via {@link #find}, which does not remove
	 * anything (mirroring indexOf()'s behavior of always finding the same first occurrence for an
	 * unmodified list) - callers that actually remove a matched child from the underlying list must
	 * also call {@link #remove} so it isn't offered again.
	 */
	private static final class ChildIndex {
		private final Map<Node.Op, Deque<Node.Op>> buckets = new HashMap<>();

		ChildIndex(List<? extends Node.Op> children) {
			for (Node.Op child : children) {
				add(child);
			}
		}

		void add(Node.Op child) {
			buckets.computeIfAbsent(child, k -> new ArrayDeque<>()).addLast(child);
		}

		/**
		 * Returns the first still-indexed child equal to the given key, without removing it (a
		 * child that stays in the underlying list, e.g. because it wasn't emptied by a recursive
		 * match, must remain available for a later, duplicate key too).
		 */
		Node.Op find(Node.Op key) {
			Deque<Node.Op> bucket = buckets.get(key);
			return bucket == null ? null : bucket.peekFirst();
		}

		/**
		 * Like {@link #find}, but when several still-indexed children are equal to the key, prefers
		 * one that's currently empty (no children of its own) over one that already has content.
		 * Used by {@link #treeFusion} when reconciling an association tree that only contributes
		 * SOME of several ordered, content-equal siblings (e.g. one voice's clef value, re-fused
		 * back in after being temporarily different in an earlier commit) against a mainTree that
		 * may already have accumulated the others from a previously-fused association - an empty
		 * candidate is far more likely to be the position still waiting for this content than an
		 * already-filled one, which is almost certainly a different, already-resolved sibling.
		 * Content equality alone can't tell these apart (that's the whole reason multiple equal
		 * children need an "ordered" parent to coexist at all - see SerNode#addChildWithoutNumberUpdate),
		 * and unlike {@link #find}, this does NOT default to insertion order when it doesn't need to,
		 * since insertion order here reflects the ACCIDENT of which association got fused first, not
		 * any real correspondence between the two trees being fused.
		 */
		Node.Op findPreferringEmpty(Node.Op key) {
			Deque<Node.Op> bucket = buckets.get(key);
			if (bucket == null) return null;
			for (Node.Op candidate : bucket) {
				if (candidate.getChildren().isEmpty()) {
					return candidate;
				}
			}
			return bucket.peekFirst();
		}

		/**
		 * Removes the given child (previously returned by {@link #find}) from the index, once a
		 * caller has actually removed it from the underlying children list too.
		 */
		void remove(Node.Op child) {
			Deque<Node.Op> bucket = buckets.get(child);
			if (bucket != null) {
				bucket.removeFirstOccurrence(child);
				if (bucket.isEmpty()) {
					buckets.remove(child);
				}
			}
		}
	}

	/**
	 * A non-consuming, hash-based index mapping each equality class in {@code children} to its
	 * first occurrence, replacing repeated {@code list.indexOf(x)} scans for callers that never
	 * remove matched children (so, like indexOf() on an unmodified list, every duplicate key
	 * consistently resolves to the same first occurrence).
	 */
	private static <T extends Node> Map<T, T> buildFirstOccurrenceIndex(List<? extends T> children) {
		Map<T, T> index = new HashMap<>();
		for (T child : children) {
			index.putIfAbsent(child, child);
		}
		return index;
	}

	/**
	 * Removes exactly the given (identity-distinct) children from the list in a single O(n) pass,
	 * instead of once per removed child (each of which is itself O(n) on an ArrayList, due to
	 * element shifting) - avoiding turning n individual removals into an O(n^2) cost.
	 */
	private static void removeAll(List<? extends Node.Op> children, Set<Node.Op> toRemove) {
		if (!toRemove.isEmpty()) {
			children.removeIf(toRemove::contains);
		}
	}

	private static Set<Node.Op> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}


	// # WRITE OPERATIONS ##############################################################################################

	/**
	 * Slices (i.e. intersects) the two given nodes. It returns the intersection node and removes the intersection from the left and right nodes.
	 *
	 * @param left  The left (original) node.
	 * @param right The right (new) node.
	 * @return The created intersection node.
	 */
	public static Node.Op slice(Node.Op left, Node.Op right) {
		if (!left.equals(right))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");

		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getPartialOrderGraph() != right.getArtifact().getPartialOrderGraph()) {
					left.getArtifact().getPartialOrderGraph().merge(right.getArtifact().getPartialOrderGraph());
					right.getArtifact().setPartialOrderGraph(left.getArtifact().getPartialOrderGraph());
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setPartialOrderGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getPartialOrderGraph().merge(left.getChildrenArtifacts());
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().getPartialOrderGraph().merge(right.getChildrenArtifacts());
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					right.getArtifact().getPartialOrderGraph().merge(left.getChildrenArtifacts());
					left.getArtifact().setPartialOrderGraph(right.getArtifact().getPartialOrderGraph());
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

			intersection.combineProactiveTrace(left);
			intersection.combineProactiveTrace(right);
			left.removeProactiveTrace();
			right.removeProactiveTrace();
		} else {
			intersection.setUnique(false);
		}

		ChildIndex rightIndex = new ChildIndex(right.getChildren());
		Set<Node.Op> leftChildrenToRemove = newIdentitySet();
		Set<Node.Op> rightChildrenToRemove = newIdentitySet();
		List<Node.Op> intersectionChildrenToAdd = new ArrayList<>();

		for (Node.Op leftChild : left.getChildren()) {
			Node.Op rightChild = rightIndex.find(leftChild);
			if (rightChild == null)
				continue;

			Node.Op intersectionChild = slice(leftChild, rightChild);
			boolean keepInIntersection = intersectionChild != null && (intersectionChild.isUnique() || (!intersectionChild.getChildren().isEmpty() && !intersectionChild.isAtomic()));
			if (keepInIntersection) {
				intersectionChildrenToAdd.add(intersectionChild);
			}

			if (intersectionChild != null && intersectionChild.isAtomic()) { // left child becomes the intersection child (if kept - see keepInIntersection)
				if (keepInIntersection) {
					intersectionChild.setParent(intersection);
				} else {
					// not unique enough to keep on its own (same bar as the general case below) - it
					// was still matched/consumed by this slice though, so it must not remain attached
					// to left either, just like a pruned non-atomic non-unique childless node isn't.
					leftChild.setParent(null);
				}
				rightChild.setParent(null);

				leftChildrenToRemove.add(leftChild);
				rightChildrenToRemove.add(rightChild);
				rightIndex.remove(rightChild);
			} else {
				if (!leftChild.isUnique() && leftChild.getChildren().isEmpty()) {
					leftChild.setParent(null);
					leftChildrenToRemove.add(leftChild);
				}

				if (!rightChild.isUnique() && rightChild.getChildren().isEmpty()) {
					rightChild.setParent(null);
					rightChildrenToRemove.add(rightChild);
					rightIndex.remove(rightChild);
				}
			}
		}

		if (!intersectionChildrenToAdd.isEmpty()) {
			intersection.addChildren(intersectionChildrenToAdd.toArray(new Node.Op[0]));
		}

		removeAll(left.getChildren(), leftChildrenToRemove);
		removeAll(right.getChildren(), rightChildrenToRemove);

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

		Map<Node.Op, Node.Op> rightByArtifact = buildFirstOccurrenceIndex(right.getChildren());
		for (Node.Op leftChild : left.getChildren()) {
			Node.Op rightChild = rightByArtifact.get(leftChild);
			if (rightChild == null) {
				throw new EccoException("Equal atomic nodes must have identical children!");
			}

			Trees.matchAtomicArtifacts(leftChild, rightChild);
		}
	}

	public static void mergePartialOrderGraphs(Node.Op left, Node.Op right) {
		if (left.getArtifact() != null && right.getArtifact() != null) {
			if (left.getArtifact().isOrdered()) {
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getPartialOrderGraph() != right.getArtifact().getPartialOrderGraph()) {
					left.getArtifact().getPartialOrderGraph().merge(right.getArtifact().getPartialOrderGraph());
					right.getArtifact().setPartialOrderGraph(left.getArtifact().getPartialOrderGraph());
				} else if (!left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().setPartialOrderGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getPartialOrderGraph().merge(left.getChildrenArtifacts());
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					left.getArtifact().getPartialOrderGraph().merge(right.getChildrenArtifacts());
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					right.getArtifact().getPartialOrderGraph().merge(left.getChildrenArtifacts());
					left.getArtifact().setPartialOrderGraph(right.getArtifact().getPartialOrderGraph());
					throw new EccoException("Left node was not sequenced but right node was!");
				}
			}
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
		ChildIndex leftIndex = new ChildIndex(left.getChildren());
		Set<Node.Op> rightChildrenToRemove = newIdentitySet();

		for (Node.Op rightChild : right.getChildren()) {
			Node.Op leftChild = leftIndex.find(rightChild);
			if (leftChild != null) {
				merge(leftChild, rightChild);

				// detatch right child from right node. this should not be necessary, but to be safe we clean up here.
				rightChildrenToRemove.add(rightChild);
				rightChild.setParent(null);
			} else {
				left.addChild(rightChild);
				leftIndex.add(rightChild); // a newly-added child is a match candidate for a later duplicate
			}
		}

		removeAll(right.getChildren(), rightChildrenToRemove);
	}

	/**
	 * Merges the right node into the left node and combines Traces of equal nodes.
	 *
	 * @param left  The left node to which is added.
	 * @param right The right node which is added.
	 */
	public static void mergeTraceTrees(Node.Op left, Node.Op right) {
		// do some basic checks
		if (left.getArtifact() != right.getArtifact()) {
			throw new EccoException("Artifact instance must be identical, i.e. trees must originate from the same repository.");
		}

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
			node.getArtifact().setPartialOrderGraph(node.getArtifact().createSequenceGraph());
			node.getArtifact().getPartialOrderGraph().merge(node.getChildrenArtifacts());
			//SequenceGraphUtil.sequence(node.getArtifact().getPartialOrderGraph(), node);
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
	public static void subtract(Node.Op left, Node.Op right) {
		// do some basic checks
		if (left.getArtifact() != null && !left.getArtifact().equals(right.getArtifact()))
			throw new EccoException("Artifacts must be equal.");

		// deal with current node
		if (right.isUnique())
			left.setUnique(false);

		// deal with children (right is never modified, so a plain non-consuming lookup suffices)
		Map<Node.Op, Node.Op> rightByArtifact = buildFirstOccurrenceIndex(right.getChildren());
		Set<Node.Op> leftChildrenToRemove = newIdentitySet();

		for (Node.Op leftChild : left.getChildren()) {
			Node.Op rightChild = rightByArtifact.get(leftChild);
			if (rightChild != null) {
				subtract(leftChild, rightChild);

				if (!leftChild.isUnique() && leftChild.getChildren().isEmpty())
					leftChildrenToRemove.add(leftChild);
			}
		}

		removeAll(left.getChildren(), leftChildrenToRemove);
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
		if (left == null && right == null) { return true; }
		if (left == null) { return false; }
		if (!left.equals(right)) { return false; }

		Map<Node, Node> rightByArtifact = buildFirstOccurrenceIndex(right.getChildren());

		for (Node leftChild : left.getChildren()) {
			Node rightChild = rightByArtifact.get(leftChild);
			if (rightChild == null)
				return false;

			if (!equals(leftChild, rightChild))
				return false;
		}
		return true;
	}

	/**
	 * Returns true, if the given nodes and all nodes on the path to root are equal.
	 * @param left first node to be compared (not necessarily root).
	 * @param right second node to be compared (not necessarily root).
	 * @return
	 */
	public static boolean equalTrunks(Node left, Node right){
		if (left == null && right == null) { return true; }
		if (left == null) { return false; }
		if (!left.equals(right)) { return false; }
		return equalTrunks(left.getParent(), right.getParent());
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
				if (left.getArtifact().isSequenced() && right.getArtifact().isSequenced() && left.getArtifact().getPartialOrderGraph() != right.getArtifact().getPartialOrderGraph()) {
					throw new EccoException("Sequence Graphs did not match!");
				} else if (!left.getArtifact().isSequenced() && right.getArtifact().isSequenced()) {
					throw new EccoException("Left node was not sequenced but right node was!");
				}

				if (!left.getArtifact().isSequenced()) {
					left.getArtifact().setPartialOrderGraph(left.getArtifact().createSequenceGraph());
					left.getArtifact().getPartialOrderGraph().merge(left.getChildrenArtifacts());
				}

				if (left.getArtifact().isSequenced() && !right.getArtifact().isSequenced()) {
					List<Artifact.Op<?>> rightArtifacts = right.getChildren().stream().map(Node.Op::getArtifact).collect(Collectors.toList());
					left.getArtifact().getPartialOrderGraph().align(rightArtifacts);
					right.getArtifact().setPartialOrderGraph(left.getArtifact().getPartialOrderGraph());
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

		Map<Node.Op, Node.Op> rightByArtifact = buildFirstOccurrenceIndex(right.getChildren());
		for (Node.Op leftChild : left.getChildren()) {
			Node.Op rightChild = rightByArtifact.get(leftChild);
			if (rightChild == null)
				continue;

			Trees.map(leftChild, rightChild);
		}
	}

	private static void mapAtomicArtifacts(Node.Op left, Node.Op right) {
		right.getArtifact().putProperty(Artifact.PROPERTY_MAPPED_ARTIFACT, left.getArtifact());
		right.getArtifact().setReplacingArtifact(left.getArtifact());
		//right.setArtifact(left.getArtifact());

		if (left.getChildren().size() != right.getChildren().size()) {
			throw new EccoException("Equal atomic nodes must have identical children!");
		}

		Map<Node.Op, Node.Op> rightByArtifact = buildFirstOccurrenceIndex(right.getChildren());
		for (Node.Op leftChild : left.getChildren()) {
			Node.Op rightChild = rightByArtifact.get(leftChild);
			if (rightChild == null) {
				throw new EccoException("Equal atomic nodes must have identical children!");
			}

			Trees.mapAtomicArtifacts(leftChild, rightChild);
		}
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
		checkConsistencyRec(node, true);
	}

	private static void checkConsistencyRec(Node.Op node, boolean checkParentLink) {
		if (node.getArtifact() != null)
			node.getArtifact().checkConsistency();

		checkUses(node);
		hasNoReplacingArtifact(node);
		hasParent(node);
		isNotRootNode(node);
		hasArtifact(node);
		isNotUniqueAndArtifactDoesNotReferenceNode(node);
		isUniqueAndArtifactReferencesNode(node);
		// parentHasNodeAsChild() is an O(n) scan (node.getParent().getChildren().contains(node)),
		// so O(n^2) if repeated for every node of a wide tree. Only check it here, at the top-level
		// entry point - for every node reached via the loop below, it is provably redundant: child
		// was just obtained by iterating node.getChildren(), and child.getParent() == node is
		// already verified right below, so node.getChildren().contains(child) trivially holds.
		if (checkParentLink) {
			parentHasNodeAsChild(node);
		}

		for (Node.Op child : node.getChildren()) {
			if (child.getParent() != node)
				throw new IllegalStateException("Node is child of a node that is not its parent.");
			checkConsistencyRec(child, false);
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

	/**
	 * Copies the node and removes all nodes from the tree except the given and those leading to the root node.
	 * Makes all nodes non-unique except the given node, which it makes unique.
	 */
	public static Node.Op createSkeletonPath(Node.Op node) {
		// create a copy of the path from the node to the root
		// copy of given node will include feature trace
		// other copies will not
		Node.Op newNode = node.copySingleNode(true);
		if (node.getParent() == null) {
			return newNode;
		} else {
			Node.Op parent = createShallowSkeletonPath(node.getParent());
			parent.addChild(newNode);
			return newNode;
		}
	}

	public static Node.Op createShallowSkeletonPath(Node.Op node){
		// create a copy of the path from the node to the root
		// copied nodes will not include feature traces
		Node.Op newNode = node.copySingleNode(false);
		if (node.getParent() == null) {
			return newNode;
		} else {
			Node.Op parent = createShallowSkeletonPath(node.getParent());
			parent.addChild(newNode);
			return newNode;
		}
	}

	public static Node.Op treeFusion(Node.Op mainTree, Node.Op fusionNode){
		if (fusionNode == null){ return mainTree; }
		// new node will contain pog because same artifact is set in new node and pog is set in artifact
		if (mainTree == null){ mainTree = fusionNode.copySingleNode(false); }
		if (!Objects.equals(mainTree.getArtifact(), fusionNode.getArtifact())) {
			throw new EccoException("Fusing feature trace (sub-)trees with different root nodes is not possible.");
		}

		// deal with children - ChildIndex (the same mechanism Trees.slice() already uses for
		// matching duplicate/ordered siblings) instead of calling mainTree.getEqualChild() per
		// child directly: getEqualChild() alone has no memory of what it already matched earlier in
		// THIS loop, so if fusionNode itself contributes multiple content-equal ordered children
		// (e.g. several voices sharing the same clef value) in one pass, every one of them would be
		// funneled into whichever single mainTree candidate happens to match first, instead of each
		// pairing up with a DIFFERENT candidate. findPreferringEmpty() additionally prefers a
		// still-empty candidate over an already-filled one when several remain available, for the
		// case where mainTree already accumulated more ordered siblings than this fusionNode
		// currently offers (an association re-fusing just one voice's content back in) - see
		// TreeFusionOrderedSiblingMatchingTest for the exact scenario this fixes.
		ChildIndex mainIndex = new ChildIndex(mainTree.getChildren());
		for (Node.Op child : fusionNode.getChildren()) {
			Node.Op mainChild = mainIndex.findPreferringEmpty(child);
			if (mainChild == null) {
				Node.Op newChild = child.copySingleNode(false);
				newChild.getFeatureTrace().fuseFeatureTrace(child.getFeatureTrace());
				newChild.setUnique(child.isUnique());
				mainTree.addChild(newChild);
				// deliberately NOT added to mainIndex - a newly-created child must stay available
				// only for a later cross-association re-fusion (a future treeFusion() call, with its
				// own fresh ChildIndex), not for a second content-equal sibling arriving LATER in
				// THIS SAME fusionNode's children, which needs its own distinct new child too
				treeFusion(newChild, child);
			} else {
				mainIndex.remove(mainChild);
				mainChild.getFeatureTrace().fuseFeatureTrace(child.getFeatureTrace());
				mainChild.setUnique(mainChild.isUnique() || child.isUnique());
				Trees.mergePartialOrderGraphs(mainChild, child);
				// properties (e.g. LINE_START/LINE_END, set by adapters like TextReader at parse
				// time) are purely informational/display metadata, not structural -- but were
				// silently dropped for a content-equal sibling reused via mainIndex, unlike the
				// copySingleNode() branch above which already propagates them. Additive (putProperty
				// per key), so this can't clobber anything mainChild already has beyond the same keys.
				mainChild.putProperties(child.getProperties());
				treeFusion(mainChild, child);
			}
		}

		return mainTree;
	}
}
