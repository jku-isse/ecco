package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;

import java.util.*;

public interface PartialOrderGraph extends Persistable {
	int INITIAL_SEQUENCE_NUMBER = 1;
	int NOT_MATCHED_SEQUENCE_NUMBER = -1;
	int UNASSIGNED_SEQUENCE_NUMBER = -2;


	Node getHead();

	Collection<? extends Node> collectNodes();


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

		//private
		default void alignMemoizedBacktracking(PartialOrderGraph.Op other) {
			IntObjectMap<Node.Op> result = this.iterativeLcsAlignment(other);
			// set sequence number of matched artifacts
			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			result.forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
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
			int numMatchedNodes = (int) otherNodes.stream().filter(otherNode -> otherNode.getArtifact() != null && otherNode.getArtifact().getSequenceNumber() != PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER).count() + 2; // +2 because of head and tail
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
						if (canReach(nextNode, thisNode.getArtifact()))
							throw new EccoException("There is a cycle in the POG!");

			// CONSISTENCY: check for redundant connections: can any node be reached from any of the other nodes?
			for (Node.Op thisNode : this.collectNodes())
				for (Node.Op nextNode : thisNode.getNext())
					for (Node.Op nextNode2 : thisNode.getNext())
						if (nextNode != nextNode2 && nextNode.getArtifact() != null && canReach(nextNode2, nextNode.getArtifact()))
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
				} else if (otherNode.getArtifact().getSequenceNumber() == PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
					otherNode.getArtifact().setSequenceNumber(this.getMaxIdentifier());
					this.incMaxIdentifier();
				} else {
					for (Node.Op thisNode : thisNodes) {
						if (thisNode.getArtifact() != null && thisNode.getArtifact().getSequenceNumber() == otherNode.getArtifact().getSequenceNumber()) {
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
					nodeMap.put(otherNode, thisNode);
				}
				// add all next nodes that do not already exist
				for (Node.Op otherNextNode : otherNode.getNext()) {
					Node.Op thisNextNode = nodeMap.get(otherNextNode);
					if (thisNextNode == null) {
						thisNextNode = this.createNode(otherNextNode.getArtifact());
						nodeMap.put(otherNextNode, thisNextNode);
					}
					if (!thisNode.getNext().contains(thisNextNode)) {
						thisNode.addChild(thisNextNode);
					}
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
						if (otherChild != child && canReach(otherChild, child.getArtifact())) {
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
		 * Checks whether an artifact can be reached from a given node.
		 *
		 * @param node     The node to start from.
		 * @param artifact The artifact to look for.
		 * @return True if artifact could be reached from node, false otherwise.
		 */
		//private
		static boolean canReach(Node node, Artifact<?> artifact) {
//			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<Node> stack = new Stack<>();
			stack.add(node);
			Set<Node> stacked = new HashSet<>();
			stacked.add(node);

			while (!stack.isEmpty()) {
				Node current = stack.pop();

				// process node
				if ((artifact == null && current.getArtifact() == null) || (artifact != null && current.getArtifact() != null && current.getArtifact().getSequenceNumber() == artifact.getSequenceNumber()))
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
						// to every child
						for (Node.Op child : current.getNext()) {
							parent.addChild(child);
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
					replacing.setSequenceNumber(node.getArtifact().getSequenceNumber());
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


		default MutableIntObjectMap<Node.Op> iterativeLcsAlignment(PartialOrderGraph.Op other){
			List<Node.Op> thisNodes = this.collectNodes();
			thisNodes.remove(thisNodes.size() - 1); // remove tail
			thisNodes.remove(0); // remove head
			List<Node.Op> otherNodes = other.collectNodes();
			otherNodes.remove(otherNodes.size() - 1); // remove tail
			otherNodes.remove(0); // remove head
			Node.Op[] thisNodesArray = new Node.Op[thisNodes.size()];
			thisNodes.toArray(thisNodesArray);
			Node.Op[] otherNodesArray = new Node.Op[otherNodes.size()];
			otherNodes.toArray(otherNodesArray);

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
			// Every index has a mapping of sequence-numbers to nodes of the other pof (the index of the node in the array)
			MutableIntIntMap[] lastColumn;
			MutableIntIntMap[] currentColumn = new IntIntHashMap[otherNodesArray.length - 2];
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
				this.lcsMatchStep(thisArtifact.getSequenceNumber(), otherIndex, thisIndex, lastColumn, currentColumn);
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
