package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.util.Permutation;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;

import java.util.*;

public interface PartialOrderGraph extends Persistable {
	int INITIAL_SEQUENCE_NUMBER = 1;
	int NOT_MATCHED_SEQUENCE_NUMBER = -1;
	int UNASSIGNED_SEQUENCE_NUMBER = -2;


	Node getHead();

	Collection<? extends Node> collectNodes();

	/**
	 * Matches {@code treeNode}'s children to this graph's own nodes by artifact equality - the same
	 * correlation {@code DefaultOrderSelector}'s topological walk already does when picking an order,
	 * deliberately duplicated here (not shared/extracted) rather than reused from there, so this stays
	 * fully additive to the composition path instead of risking a change to it. A child with no match
	 * (e.g. content not yet reflected in the graph) is simply absent from the result - callers should
	 * treat that as "no known constraint", not an error.
	 */
	default Map<at.jku.isse.ecco.tree.Node, PartialOrderGraph.Node> matchChildren(at.jku.isse.ecco.tree.Node treeNode) {
		Map<at.jku.isse.ecco.tree.Node, PartialOrderGraph.Node> matched = new HashMap<>();

		Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
		Stack<PartialOrderGraph.Node> stack = new Stack<>();
		stack.push(this.getHead());

		while (!stack.isEmpty()) {
			PartialOrderGraph.Node pogNode = stack.pop();

			for (at.jku.isse.ecco.tree.Node childNode : treeNode.getChildren()) {
				if (childNode.getArtifact() != null && childNode.getArtifact().equals(pogNode.getArtifact())) {
					matched.put(childNode, pogNode);
					break;
				}
			}

			for (PartialOrderGraph.Node child : pogNode.getNext()) {
				counters.putIfAbsent(child, 0);
				int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
				// check if all parents of the node have been processed
				if (counter >= child.getPrevious().size()) {
					counters.remove(child);
					stack.push(child);
				}
			}
		}

		return matched;
	}

	/**
	 * Whether {@code treeNode}'s children have any genuinely undetermined relative order left - i.e.
	 * at least one pair, adjacent in {@code treeNode}'s current child order, that this graph doesn't
	 * already fix (directly or transitively - see {@link Op#canReach}) in either direction. A child
	 * with no matching graph node (see {@link #matchChildren}) counts as free relative to its
	 * neighbor, same permissive fallback used there.
	 * <p>
	 * {@code false} doesn't mean the node wasn't flagged uncertain - {@code DefaultOrderSelector}
	 * flags a node the moment it walks past *any* branch point anywhere in the graph's accumulated
	 * history, even one between content from two variants that were never both present in this
	 * composition at once. This checks something narrower and more actionable: whether *this specific
	 * set of children*, in their current order, still has an actual decision left in it.
	 */
	default boolean hasUnresolvedOrder(at.jku.isse.ecco.tree.Node treeNode) {
		Map<at.jku.isse.ecco.tree.Node, PartialOrderGraph.Node> matched = this.matchChildren(treeNode);
		List<? extends at.jku.isse.ecco.tree.Node> children = treeNode.getChildren();

		for (int i = 0; i < children.size() - 1; i++) {
			PartialOrderGraph.Node a = matched.get(children.get(i));
			PartialOrderGraph.Node b = matched.get(children.get(i + 1));
			boolean fixed = a != null && b != null && PartialOrderGraph.Op.canReach(a, b);
			if (!fixed) {
				return true;
			}
		}
		return false;
	}


	interface Op extends PartialOrderGraph {

		Node.Op getHead();

		Node.Op getTail();

		int getMaxIdentifier();

		void setMaxIdentifier(int value);

		void incMaxIdentifier();

		default List<Node.Op> collectNodes() {
			List<Node.Op> nodes = new ArrayList<>();

			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(this.getHead());

			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();

				nodes.add(node);

				// add children of current node
				for (Node.Op child : node.getNext()) {
					counters.putIfAbsent(child, 0);
					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
					// check if all parents of the node have been processed
					if (counter >= child.getPrevious().size()) {
						// remove node from counters
						counters.remove(child);
						// push node onto stack
						stack.push(child);
					}
				}
			}

			return nodes;
		}

		default Node.Op[][] collectNodeSequencings() {
			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(this.getHead());

			List<List<Node.Op>> sequencings = new LinkedList<>();
			this.extendNodeSequencings(new LinkedList<>(), sequencings, counters, stack);

			Node.Op[][] sequencingsArray = new Node.Op[sequencings.size()][];
			Iterator<List<Node.Op>> pathIterator = sequencings.iterator();
			for (int i = 0; i < sequencings.size(); i++){
				List<Node.Op> path = pathIterator.next();
				Node.Op[] pathArray = new Node.Op[path.size()];
				path.toArray(pathArray);
				sequencingsArray[i] = pathArray;
			}
			return sequencingsArray;
		}

		default void extendNodeSequencings(List<Node.Op> nodes,
														  List<List<Node.Op>> sequencings,
														  Map<PartialOrderGraph.Node.Op, Integer> counters,
														  Stack<PartialOrderGraph.Node.Op> stack){
			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();
				if (!node.getNext().isEmpty() && !node.getPrevious().isEmpty()) {
					nodes.add(node);
				}

				Collection<Node.Op> nextNodes = (Collection<Node.Op>) node.getNext();
				if (nextNodes.size() > 1){
					Collection<List<Node.Op>> permutations = Permutation.generatePermutations(nextNodes);
					Iterator<List<Node.Op>> iterator = permutations.iterator();
					List<Node.Op> firstPermutation = iterator.next();

					while(iterator.hasNext()){
						List<Node.Op> permutation = iterator.next();
						Stack<PartialOrderGraph.Node.Op> newStack = (Stack<Node.Op>) stack.clone();
						Map<PartialOrderGraph.Node.Op, Integer> newCounters = new HashMap<>(counters);
						List<Node.Op> newNodes = new LinkedList<>(nodes);
						this.sequenceChildNodes(permutation, newCounters, newStack);
						this.extendNodeSequencings(newNodes, sequencings, newCounters, newStack);
					}
				}

				this.sequenceChildNodes(nextNodes, counters, stack);
			}
			sequencings.add(nodes);
		}

		default void sequenceChildNodes(Collection<Node.Op> childNodes,
										Map<PartialOrderGraph.Node.Op, Integer> counters,
										Stack<PartialOrderGraph.Node.Op> stack){
			for (Node.Op child : childNodes) {
				counters.putIfAbsent(child, 0);
				int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
				// check if all parents of the node have been processed
				if (counter >= child.getPrevious().size()) {
					counters.remove(child);
					stack.push(child);
				}
			}
		}

		/**
		 * Cheaply estimates how many linearizations {@link #collectNodeSequencings()} would produce
		 * (the product, over every branch point, of that branch's degree factorial), without actually
		 * enumerating them - a single topological walk multiplying in {@code degree!} at each node with
		 * more than one "next" child, same branch-point detection as {@link #extendNodeSequencings}
		 * but no recursion/cloning. Stops and returns {@link Long#MAX_VALUE} as soon as the running
		 * product reaches {@code cap} (or would overflow), since the caller only needs "is this over
		 * the cap" - computing the exact count for a graph that's already over it is itself expensive
		 * (that's the whole problem this exists to avoid).
		 */
		default long estimateLinearizationCount(long cap) {
			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(this.getHead());

			long estimate = 1;
			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();

				Collection<Node.Op> nextNodes = (Collection<Node.Op>) node.getNext();
				if (nextNodes.size() > 1) {
					// both factors are always < cap here (this factor by loop invariant, the other
					// from factorial()'s own cap), and cap is small enough in practice (see callers)
					// that the product can't overflow a long before either factor would've hit cap
					estimate *= factorial(nextNodes.size(), cap);
					if (estimate >= cap) {
						return Long.MAX_VALUE;
					}
				}

				this.sequenceChildNodes(nextNodes, counters, stack);
			}
			return estimate;
		}

		private static long factorial(int n, long cap) {
			long result = 1;
			for (int i = 2; i <= n; i++) {
				result *= i;
				if (result >= cap) {
					return cap;
				}
			}
			return result;
		}


		Node.Op createNode(Artifact.Op<?> artifact);


		PartialOrderGraph.Op createPartialOrderGraph();


		// #############################################################################################################

		/**
		 * Creates a new partial order graph (see {@link #fromList(List)}) reflecting the given list of artifacts and aligns it to this partial order graph (see {@link #align(PartialOrderGraph.Op)}).
		 *
		 * @param artifacts Sequence of artifacts to be aligned to this partial order graph.
		 */
		default void align(List<? extends Artifact.Op<?>> artifacts) {
			this.align(this.fromList(artifacts));
		}

		/**
		 * Aligns the given partial order graph (i.e. sets the identifiers of its artifacts) to this partial order graph.
		 * <p>
		 * Skipping a node in LEFT (this) costs nothing.
		 * Matching a node costs nothing.
		 * Skipping a node in RIGHT (other) costs 1.
		 *
		 * @param other Other partial order graph to be aligned to this partial order graph.
		 */
		default void align(PartialOrderGraph.Op other) {
			this.alignMemoizedBacktracking(other);
		}

		// caps the estimated total linearization pair count (this * other) that iterativeLcsAlignment
		// is allowed to face before alignMemoizedBacktracking falls back to directPoaAlignment instead
		// of enumerating - chosen well below where iterativeLcsAlignment is empirically known to OOM
		// (3 branch points x 5-way branching = 1,728,000 already crashes) but comfortably above the
		// common case (a handful of 2-4 way branch points)
		long LINEARIZATION_CAP = 100_000L;

		//private
		default void alignMemoizedBacktracking(PartialOrderGraph.Op other) {
			long thisEstimate = this.estimateLinearizationCount(LINEARIZATION_CAP);
			long otherEstimate = thisEstimate >= LINEARIZATION_CAP ? LINEARIZATION_CAP : other.estimateLinearizationCount(LINEARIZATION_CAP);
			boolean tooComplex = thisEstimate >= LINEARIZATION_CAP || otherEstimate >= LINEARIZATION_CAP || thisEstimate * otherEstimate >= LINEARIZATION_CAP;

			// directPoaAlignment can under-match a concurrent branch whose needed order conflicts with
			// an arbitrary topological ordering (see its javadoc) - not correctness-breaking (merge()'s
			// consistency checks are structural, not optimality checks; an under-match just leaves one
			// extra unmerged branch rather than corrupting anything), but it is a real quality tradeoff,
			// so it's only used once the exact algorithm is estimated to be intractable, not by default.
			IntObjectMap<Node.Op> result = tooComplex ? this.directPoaAlignment(other) : this.iterativeLcsAlignment(other);
			// set sequence number of matched nodes - on the NODE, not the artifact (see
			// Node.getSequenceNumber()'s javadoc): other's nodes can share an artifact object with
			// nodes in this or in a completely unrelated graph, and writing through the artifact would
			// silently corrupt whichever other node/graph happens to reference the same object.
			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			result.forEachKeyValue((key, value) -> value.setSequenceNumber(key));

			// align()'s own contract (see its javadoc) is "sets the identifiers of other's
			// artifacts" - a public API in its own right, callable without ever going through
			// addRelations()/merge() (see DirectPoaAlignmentSpikeTest.fallbackViaAlign...), so the
			// node -> artifact sync needs to happen here too, not only in addRelations(). Same
			// tradeoff as there: this can still be overwritten later by an unrelated graph sharing
			// the same artifact, which is a display/equals staleness concern now, not a correctness
			// one, since matching itself is entirely node-based (see Node.getSequenceNumber()).
			other.collectNodes().stream()
					.filter(op -> op.getArtifact() != null)
					.forEach(op -> op.getArtifact().setSequenceNumber(op.getSequenceNumber()));
		}


		/**
		 * Creates a new partial order graph reflecting the given list of artifacts and merges it into this partial order graph.
		 *
		 * @param artifacts Sequence of artifacts to be merged into this partial order graph.
		 */
		default void merge(List<? extends Artifact.Op<?>> artifacts) {
			this.merge(this.fromList(artifacts));
		}

		/**
		 * @param other Other partial order graph to be merged into this partial order graph.
		 */
		default void merge(PartialOrderGraph.Op other) {
			// align other graph to this graph
			this.align(other);

			// CONSISTENCY: check if alignment is valid
			// TODO
			this.checkAlignment(other);

			// CONSISTENCY: count number of nodes before merge
			Collection<Node.Op> thisNodes = this.collectNodes();
			Collection<Node.Op> otherNodes = other.collectNodes();
			int numNodesBefore = thisNodes.size();
			int numMatchedNodes = (int) otherNodes.stream().filter(otherNode -> otherNode.getArtifact() != null && otherNode.getSequenceNumber() != PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER).count() + 2; // +2 because of head and tail
			int numUnmatchedNodes = otherNodes.size() - numMatchedNodes;

			// merge other partial order graph into this partial order graph
			//this.mergeRec(this.getHead(), other.getHead(), shared, new HashSet<>(), new HashMap<>());
			this.addRelations(other);
			this.removeTransitiveRelations(this.getHead());

			// CONSISTENCY: count number of nodes afters merge
			int numNodesAfter = this.collectNodes().size();
			if (numNodesAfter != numNodesBefore + numUnmatchedNodes)
				throw new EccoException("POG node count mismatch! BEFORE: " + numNodesBefore + ", MATCHED: " + numMatchedNodes + ", UNMATCHED: " + numUnmatchedNodes + ", AFTER: " + numNodesAfter);

			// CONSISTENCY: check cycles: for every node: can it reach itself?
			for (Node.Op thisNode : this.collectNodes())
				if (thisNode.getArtifact() != null)
					for (Node.Op nextNode : thisNode.getNext())
						if (canReach(nextNode, thisNode))
							throw new EccoException("There is a cycle in the POG!");

			// CONSISTENCY: check for redundant connections: can any node be reached from any of the other nodes?
			for (Node.Op thisNode : this.collectNodes())
				for (Node.Op nextNode : thisNode.getNext())
					for (Node.Op nextNode2 : thisNode.getNext())
						if (nextNode != nextNode2 && nextNode.getArtifact() != null && canReach(nextNode2, nextNode))
							throw new EccoException("There is a redundant transitive connection in the POG!");

			// CONSISTENCY: check if graph has cycles and throw exception if it does
			this.checkConsistency();
		}


		//private
		default void addRelations(PartialOrderGraph.Op other) {
			Collection<Node.Op> thisNodes = this.collectNodes();
			Collection<Node.Op> otherNodes = other.collectNodes();

			Map<Node.Op, Node.Op> nodeMap = new HashMap<>();
			nodeMap.put(other.getHead(), this.getHead());
			nodeMap.put(other.getTail(), this.getTail());

			for (Node.Op otherNode : otherNodes) {
				if (otherNode.getArtifact() == null) {
					// nothing to do
				} else if (otherNode.getSequenceNumber() == PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
					otherNode.setSequenceNumber(this.getMaxIdentifier());
					this.incMaxIdentifier();
				} else {
					for (Node.Op thisNode : thisNodes) {
						if (thisNode.getArtifact() != null && thisNode.getSequenceNumber() == otherNode.getSequenceNumber()) {
							nodeMap.put(otherNode, thisNode);
							break;
						}
					}
				}
			}

			for (Node.Op otherNode : otherNodes) {
				Node.Op thisNode = nodeMap.get(otherNode);
				if (thisNode == null) {
					thisNode = this.createNode(otherNode.getArtifact());
					// sequence number used to propagate for free here (it lived on the shared
					// artifact object createNode() just reused) - now that it's node-owned, a freshly
					// created node needs its OWN copy of otherNode's already-assigned number, or it'd
					// default to UNASSIGNED and never match anything in a later merge.
					thisNode.setSequenceNumber(otherNode.getSequenceNumber());
					nodeMap.put(otherNode, thisNode);
				}
				// add all next nodes that do not already exist
				for (Node.Op otherNextNode : otherNode.getNext()) {
					Node.Op thisNextNode = nodeMap.get(otherNextNode);
					if (thisNextNode == null) {
						thisNextNode = this.createNode(otherNextNode.getArtifact());
						thisNextNode.setSequenceNumber(otherNextNode.getSequenceNumber());
						nodeMap.put(otherNextNode, thisNextNode);
					}
					if (!thisNode.getNext().contains(thisNextNode)) {
						thisNode.addChild(thisNextNode);
					}
				}
			}

			// nothing inside this class reads artifact.getSequenceNumber() anymore (matching/merging
			// is entirely node-owned now - see canReach() and Node.getSequenceNumber()'s javadoc), but
			// code outside it still does: Artifact.equals() (deliberately, for ordered-duplicate
			// disambiguation), GUI/web display, EccoUtil's cross-backend copy. Sync each of this
			// graph's own nodes' final, settled value onto its artifact once here so those keep
			// working. This can still be overwritten later by an unrelated graph that happens to
			// share the same artifact object - but that's now a cosmetic display/equals staleness
			// concern, not a correctness one, since nothing above depends on reading it back.
			for (Node.Op thisNode : this.collectNodes()) {
				if (thisNode.getArtifact() != null) {
					thisNode.getArtifact().setSequenceNumber(thisNode.getSequenceNumber());
				}
			}
		}


		//private
		default void removeTransitiveRelations(Node.Op node) {
			// trim transitives, i.e. remove direct children that can be reached indirectly via any of the other children

			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(node);

			while (!stack.isEmpty()) {
				Node.Op current = stack.pop();

				// process node
				Iterator<? extends Node.Op> it = current.getNext().iterator();
				while (it.hasNext()) {
					Node.Op child = it.next();

					for (Node.Op otherChild : current.getNext()) {
						if (otherChild != child && canReach(otherChild, child)) {
							// we do not need connection -> delete it
							it.remove();
							child.getPrevious().remove(current);
							//System.out.println("Removed node " + child + " as child from node " + current);
							break;
						}
					}
				}

				// add children of current node
				for (Node.Op child : current.getNext()) {
					counters.putIfAbsent(child, 0);
					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
					// check if all parents of the node have been processed
					if (counter >= child.getPrevious().size()) {
						// remove node from counters
						counters.remove(child);
						// push node onto stack
						stack.push(child);
					}
				}
			}
		}


		/**
		 * Checks whether a target node can be reached from a given node - compared by the target's
		 * own node-owned sequence number (see {@link Node#getSequenceNumber()}), not by artifact
		 * identity: takes a {@code Node} rather than an {@code Artifact} specifically so this never
		 * has to trust an artifact's sequence number, which - unlike the node's - can be shared with
		 * (and silently overwritten by) an unrelated graph.
		 *
		 * @param node   The node to start from.
		 * @param target The node to look for.
		 * @return True if target could be reached from node, false otherwise.
		 */
		//private
		static boolean canReach(Node node, Node target) {
//			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<Node> stack = new Stack<>();
			stack.add(node);
			Set<Node> stacked = new HashSet<>();
			stacked.add(node);

			Artifact<?> targetArtifact = target == null ? null : target.getArtifact();

			while (!stack.isEmpty()) {
				Node current = stack.pop();

				// process node
				if ((targetArtifact == null && current.getArtifact() == null) || (targetArtifact != null && current.getArtifact() != null && current.getSequenceNumber() == target.getSequenceNumber()))
					return true;

				// add children of current node
				for (Node child : current.getNext()) {
//					counters.putIfAbsent(child, 0);
//					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
//					// check if all parents of the node have been processed
//					if (counter >= child.getPrevious().size()) {
//						// remove node from counters
//						counters.remove(child);
					// push node onto stack
					if (!stacked.contains(child)) {
						stack.push(child);
						stacked.add(child);
					}
//					}
				}
//				if (stack.isEmpty() && !counters.isEmpty()) {
//					for (Node remainingNode : counters.keySet()) {
//						stack.push(remainingNode);
//					}
//					counters.clear();
//				}
			}
			return false;
		}


		// #############################################################################################################


		default void copy(PartialOrderGraph.Op other) {
			// New sequences are created with their (null-)tail as a child of their (null-)heads
			// Therefore, even a new sequence is technically "not empty".
			// So first, remove the tail from the head and if it is not empty afterward, it really actually is not empty
			this.getHead().removeChild(this.getTail());

			if (!this.getHead().getNext().isEmpty()) {
				throw new EccoException("Partial order graph must be empty to copy another.");
			}

			this.setMaxIdentifier(other.getMaxIdentifier());

			Map<PartialOrderGraph.Node.Op, PartialOrderGraph.Node.Op> matches = new HashMap<>();
			matches.put(other.getHead(), this.getTail());
			matches.put(other.getTail(), this.getTail());

			Stack<PartialOrderGraph.Node.Op[]> stack = new Stack<>();
			stack.push(new Node.Op[]{this.getHead(), other.getHead()});
			while (!stack.isEmpty()) {
				Node.Op[] nodes = stack.pop();

				// add children of current node
				for (Node.Op rightChild : nodes[1].getNext()) {
					// push new pair of nodes onto stack
					Node.Op leftChild = matches.get(rightChild);
					if (leftChild == null) {
						leftChild = this.createNode(rightChild.getArtifact());
						leftChild.setSequenceNumber(rightChild.getSequenceNumber());
						matches.put(rightChild, leftChild);
						stack.push(new Node.Op[]{leftChild, rightChild});
					}
					nodes[0].addChild(leftChild);
				}
			}
		}


		/**
		 * Checks if the alignments of this pog and the other pog are compatible.
		 */
		//private
		default void checkAlignment(PartialOrderGraph.Op other) {
			// try to traverse other pog until the very end. if this is not possible the alignments are not compatible.
			// NOTE: use NOT_MATCHED_SEQUENCE_NUMBER instead of shared. anything in other that is not NOT_MATCHED_SEQUENCE_NUMBER is shared.

		}


		default void checkConsistency() {
			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node> stack = new Stack<>();
			stack.push(this.getHead());
			Set<PartialOrderGraph.Node> visited = new HashSet<>();

			while (!stack.isEmpty()) {
				Node node = stack.pop();

				if (visited.contains(node)) {
					StringBuilder sb = new StringBuilder();
					if (node.getArtifact() != null) {
						at.jku.isse.ecco.tree.Node current = node.getArtifact().getContainingNode();
						while (current != null) {
							sb.append(current + " - ");
							current = current.getParent();
						}
					}
					throw new EccoException("The same partial order graph node is being visited twice (this indicates a cycle)! " + sb);
				} else
					visited.add(node);

				// add children of current node
				for (Node child : node.getNext()) {
					counters.putIfAbsent(child, 0);
					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
					// check if all parents of the node have been processed
					if (counter >= child.getPrevious().size()) {
						// remove node from counters
						counters.remove(child);
						// push node onto stack
						stack.push(child);
					}
				}
			}

			if (!counters.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				if (!this.getHead().getNext().isEmpty() && this.getHead().getNext().iterator().next().getArtifact() != null) {
					at.jku.isse.ecco.tree.Node current = this.getHead().getNext().iterator().next().getArtifact().getContainingNode();
					while (current != null) {
						sb.append(current + " - ");
						current = current.getParent();
					}
				}
				throw new EccoException("Not all partial order graph nodes can be reached (this indicates a cycle or an orphan node without parent)! " + sb);
			}
		}


		/**
		 * Creates a (temporary) partial order graph from a given list of artifacts.
		 *
		 * @param artifacts Sequence of artifacts from which to create a partial order graph.
		 * @return The created partial order graph containing the provided artifacts.
		 */
		//private
		default PartialOrderGraph.Op fromList(List<? extends Artifact.Op<?>> artifacts) {
			PartialOrderGraph.Op other = this.createPartialOrderGraph(); // create new partial order graph
			Node.Op current = other.getHead(); // start at head
			for (Artifact.Op<?> artifact : artifacts) {
				current = current.addChild(other.createNode(artifact));
			}
			current.addChild(other.getTail()); // finish at tail

			if (artifacts.size() > 0) {
				// remove link between head and tail
				Node.Op head = other.getHead();
				Node.Op tail = other.getTail();
				head.removeChild(tail);
			}

			return other;
		}


		/**
		 * Trims the partial order graph by removing all symbols that are not contained in the collection of given symbols.
		 *
		 * @param symbols Symbols to keep.
		 */
		default void trim(Collection<? extends Artifact.Op<?>> symbols) {
			// for every node
			LinkedList<Node.Op> stack = new LinkedList<>();
			stack.push(this.getHead());
			while (!stack.isEmpty()) {
				Node.Op current = stack.pop();

				// if it is not contained in symbols remove node and connect all its parents to all its children
				if (current.getArtifact() != null && !symbols.contains(current.getArtifact())) {

					// connect every parent
					for (Node.Op parent : new ArrayList<>(current.getPrevious())) {
						// to every child, unless already connected (e.g. another removed sibling
						// already bypassed this same parent to this same child - a duplicate edge
						// here would silently corrupt the graph's true branching degree)
						for (Node.Op child : current.getNext()) {
							if (!parent.getNext().contains(child)) {
								parent.addChild(child);
							}
						}
						// and remove it as child from parent
						parent.removeChild(current);
					}
					// remove all children from current node (and subsequently the current node as parent of its children) and push children onto stack
					for (Node.Op child : new ArrayList<>(current.getNext())) {
						current.removeChild(child);
						stack.push(child);
					}
				} else {
					for (Node.Op child : current.getNext()) {
						stack.push(child);
					}
				}
			}
		}


		default void updateArtifactReferences() {
			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(this.getHead());

			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();

				if (node.getArtifact() != null && node.getArtifact().hasReplacingArtifact()) {
					Artifact.Op<?> replacing = node.getArtifact().getReplacingArtifact();
					// node's own sequence number is unaffected by which artifact it wraps, so this
					// isn't strictly needed for the node itself anymore - kept so the replacing
					// artifact's own (cosmetic/display) sequence number stays consistent too, sourced
					// from the node (authoritative) rather than the old artifact (see
					// Node.getSequenceNumber()'s javadoc).
					replacing.setSequenceNumber(node.getSequenceNumber());
					node.setArtifact(replacing);
				}

				// add children of current node
				for (Node.Op child : node.getNext()) {
					counters.putIfAbsent(child, 0);
					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
					// check if all parents of the node have been processed
					if (counter >= child.getPrevious().size()) {
						// remove node from counters
						counters.remove(child);
						// push node onto stack
						stack.push(child);
					}
				}
			}
		}


		default Collection<List<Node.Op>> computeAllOrders() {
			Map<Node.Op, Integer> nodes = new HashMap<>();
			nodes.put(this.getHead(), 0);
			return this.computeAllOrdersRec(nodes);
		}

		//private
		default Collection<List<Node.Op>> computeAllOrdersRec(Map<Node.Op, Integer> nodes) {
			Collection<List<Node.Op>> orders = new ArrayList<>();
			// for every node in current match state
			for (Node.Op node : nodes.keySet()) {
				// check if all parents of the node have been processed
				if (nodes.getOrDefault(node, 0) >= node.getPrevious().size()) {
					// clone the match state
					Map<Node.Op, Integer> nextNodes = new HashMap<>(nodes);
					// remove current node from match state
					nextNodes.remove(node);
					// add current node's children to match state
					for (Node.Op childNode : node.getNext()) {
						// add child node or, if it already existed, increases its counter
						nextNodes.putIfAbsent(childNode, 0);
						nextNodes.computeIfPresent(childNode, (op, integer) -> integer + 1);
					}
					// continue recursively with next match state
					Collection<List<Node.Op>> currentOrders = this.computeAllOrdersRec(nextNodes);

					// prefix every order in orders with current order
					for (List<Node.Op> order : currentOrders) {
						order.add(0, node);
					}
					// add current orders to all orders
					orders.addAll(currentOrders);
				}
			}
			// if there is no order add an empty list
			if (nodes.isEmpty() || orders.isEmpty()) {
				orders.add(new LinkedList<>());
			}
			// return orders
			return orders;
		}


		/**
		 * The alignment algorithm {@link #align(Op)} actually uses, via {@link #alignMemoizedBacktracking(Op)}.
		 * Factorial in the number of concurrent unresolved branches (see {@link #collectNodeSequencings()}),
		 * but - unlike {@link #directPoaAlignment(Op)} - correct: it tries every relative ordering of a
		 * branch's children explicitly, so it always finds the true best alignment regardless of which
		 * order {@code other} needs them in.
		 */
		default MutableIntObjectMap<Node.Op> iterativeLcsAlignment(PartialOrderGraph.Op other) {
			Node.Op[][] thisPaths = this.collectNodeSequencings();
			Node.Op[][] otherPaths = other.collectNodeSequencings();

			Collection<MutableIntObjectMap<Node.Op>> alignmentResults = new LinkedList<>();

			for (Node.Op[] thisPath : thisPaths){
				for (Node.Op[] otherPath : otherPaths){
					alignmentResults.add(this.alignPaths(thisPath, otherPath));
				}
			}

			return alignmentResults.stream()
					.max(Comparator.comparingInt(MutableIntObjectMap::size))
					.orElseThrow(NoSuchElementException::new);
		}


		/**
		 * NOT wired into {@link #align(Op)}/{@link #merge(Op)} - {@link #alignMemoizedBacktracking(Op)}
		 * calls {@link #iterativeLcsAlignment(Op)} instead, deliberately. Wiring this in on 2026-07-08
		 * broke {@code PartialOrderGraphTest.mergingWithBranchesWorks()}/{@code mergeTest()} (caught by
		 * running the full suite, exactly as intended, and immediately reverted) - see
		 * {@code DirectPoaAlignmentSpikeTest.KNOWN_BUG_concurrentBranchOrderConflictsWithOther_...} for
		 * a minimal reproduction. <b>Known correctness bug, not just a slower-but-safe fallback:</b>
		 * this fixes ONE arbitrary topological order of {@code this} up front (whatever
		 * {@link #collectNodes()}'s stack traversal happens to produce) and then treats each node's
		 * position in that one order as an implicit ordering constraint - including between sibling
		 * nodes that aren't actually ordered relative to each other in the graph. When {@code other}
		 * needs such siblings matched in the opposite order from the one {@link #collectNodes()}
		 * happened to pick, this can silently under-match (find fewer matches than the true optimum)
		 * rather than fail loudly. A correct fix needs to explore both relative orderings of a branch's
		 * children when they conflict with {@code other}'s order - i.e. still needs something like
		 * {@link #collectNodeSequencings()}'s permutation trying, just scoped to genuinely-conflicting
		 * branches instead of applied globally, which is more involved than the swap attempted here.
		 * <p>
		 * Direct DP alignment over the two DAGs' topological structure, without enumerating
		 * linearizations via {@link #collectNodeSequencings()}/{@code Permutation.generatePermutations()}
		 * (the source of {@link #iterativeLcsAlignment(Op)}'s factorial blowup in the number of
		 * concurrent unresolved branches). This is the "Partial Order Alignment" technique from
		 * bioinformatics (Lee, Grasso &amp; Sharlow, 2002): generalize the classic Needleman-Wunsch/LCS
		 * recurrence - where cell (i,j) depends on the single previous cell (i-1,j-1)/(i-1,j)/(i,j-1) -
		 * so that "the previous position" becomes "the max over all direct predecessors in the DAG".
		 * A branch point is then handled implicitly by having multiple predecessor cells to max over,
		 * rather than explicitly by trying every ordering of its children beforehand.
		 * <p>
		 * {@code this} and {@code other} are each visited once in topological order (the same order
		 * {@link #collectNodes()} already produces, no enumeration needed), so complexity is
		 * O(E_this * E_other) - the sum, over every cell, of (in-degree in this) * (in-degree in
		 * other) - which collapses to roughly O(V_this * V_other) for typical low-branching graphs,
		 * regardless of how many concurrent branches exist, instead of O(V_this! * V_other!).
		 * <p>
		 * Matches {@link #iterativeLcsAlignment(Op)}'s actual optimization objective (confirmed by
		 * reading {@link #lcsNonMatchStep}: it simply keeps whichever of the two skip options has more
		 * matches so far) - maximize the number of matched nodes, with no differential cost between
		 * skipping a node in {@code this} vs. in {@code other} (the class javadoc above describing a
		 * "skip other costs 1" asymmetry doesn't appear to be reflected in the actual DP).
		 */
		default MutableIntObjectMap<Node.Op> directPoaAlignment(PartialOrderGraph.Op other) {
			List<Node.Op> thisNodes = this.collectNodes();
			List<Node.Op> otherNodes = other.collectNodes();

			int n = thisNodes.size();
			int m = otherNodes.size();
			if (n == 0 || m == 0) {
				return IntObjectMaps.mutable.empty();
			}

			Map<Node.Op, Integer> thisIndex = new HashMap<>();
			for (int i = 0; i < n; i++) thisIndex.put(thisNodes.get(i), i);
			Map<Node.Op, Integer> otherIndex = new HashMap<>();
			for (int j = 0; j < m; j++) otherIndex.put(otherNodes.get(j), j);

			int[][] score = new int[n][m];
			// 0 = base case (0,0), 1 = match, 2 = skip this-node, 3 = skip other-node
			byte[][] action = new byte[n][m];
			int[][] backA = new int[n][m];
			int[][] backB = new int[n][m];

			for (int i = 0; i < n; i++) {
				Node.Op nodeA = thisNodes.get(i);
				Artifact<?> artifactA = nodeA.getArtifact();

				for (int j = 0; j < m; j++) {
					if (i == 0 && j == 0) {
						continue; // score/action/backA/backB already 0 - this is the base case
					}

					Node.Op nodeB = otherNodes.get(j);
					Artifact<?> artifactB = nodeB.getArtifact();

					int best = Integer.MIN_VALUE;
					byte bestAction = 0;
					int bestA = -1, bestB = -1;

					boolean canMatch = i > 0 && j > 0 && artifactA != null && artifactA.getData() != null
							&& artifactB != null && artifactA.getData().equals(artifactB.getData());
					if (canMatch) {
						for (Node.Op predA : nodeA.getPrevious()) {
							int pa = thisIndex.get(predA);
							for (Node.Op predB : nodeB.getPrevious()) {
								int pb = otherIndex.get(predB);
								int candidate = score[pa][pb] + 1;
								if (candidate > best) {
									best = candidate;
									bestAction = 1;
									bestA = pa;
									bestB = pb;
								}
							}
						}
					}
					if (i > 0) {
						for (Node.Op predA : nodeA.getPrevious()) {
							int pa = thisIndex.get(predA);
							int candidate = score[pa][j];
							if (candidate > best) {
								best = candidate;
								bestAction = 2;
								bestA = pa;
								bestB = -1;
							}
						}
					}
					if (j > 0) {
						for (Node.Op predB : nodeB.getPrevious()) {
							int pb = otherIndex.get(predB);
							int candidate = score[i][pb];
							if (candidate > best) {
								best = candidate;
								bestAction = 3;
								bestA = -1;
								bestB = pb;
							}
						}
					}

					score[i][j] = best;
					action[i][j] = bestAction;
					backA[i][j] = bestA;
					backB[i][j] = bestB;
				}
			}

			// traceback from (this.tail, other.tail) - guaranteed to be the last node in each
			// topological order, since every node eventually flows into it
			MutableIntObjectMap<Node.Op> result = IntObjectMaps.mutable.empty();
			int i = n - 1, j = m - 1;
			while (!(i == 0 && j == 0)) {
				byte a = action[i][j];
				if (a == 1) {
					result.put(thisNodes.get(i).getSequenceNumber(), otherNodes.get(j));
					int pa = backA[i][j], pb = backB[i][j];
					i = pa;
					j = pb;
				} else if (a == 2) {
					i = backA[i][j];
				} else if (a == 3) {
					j = backB[i][j];
				} else {
					break;
				}
			}

			return result;
		}


		default MutableIntObjectMap<Node.Op> alignPaths(Node.Op[] thisNodesArray, Node.Op[] otherNodesArray){

			if (thisNodesArray.length == 0 || otherNodesArray.length == 0){
				return IntObjectMaps.mutable.empty();
			}

			// consider a matrix with the first dimension being the nodes in this pog
			// and the second dimension being the nodes in the other pog
			// LCS is usually performed iteratively by filling this matrix one column after the other
			// in order to fill a column, only the last column is needed
			// in order to not use too much memory, the other columns are therefore not saved
			// instead, only two arrays are used.

			// This array represents the last column.
			// Every index has a mapping of sequence-numbers to nodes of the other pog (the index of the node in the array)
			MutableIntIntMap[] lastColumn;
			MutableIntIntMap[] currentColumn = new IntIntHashMap[otherNodesArray.length];
			int currentColumnNumber = 0;

			for (int i = 0; i < thisNodesArray.length; i++) {
				lastColumn = currentColumn;
				currentColumn = new IntIntHashMap[otherNodesArray.length];
				for (int j = 0; j < otherNodesArray.length; j++) {
					this.lcsStep(thisNodesArray, otherNodesArray, currentColumnNumber, j, lastColumn, currentColumn);
				}
				currentColumnNumber++;
			}

			MutableIntIntMap resultIndexMap = currentColumn[otherNodesArray.length - 1];
			MutableIntObjectMap<Node.Op> resultMap = IntObjectMaps.mutable.empty();
			if (resultIndexMap == null){
				return resultMap;
			}
			resultIndexMap.forEachKey(key -> {
				int value = resultIndexMap.get(key);
				resultMap.put(key, otherNodesArray[value]);
			});

			return resultMap;
		}

		private void lcsStep(Node.Op[] thisNodesArray, Node.Op[] otherNodesArray,
							 int thisIndex, int otherIndex,
							 MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn){
			Node.Op thisNode = thisNodesArray[thisIndex];
			Node.Op otherNode = otherNodesArray[otherIndex];
			Artifact<?> thisArtifact = thisNode.getArtifact();
			Artifact<?> otherArtifact = otherNode.getArtifact();
			if (thisArtifact != null && thisArtifact.getData() != null && otherArtifact != null && thisArtifact.getData().equals(otherArtifact.getData())){
				this.lcsMatchStep(thisNode.getSequenceNumber(), otherIndex, thisIndex, lastColumn, currentColumn);
			} else {
				this.lcsNonMatchStep(otherIndex, thisIndex, lastColumn, currentColumn);
			}
		}

		private void lcsMatchStep(int sequenceNumber, int otherIndex, int thisIndex, MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn){
			MutableIntIntMap sequenceNumberMap;
			if (thisIndex == 0 || otherIndex == 0){
				sequenceNumberMap = new IntIntHashMap();
			} else {
				sequenceNumberMap = new IntIntHashMap(lastColumn[otherIndex - 1]);
			}
			sequenceNumberMap.put(sequenceNumber, otherIndex);
			currentColumn[otherIndex] = sequenceNumberMap;
		}

		private void lcsNonMatchStep(int otherIndex, int thisIndex, MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn) {
			MutableIntIntMap sequenceNumberMap;
			MutableIntIntMap lastThisCurrentOtherMap = thisIndex == 0 ? null : lastColumn[otherIndex];
			MutableIntIntMap currentThisLastOtherMap = otherIndex == 0 ? null : currentColumn[otherIndex - 1];

			if (lastThisCurrentOtherMap == null && currentThisLastOtherMap == null) {
				sequenceNumberMap = new IntIntHashMap();
			} else if (currentThisLastOtherMap == null || (lastThisCurrentOtherMap != null && lastThisCurrentOtherMap.size() > currentThisLastOtherMap.size())) {
				sequenceNumberMap = new IntIntHashMap(lastThisCurrentOtherMap);
			} else {
				sequenceNumberMap = new IntIntHashMap(currentThisLastOtherMap);
			}
			currentColumn[otherIndex] = sequenceNumberMap;
		}
	}


	interface Node extends Persistable {
		Collection<? extends Node> getPrevious();

		Collection<? extends Node> getNext();

		Artifact<?> getArtifact();

		/**
		 * This node's own position within its own graph - NOT the same thing as
		 * {@code getArtifact().getSequenceNumber()}, and deliberately so: an artifact object can be
		 * legitimately shared by nodes in more than one PartialOrderGraph (see
		 * pog-merge-shared-artifact-bug), so a position that lived on the artifact would let one
		 * graph's bookkeeping clobber another's merely because they happen to reference the same
		 * artifact. Defaults to delegating to the artifact for any implementation that doesn't
		 * override it (backward compatible with backends that never adopted their own storage for
		 * this - see SerPartialOrderGraphNode for the real, node-owned implementation actually used).
		 * The mutator is on {@link Op} only - Artifact.setSequenceNumber() is likewise only on
		 * {@link Artifact.Op}.
		 */
		default int getSequenceNumber() {
			Artifact<?> artifact = this.getArtifact();
			return artifact != null ? artifact.getSequenceNumber() : PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER;
		}

		default void traverse(NodeVisitor visitor) {
			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node> stack = new Stack<>();
			stack.push(this);

			while (!stack.isEmpty()) {
				Node node = stack.pop();

				visitor.visit(node);

				// add children of current node
				for (Node child : node.getNext()) {
					counters.putIfAbsent(child, 0);
					int counter = counters.computeIfPresent(child, (op, integer) -> integer + 1);
					// check if all parents of the node have been processed
					if (counter >= child.getPrevious().size()) {
						// remove node from counters
						counters.remove(child);
						// push node onto stack
						stack.push(child);
					}
				}
			}
		}

		interface NodeVisitor {
			void visit(Node node);
		}


		interface Op extends Node {
			Collection<? extends Node.Op> getPrevious();

			Collection<? extends Node.Op> getNext();

			@Override
			Artifact.Op<?> getArtifact();

			void setArtifact(Artifact.Op<?> artifact);

			/** See {@link Node#getSequenceNumber()}. Default mirrors that one's artifact-delegating fallback. */
			default void setSequenceNumber(int sequenceNumber) {
				Artifact.Op<?> artifact = this.getArtifact();
				if (artifact != null) {
					artifact.setSequenceNumber(sequenceNumber);
				}
			}

			Node.Op addChild(Node.Op child);

			void removeChild(Node.Op child);

			default void traverse(NodeVisitor visitor) {
				visitor.visit(this);

				throw new UnsupportedOperationException("Not yet implemented.");
			}

			interface NodeVisitor {
				void visit(Node.Op node);
			}

			// compare node itself as well as previous and next nodes
			boolean equalsCompletely(Object o);

		}
	}

	// compares nodes using equals()
	// does account for different number of occurrences in collections
	static boolean nodeCollectionsAreEqual(Collection<Node.Op> leftCollection, Collection<Node.Op> rightCollection){
		if (leftCollection == null && rightCollection == null){ return true; }
		if (leftCollection == null){ return false; }
		if (rightCollection == null){ return false; }
		boolean allLeftNodesInBothListSameTimes = leftCollection.stream().allMatch(n -> PartialOrderGraph.nodeOccursSameNumberOfTimes(n, leftCollection, rightCollection));
		boolean allRightNodesInBothListSameTimes = rightCollection.stream().allMatch(n -> PartialOrderGraph.nodeOccursSameNumberOfTimes(n, leftCollection, rightCollection));
		return allLeftNodesInBothListSameTimes && allRightNodesInBothListSameTimes;
	}

	// Compares occurrence of node in collections. When comparing two nodes, previous and next nodes are not compared.
	private static boolean nodeOccursSameNumberOfTimes(Node.Op node, Collection<Node.Op> leftCollection, Collection<Node.Op> rightCollection) {
		assert node != null;
		long numOfLeftNodes = leftCollection.stream().filter(node::equals).count();
		long numOfRightNodes = rightCollection.stream().filter(node::equals).count();
		return numOfLeftNodes == numOfRightNodes;
	}

	// compares nodes using equalsCompletely()
	// does account for different number of occurrences in collections
	static boolean nodeCollectionsAreCompletelyEqual(Collection<Node.Op> leftCollection, Collection<Node.Op> rightCollection){
		if (leftCollection == null && rightCollection == null){ return true; }
		if (leftCollection == null){ return false; }
		if (rightCollection == null){ return false; }
		boolean allLeftNodesInBothListSameTimes = leftCollection.stream().allMatch(n -> PartialOrderGraph.nodeOccursSameNumberOfTimesCompletely(n, leftCollection, rightCollection));
		boolean allRightNodesInBothListSameTimes = rightCollection.stream().allMatch(n -> PartialOrderGraph.nodeOccursSameNumberOfTimesCompletely(n, leftCollection, rightCollection));
		return allLeftNodesInBothListSameTimes && allRightNodesInBothListSameTimes;
	}

	// Compares occurrence of node in collections. When comparing two nodes, previous and next nodes are also compared.
	private static boolean nodeOccursSameNumberOfTimesCompletely(Node.Op node, Collection<Node.Op> leftCollection, Collection<Node.Op> rightCollection){
		assert node != null;
		long numOfLeftNodes = leftCollection.stream().filter(node::equalsCompletely).count();
		long numOfRightNodes = rightCollection.stream().filter(node::equalsCompletely).count();
		return numOfLeftNodes == numOfRightNodes;
	}
}
