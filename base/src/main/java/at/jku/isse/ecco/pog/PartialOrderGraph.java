package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;

import java.util.*;

public interface PartialOrderGraph extends Persistable {

	public static final int INITIAL_SEQUENCE_NUMBER = 0;
	public static final int NOT_MATCHED_SEQUENCE_NUMBER = -1;
	public static final int UNASSIGNED_SEQUENCE_NUMBER = -2;
	public static final int HEAD_SEQUENCE_NUMBER = -3;
	public static final int TAIL_SEQUENCE_NUMBER = -4;


	public Node getHead();

	public Collection<? extends Node> collectNodes();


	public interface Op extends PartialOrderGraph {

		public Node.Op getHead();

		public Node.Op getTail();

		public int getMaxIdentifier();

		public void setMaxIdentifier(int value);

		public void incMaxIdentifier();

		public default Collection<Node.Op> collectNodes() { // TODO: this potentially adds the same nodes multiple times!
			Collection<Node.Op> nodes = new ArrayList<>();

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

		public Node.Op createNode(Artifact.Op<?> artifact);


		public PartialOrderGraph.Op createPartialOrderGraph();


		// #############################################################################################################


		/**
		 * Creates a new partial order graph (see {@link #fromList(List)}) reflecting the given list of artifacts and aligns it to this partial order graph (see {@link #align(Op)}).
		 *
		 * @param artifacts
		 */
		public default void align(List<? extends Artifact.Op<?>> artifacts) {
			this.align(this.fromList(artifacts));
		}

		/**
		 * Aligns the given partial order graph (i.e. sets the identifiers of its artifacts) to this partial order graph.
		 * <p>
		 * Skipping a node in LEFT (this) costs nothing.
		 * Matching a node costs nothing.
		 * Skipping a node in RIGHT (other) costs 1.
		 *
		 * @param other
		 */
		public default void align(PartialOrderGraph.Op other) {
			Alignment leftMatchState = new Alignment();
			leftMatchState.nodes.add(this.getHead());

			Map<Node.Op, Integer> rightNodes = new HashMap<>();
			rightNodes.put(other.getHead(), 0);

			this.alignRec(leftMatchState, rightNodes, 0, Integer.MAX_VALUE);
		}

		//private
		default int alignRec(Alignment leftMatchState, Map<Node.Op, Integer> rightNodes, int currentCost, int bestCost) {
			// traverse RIGHT in all possible orders

			// move to next node in RIGHT. if there are multiple options ... consider every order? unless a traversal state without skipped artifacts was found, in which case no other orders need to be considered.

			// return (or include in traversal state) a list of skipped artifacts so that i can check whether a relevant artifact has been skipped and it is worth going back? maybe even return list of traversal states?


			// if current cost is above best cost we do not need to continue
			if (currentCost >= bestCost)
				return currentCost;

			// if we finished traversing RIGHT we are done
			if (rightNodes.isEmpty())
				return currentCost;

			int localBestCost = bestCost;

			// for every node in current RIGHT match state
			for (Node.Op currentRightNode : rightNodes.keySet()) {
				// check if all parents of the node have been processed
				if (rightNodes.getOrDefault(currentRightNode, 0) >= currentRightNode.getPrevious().size()) {
					// process the node

					// find all possible matches in LEFT in the form of match states (containing list of skipped nodes?)
					// for current node in RIGHT find all possible matches in LEFT
					// return list of traversal states (one per match that was found) ... ??? ... and order them by number of skipped artifacts. start with smallest number of skipped artifacts.
					List<Alignment> matchStates = this.collectMatches(leftMatchState, currentRightNode.getArtifact());
					for (Alignment nextLeftMatchState : matchStates) {
						// ... clone the RIGHT match state
						Map<Node.Op, Integer> nextRightNodes = new HashMap<>(rightNodes);
						// ... remove current node from match state
						nextRightNodes.remove(currentRightNode);
						// ... add current node's children to match state
						for (Node.Op childNode : currentRightNode.getNext()) {
							// add child node
							// adds the current node or, if it already existed, increases its counter
							nextRightNodes.putIfAbsent(childNode, 0);
							nextRightNodes.computeIfPresent(childNode, (op, integer) -> integer + 1);
						}
						// advance match state for right by current node and continue recursively with all other possible nodes in right match state
						// continue recursively with next match state
						int tempCost = this.alignRec(nextLeftMatchState, nextRightNodes, currentCost, localBestCost);
						if (tempCost < localBestCost) {
							localBestCost = tempCost;
							// set sequence number of artifact to the one of the matching artifact that was found in LEFT
							if (currentRightNode.getArtifact() != null)
								currentRightNode.getArtifact().setSequenceNumber(nextLeftMatchState.matchedArtifact != null ? nextLeftMatchState.matchedArtifact.getSequenceNumber() : NOT_MATCHED_SEQUENCE_NUMBER);
						}
					}

					// finally also consider skipping the current node in RIGHT (only if no traversal state that did not skip a single artifact was found).
					// this is done by not changing the LEFT match state and increasing the cost by 1 before advancing the RIGHT match state past the current node
					{
						// clone the RIGHT match state
						Map<Node.Op, Integer> nextRightNodes = new HashMap<>(rightNodes);
						// remove current node from match state
						nextRightNodes.remove(currentRightNode);
						// add current node's children to match state
						for (Node.Op childNode : currentRightNode.getNext()) {
							// add child node or, if it already existed, increases its counter
							nextRightNodes.putIfAbsent(childNode, 0);
							nextRightNodes.computeIfPresent(childNode, (op, integer) -> integer + 1);
						}
						// advance match state for right by current node and continue recursively with all other possible nodes in right match state
						// continue recursively with next match state
						int tempCost = this.alignRec(leftMatchState, nextRightNodes, currentCost + 1, localBestCost);
						if (tempCost < localBestCost) {
							localBestCost = tempCost;
							// indicate that this artifact needs a new sequence number assigned
							if (currentRightNode.getArtifact() != null)
								currentRightNode.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER);
						}
					}

				}
			}

			return localBestCost;
		}

		// private
		default List<Alignment> collectMatches(Alignment startMatchState, Artifact.Op artifact) {
			List<Alignment> matchStates = new LinkedList<>(); // TODO: linked list for insertion sort? sorted by the number of skipped artifacts?

			Map<Node.Op, Integer> counters = new HashMap<>();
			// fill stack using nodes in startMatchState
			Stack<Node.Op> stack = new Stack<>();
			for (Node.Op initialNode : startMatchState.nodes) {
				stack.push(initialNode);
			}

			// for every node in start match state ...
			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();

				// check for match
				if (node.getArtifact() == artifact || node.getArtifact() != null && node.getArtifact().getData() != null && artifact != null && node.getArtifact().getData().equals(artifact.getData())) {
					// copy start match state
					Alignment resultMatchState = new Alignment(startMatchState);
					resultMatchState.matchedArtifact = node.getArtifact();
					// remove current node and all its parent nodes from result match state and add all (other) children of encountered fork nodes to result match state. stop at nodes in start match state.
					LinkedList<Node.Op> upwardsStack = new LinkedList<>();
					upwardsStack.add(node);
					Node.Op previous = null;
					while (!upwardsStack.isEmpty()) {
						// remove current node from stack
						Node.Op current = upwardsStack.pop();
						// remove current node from result match state
						resultMatchState.nodes.remove(current);

						// add children of current node to result match state
						for (Node.Op child : current.getNext()) {
							if (child != previous)
								resultMatchState.nodes.add(child);
						}
						previous = current;
						// add parents to stack
						for (Node.Op parent : current.getPrevious()) {
							// but not past the "barrier" nodes from which we started
							if (!startMatchState.nodes.contains(current))
								upwardsStack.push(parent);
							else
								resultMatchState.nodes.remove(parent);
						}

					}
					matchStates.add(resultMatchState);
				} else {
					// add children of current node (ONLY IF NO MATCH WAS FOUND)
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

			return matchStates;
		}


		/**
		 * Creates a new partial order graph reflecting the given list of artifacts and merges it into this partial order graph.
		 *
		 * @param artifacts
		 */
		public default void merge(List<? extends Artifact.Op<?>> artifacts) {
			this.merge(this.fromList(artifacts));
		}

		/**
		 * @param other
		 */
		public default void merge(PartialOrderGraph.Op other) {
			// align other graph to this graph
			this.align(other);

			// collect shared symbols
			Map<Integer, Node.Op> shared = new HashMap<>();
			Collection<Node.Op> thisNodes = this.collectNodes();
			Collection<Node.Op> otherNodes = other.collectNodes();
			for (Node.Op otherNode : otherNodes) {
				if (otherNode.getArtifact() != null && otherNode.getArtifact().getSequenceNumber() != PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
					shared.put(otherNode.getArtifact().getSequenceNumber(), thisNodes.stream().filter(thisNode -> thisNode.getArtifact() != null && thisNode.getArtifact().getSequenceNumber() == otherNode.getArtifact().getSequenceNumber()).findFirst().get());
				}
			}

			// merge other partial order graph into this partial order graph
			this.mergeRec(this.getHead(), other.getHead(), shared);

			// check if graph has cycles and throw exception if it does
			this.checkConsistency();
		}

		// private
		default void mergeRec(Node.Op left, Node.Op right, Map<Integer, Node.Op> shared) {
			//System.out.println("MERGE: LEFT: " + left + " / RIGHT: " + right);

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// add all unshared (i.e. new) symbols in right children to left children. add tail as their child. assign new sequence number to them.
				for (Node.Op childRight : right.getNext()) {
					// check if symbol is unshared
					if (childRight.getArtifact() != null && !shared.containsKey(childRight.getArtifact().getSequenceNumber())) { // && childRight.getArtifact().getSequenceNumber() == PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
						// create node for new artifact
						Node.Op newLeft = this.createNode(childRight.getArtifact());
						// assign new sequence number to new artifact
						newLeft.getArtifact().setSequenceNumber(this.getMaxIdentifier());
						this.incMaxIdentifier();
						// add it to left
						left.addChild(newLeft);
						// add tail as child of new node
						newLeft.addChild(this.getTail());
						//System.out.println("Added new node " + newLeft + " as child to node " + left);
					}
				}
			}

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// find shared symbols that are in left and not in right. REMOVE those that can be reached from right node because that means that the right graph is more restrictive.
				{
					Iterator<Node.Op> it = left.getNext().iterator();
					while (it.hasNext()) {
						Node.Op childLeft = it.next();
						// check if left symbol is shared
						if (childLeft.getArtifact() == null || childLeft.getArtifact() != null && shared.containsKey(childLeft.getArtifact().getSequenceNumber())) {
							Node.Op matchingChildRight = null;
							for (Node.Op childRight : right.getNext()) {
								//if (childLeft.getArtifact() != null && childLeft.getArtifact().getData() != null && childRight.getArtifact() != null && childLeft.getArtifact().getData().equals(childRight.getArtifact().getData())) {
								if (childLeft.getArtifact() != null && childLeft.getArtifact().equals(childRight.getArtifact())) {
									matchingChildRight = childRight;
									break;
								}
							}

							// when encountering a connection to a shared symbol that is in LEFT that is not in RIGHT
							if (matchingChildRight == null) {
								if (canReach(right, childLeft.getArtifact())) {
									// we do not need left connection -> delete it
									it.remove();
									childLeft.getPrevious().remove(left);
									//System.out.println("Removed node " + childLeft + " as child from node " + left);
								}
							}
						}
					}
				}
			}

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// find shared symbols that are in right and not in left. ADD those that cannot be reached from left node because that means the right graph is more restrictive.
				for (Node.Op childRight : right.getNext()) {
					// check if right symbol is shared
					if (childRight.getArtifact() == null || childRight.getArtifact() != null && shared.containsKey(childRight.getArtifact().getSequenceNumber())) {
						Node.Op matchingChildLeft = null;
						for (Node.Op childLeft : left.getNext()) {
							//if (childRight.getArtifact() != null && childRight.getArtifact().getData() != null && childLeft.getArtifact() != null && childRight.getArtifact().getData().equals(childLeft.getArtifact().getData())) {
							if (childRight.getArtifact() != null && childRight.getArtifact().equals(childLeft.getArtifact())) {
								matchingChildLeft = childLeft;
								break;
							}
						}

						// when encountering a connection to a shared symbol that is in RIGHT that is not in LEFT
						if (matchingChildLeft == null) {
							if (!canReach(left, childRight.getArtifact())) {
								// we need right connection -> add it
								if (childRight.getArtifact() == null) { // this is ugly for checking if it is the tail!
									left.addChild(this.getTail());
									//System.out.println("Added new node " + this.getTail() + " as child to node " + left);
								} else {
									Node.Op childLeft = shared.get(childRight.getArtifact().getSequenceNumber());
									left.addChild(childLeft);
									//System.out.println("Added new node " + childLeft + " matching " + childRight + " as child to node " + left);
								}
							}
						}
					}
				}
			}

			// find matching nodes (shared or unshared) in children of left and right
			for (Node.Op childLeft : left.getNext()) {
				boolean foundMatchingChildRight = false;
				for (Node.Op childRight : right.getNext()) {
					//if ((childLeft.getArtifact() == null && childRight.getArtifact() == null) || (childLeft.getArtifact() != null && childRight.getArtifact() != null && childLeft.getArtifact().getData() != null && childRight.getArtifact() != null && childLeft.getArtifact().getData().equals(childRight.getArtifact().getData()))) {
					if ((childLeft.getArtifact() == null && childRight.getArtifact() == null) || (childLeft.getArtifact() != null && childLeft.getArtifact().equals(childRight.getArtifact()))) {
						this.mergeRec(childLeft, childRight, shared);
						foundMatchingChildRight = true;
						break;
					}
				}
				// if left is unshared and was not added from right before then rec right here
				if (!foundMatchingChildRight && childLeft.getArtifact() != null && !shared.containsKey(childLeft.getArtifact().getSequenceNumber()))
					this.mergeRec(childLeft, right, shared);
			}
		}

		/**
		 * Checks whether an artifact can be reached from a given node.
		 *
		 * @param node     The node to start from.
		 * @param artifact The artifact to look for.
		 * @return
		 */
		// private
		static boolean canReach(Node node, Artifact<?> artifact) {
//			LinkedList<Node> stack = new LinkedList<>();
//			stack.push(node);
//			while (!stack.isEmpty()) {
//				Node current = stack.pop();
//
//				if ((artifact == null && current.getArtifact() == null) || (artifact != null && current.getArtifact() != null && current.getArtifact().getSequenceNumber() == artifact.getSequenceNumber()))
//					return true;
//
//				for (Node child : current.getNext()) {
//					stack.push(child);
//				}
//			}
//			return false;

			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node> stack = new Stack<>();
			stack.push(node);

			while (!stack.isEmpty()) {
				Node current = stack.pop();

				// process node
				if ((artifact == null && current.getArtifact() == null) || (artifact != null && current.getArtifact() != null && current.getArtifact().getSequenceNumber() == artifact.getSequenceNumber()))
					return true;

				// add children of current node
				for (Node child : current.getNext()) {
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
			return false;
		}


		// #############################################################################################################


		public default void copy(PartialOrderGraph.Op other) {
			//if (this.getHead().getNext().size() != 1 || this.getHead().getNext().iterator().next() != this.getTail())
			if (!this.getHead().getNext().isEmpty())
				throw new EccoException("Partial order graph must be empty to copy another.");

			this.getHead().removeChild(this.getTail());
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


		default void checkConsistency() {
			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node> stack = new Stack<>();
			stack.push(this.getHead());
			Set<PartialOrderGraph.Node> visited = new HashSet<>();

			while (!stack.isEmpty()) {
				Node node = stack.pop();

				if (visited.contains(node))
					throw new EccoException("The same partial order graph node is being visited twice (this indicates a cycle)!");
				else
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
				throw new EccoException("Not all partial order graph nodes can be reached (this indicates a cycle or an orphan node without parent)!");
			}
		}


		/**
		 * Creates a (temporary) partial order graph from a given list of artifacts.
		 *
		 * @param artifacts
		 * @return
		 */
		// private
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
		public default void trim(Collection<? extends Artifact.Op<?>> symbols) {
			// for every node
			LinkedList<Node.Op> stack = new LinkedList<>();
			stack.push(this.getHead());
			while (!stack.isEmpty()) {
				Node.Op current = stack.pop();

				// check if it is contained in symbols
				if (current.getArtifact() != null && !symbols.contains(current.getArtifact())) {
					// if not remove node and connect all its parents to all its children

					// connect every parent
					for (Node.Op parent : current.getPrevious()) {
						// to every child
						for (Node.Op child : current.getNext()) {
							parent.addChild(child);
						}
						// and remove it as child from parent
						parent.removeChild(current);
					}
					// remove all children from current node (and subsequently the current node as parent of its children)
					for (Node.Op child : current.getNext()) {
						current.removeChild(child);
					}
				}

				for (Node.Op child : current.getNext()) {
					stack.push(child);
				}
			}
		}


		public default void updateArtifactReferences() {
			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(this.getHead());

			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();

				if (node.getArtifact() != null && node.getArtifact().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact.Op<?> replacing = node.getArtifact().<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
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


		public default Collection<List<Node.Op>> computeAllOrders() {
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

	}


	public class Alignment {
		public Set<Node.Op> nodes;
		public Artifact.Op<?> matchedArtifact;

		public Alignment() {
			this.nodes = new HashSet<>();
			this.matchedArtifact = null;
		}

		public Alignment(Alignment other) {
			this();
			this.nodes.addAll(other.nodes);
		}
	}


	public interface Node extends Persistable {
		public Collection<? extends Node> getPrevious();

		public Collection<? extends Node> getNext();

		public Artifact<?> getArtifact();

		public default void traverse(NodeVisitor visitor) {
			Map<PartialOrderGraph.Node, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node> stack = new Stack<>();
			stack.push(this);

			while (!stack.isEmpty()) {
				Node node = stack.pop();

				visitor.visit(this);

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

		public interface NodeVisitor {
			public void visit(Node node);
		}


		public interface Op extends Node {
			public Collection<Node.Op> getPrevious();

			public Collection<Node.Op> getNext();

			@Override
			public Artifact.Op<?> getArtifact();

			public void setArtifact(Artifact.Op<?> artifact);

			public Node.Op addChild(Node.Op child);

			public void removeChild(Node.Op child);

			public default void traverse(NodeVisitor visitor) {
				visitor.visit(this);

				throw new UnsupportedOperationException("Not yet implemented.");
			}

			public interface NodeVisitor {
				public void visit(Node.Op node);
			}

		}
	}

}
