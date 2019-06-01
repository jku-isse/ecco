package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import java.util.*;

public interface PartialOrderGraph extends Persistable {

	public static final int INITIAL_SEQUENCE_NUMBER = 1;
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

		public default List<Node.Op> collectNodes() { // TODO: this potentially adds the same nodes multiple times!
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
			this.alignMemoizedFixed(other);
		}


		//private
		default void alignOld(PartialOrderGraph.Op other) {
			Alignment leftMatchState = new Alignment();
			leftMatchState.counters.put(this.getHead(), new ArrayList<>());

			Map<Node.Op, Integer> rightNodes = new HashMap<>();
			rightNodes.put(other.getHead(), 0);

			// compute lower bound on best cost that is theoretically possible
			int bestPossibleCost = 0;
			Collection<PartialOrderGraph.Node.Op> tempLeftNodes = this.collectNodes();
			Collection<PartialOrderGraph.Node.Op> tempRightNodes = other.collectNodes();
			for (PartialOrderGraph.Node tempRightNode : tempRightNodes) {
				boolean found = false;
				Iterator<PartialOrderGraph.Node.Op> it = tempLeftNodes.iterator();
				while (it.hasNext()) {
					PartialOrderGraph.Node.Op tempLeftNode = it.next();
					if (tempLeftNode.getArtifact() != null && tempLeftNode.getArtifact().getData() != null && tempRightNode.getArtifact() != null && tempLeftNode.getArtifact().getData().equals(tempRightNode.getArtifact().getData())) {
						it.remove();
						found = true;
						break;
					}
				}
				if (!found && tempRightNode.getArtifact() != null)
					bestPossibleCost++;
			}

			this.alignOldRec(leftMatchState, rightNodes, 0, Integer.MAX_VALUE, bestPossibleCost);
		}

		//private
		default int alignOldRec(Alignment leftMatchState, Map<Node.Op, Integer> rightNodes, int currentCost, int bestCost, int bestPossibleCost) {
			// traverse RIGHT in all possible orders

			// move to next node in RIGHT. if there are multiple options ... consider every order? unless a traversal state without skipped artifacts was found, in which case no other orders need to be considered.

			// return (or include in traversal state) a list of skipped artifacts so that i can check whether a relevant artifact has been skipped and it is worth going back? maybe even return list of traversal states?


			// if current cost is above best cost we do not need to continue
			if (currentCost >= bestCost)
				return currentCost;

			// if we finished traversing RIGHT we are done
			if (rightNodes.isEmpty())
				return currentCost;

			// if we reached best possible cost we are done
			if (bestCost <= bestPossibleCost)
				return Integer.MAX_VALUE;

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
						int tempCost = this.alignOldRec(nextLeftMatchState, nextRightNodes, currentCost, localBestCost, bestPossibleCost);
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
						int tempCost = this.alignOldRec(leftMatchState, nextRightNodes, currentCost + 1, localBestCost, bestPossibleCost);
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

		//private
		default List<Alignment> collectMatches(Alignment startMatchState, Artifact.Op artifact) {
			List<Alignment> matchStates = new LinkedList<>(); // TODO: linked list for insertion sort? sorted by the number of skipped artifacts?

			// initialize counters with match state counters
			Map<Node.Op, Integer> counters = new HashMap<>();
			for (Map.Entry<Node.Op, List<Node.Op>> entry : startMatchState.counters.entrySet()) {
				counters.put(entry.getKey(), entry.getValue().size());
			}
			// fill stack using nodes in startMatchState
			Stack<Node.Op> stack = new Stack<>();
			for (Node.Op initialNode : startMatchState.counters.keySet()) {
				if (counters.get(initialNode) >= initialNode.getPrevious().size()) {
					stack.push(initialNode);
					counters.remove(initialNode);
				}
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
						resultMatchState.counters.remove(current);

						// add children of current node to result match state
						for (Node.Op child : current.getNext()) {
							if (child != previous) {
								resultMatchState.counters.putIfAbsent(child, new ArrayList<>());
								resultMatchState.counters.get(child).add(current);
							}
						}
						previous = current;

						// add parents to stack
						for (Node.Op parent : current.getPrevious()) {
							// but not past the "barrier" nodes from which we started
							if (!startMatchState.counters.containsKey(current) || !startMatchState.counters.get(current).contains(parent)) {
								upwardsStack.push(parent);
							}
							//else
							resultMatchState.counters.remove(parent);
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


		//private
		default void alignTable(PartialOrderGraph.Op other) {
			// collect nodes
			Node.Op[] thisNodes = this.collectNodes().toArray(new Node.Op[0]);
			Node.Op[] otherNodes = other.collectNodes().toArray(new Node.Op[0]);
			// assign index as sequence number to every node
			for (int i = 0; i < thisNodes.length; i++)
				if (thisNodes[i].getArtifact() != null)
					thisNodes[i].getArtifact().setSequenceNumber(i);
			for (int j = 0; j < otherNodes.length; j++)
				if (otherNodes[j].getArtifact() != null)
					otherNodes[j].getArtifact().setSequenceNumber(j);

			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
			//Map<Integer, Node.Op>[][] matrix = new Map[thisNodes.length][otherNodes.length];
			IntObjectMap<Node.Op>[][] matrix = new IntObjectMap[thisNodes.length][otherNodes.length];
			// initialize matrix column and row 0 with empty map
			for (int i = 0; i < thisNodes.length; i++) {
				//matrix[i][0] = new HashMap<>();
				matrix[i][0] = IntObjectMaps.immutable.empty();
			}
			for (int j = 0; j < otherNodes.length; j++) {
				//matrix[0][j] = new HashMap<>();
				matrix[0][j] = IntObjectMaps.immutable.empty();
			}

			// iterate over nodes
			for (int i = 1; i < thisNodes.length; i++) {
				for (int j = 1; j < otherNodes.length; j++) {
					Node.Op thisNode = thisNodes[i];
					Node.Op otherNode = otherNodes[j];

					if (thisNode == this.getHead() && otherNode == other.getHead()) {
						throw new EccoException("This should not happen. Heads should be excluded from alignment.");
					} else if (thisNode == this.getTail() && otherNode == other.getTail()) {
						// same as below but without adding a new value to cell
						// compute score for previous nodes of left and right and use it for current cell
						//Map<Integer, Node.Op> cell = new HashMap<>();
						MutableIntObjectMap<Node.Op> cell = IntObjectMaps.mutable.empty();
						for (Node.Op thisPrevious : thisNode.getPrevious()) {
							for (Node.Op otherPrevious : otherNode.getPrevious()) {
								cell.putAll(matrix[thisPrevious.getArtifact() == null ? 0 : thisPrevious.getArtifact().getSequenceNumber()][otherPrevious.getArtifact() == null ? 0 : otherPrevious.getArtifact().getSequenceNumber()]);
							}
						}
						// set current cell value
						matrix[i][j] = cell;
					} else if (thisNode.getArtifact() != null && thisNode.getArtifact().getData() != null && otherNode.getArtifact() != null && thisNode.getArtifact().getData().equals(otherNode.getArtifact().getData())) {
						// compute score for previous nodes of left and right and use it for current cell
						//Map<Integer, Node.Op> cell = new HashMap<>();
						MutableIntObjectMap<Node.Op> cell = IntObjectMaps.mutable.empty();
						for (Node.Op thisPrevious : thisNode.getPrevious()) {
							for (Node.Op otherPrevious : otherNode.getPrevious()) {
								cell.putAll(matrix[thisPrevious.getArtifact() == null ? 0 : thisPrevious.getArtifact().getSequenceNumber()][otherPrevious.getArtifact() == null ? 0 : otherPrevious.getArtifact().getSequenceNumber()]);
							}
						}
//						// copy diagonally previous cell
//						Map<Integer, Node.Op> cell = new HashMap<>(matrix[i - 1][j - 1]);
						// add matching node to new cell
						cell.put(thisNode.getArtifact().getSequenceNumber(), otherNode);
						// set current cell value
						matrix[i][j] = cell;
					} else { // mismatch
						// compute score for previous nodes of left
						//Map<Integer, Node.Op> thisCell = new HashMap<>();
						MutableIntObjectMap<Node.Op> thisCell = IntObjectMaps.mutable.empty();
						for (Node.Op thisPrevious : thisNode.getPrevious()) {
							thisCell.putAll(matrix[thisPrevious.getArtifact() == null ? 0 : thisPrevious.getArtifact().getSequenceNumber()][j]);
						}
						// compute score for previous nodes of right
						//Map<Integer, Node.Op> otherCell = new HashMap<>();
						MutableIntObjectMap<Node.Op> otherCell = IntObjectMaps.mutable.empty();
						for (Node.Op otherPrevious : otherNode.getPrevious()) {
							otherCell.putAll(matrix[i][otherPrevious.getArtifact() == null ? 0 : otherPrevious.getArtifact().getSequenceNumber()]);
						}
						// check which score is higher and use it for current cell
						if (thisCell.size() >= otherCell.size())
							matrix[i][j] = thisCell;
						else
							matrix[i][j] = otherCell;
//						if (matrix[i - 1][j].size() >= matrix[i][j - 1].size()) {
//							matrix[i][j] = new HashMap<>(matrix[i - 1][j]);
//						} else {
//							matrix[i][j] = new HashMap<>(matrix[i][j - 1]);
//						}
					}

				}
			}

			// set sequence number of matched artifacts
			Arrays.stream(otherNodes).filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			//matrix[thisNodes.length - 1][otherNodes.length - 1].forEach((key, value) -> value.getArtifact().setSequenceNumber(key));
			matrix[thisNodes.length - 1][otherNodes.length - 1].forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
		}


		//private
		default void alignMemoized(PartialOrderGraph.Op other) {
			// collect nodes
			Node.Op[] thisNodes = this.collectNodes().toArray(new Node.Op[0]);
			Node.Op[] otherNodes = other.collectNodes().toArray(new Node.Op[0]);
			// assign index as sequence number to every node
			for (int i = 0; i < thisNodes.length; i++)
				if (thisNodes[i].getArtifact() != null)
					thisNodes[i].getArtifact().setSequenceNumber(i);
			for (int j = 0; j < otherNodes.length; j++)
				if (otherNodes[j].getArtifact() != null)
					otherNodes[j].getArtifact().setSequenceNumber(j);

			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
			IntObjectMap<Node.Op>[][] matrix = new IntObjectMap[thisNodes.length][otherNodes.length];

			// recursive memoized lcs
			this.alignMemoizedRec(matrix, this, other, thisNodes, otherNodes, this.getTail(), other.getTail());

			// set sequence number of matched artifacts
			Arrays.stream(otherNodes).filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			matrix[thisNodes.length - 1][otherNodes.length - 1].forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
		}

		//private
		default IntObjectMap<Node.Op> alignMemoizedRec(IntObjectMap<Node.Op>[][] matrix, PartialOrderGraph.Op left, PartialOrderGraph.Op right, Node.Op[] leftNodes, Node.Op[] rightNodes, Node.Op leftNode, Node.Op rightNode) {
			int i;
			if (leftNode == left.getHead())
				i = 0;
			else if (leftNode == left.getTail())
				i = leftNodes.length - 1;
			else if (leftNode.getArtifact() != null)
				i = leftNode.getArtifact().getSequenceNumber();
			else
				throw new EccoException("This should not happen.");
			int j;
			if (rightNode == right.getHead())
				j = 0;
			else if (rightNode == right.getTail())
				j = rightNodes.length - 1;
			else if (rightNode.getArtifact() != null)
				j = rightNode.getArtifact().getSequenceNumber();
			else
				throw new EccoException("This should not happen.");

			// check if value is already memoized
			if (matrix[i][j] == null) {
				// compute value
				if (i == 0 || j == 0) {
					matrix[i][j] = IntObjectMaps.immutable.empty();
				} else if (i == leftNodes.length - 1 && j == rightNodes.length - 1) {
					// same as below but without adding a new value to cell and without setting cell value
					// compute score for previous nodes of left and right and use it for current cell
					MutableIntObjectMap<Node.Op> cell = IntObjectMaps.mutable.empty();
					for (Node.Op leftPrevious : leftNode.getPrevious()) {
						for (Node.Op rightPrevious : rightNode.getPrevious()) {
							IntObjectMap<Node.Op> previousCell = this.alignMemoizedRec(matrix, left, right, leftNodes, rightNodes, leftPrevious, rightPrevious);
							cell.putAll(previousCell);
						}
					}
					// set current cell value
					matrix[i][j] = cell;
				} else if (leftNode.getArtifact() != null && leftNode.getArtifact().getData() != null && rightNode.getArtifact() != null && leftNode.getArtifact().getData().equals(rightNode.getArtifact().getData())) {
					// compute score for previous nodes of left and right and use it for current cell
					MutableIntObjectMap<Node.Op> cell = IntObjectMaps.mutable.empty();
					for (Node.Op leftPrevious : leftNode.getPrevious()) {
						for (Node.Op rightPrevious : rightNode.getPrevious()) {
							IntObjectMap<Node.Op> previousCell = this.alignMemoizedRec(matrix, left, right, leftNodes, rightNodes, leftPrevious, rightPrevious);
							cell.putAll(previousCell);
						}
					}
					// add matching node to new cell
					cell.put(leftNode.getArtifact().getSequenceNumber(), rightNode);
					// set current cell value
					matrix[i][j] = cell;
				} else {
					// compute score for previous nodes of left
					MutableIntObjectMap<Node.Op> leftCell = IntObjectMaps.mutable.empty();
					for (Node.Op leftPrevious : leftNode.getPrevious()) {
						//leftCell.putAll(matrix[leftPrevious.getArtifact() == null ? 0 : leftPrevious.getArtifact().getSequenceNumber()][j]);
						IntObjectMap<Node.Op> previousCell = this.alignMemoizedRec(matrix, left, right, leftNodes, rightNodes, leftPrevious, rightNode);
						leftCell.putAll(previousCell);
					}
					// compute score for previous nodes of right
					MutableIntObjectMap<Node.Op> rightCell = IntObjectMaps.mutable.empty();
					for (Node.Op rightPrevious : rightNode.getPrevious()) {
						//rightCell.putAll(matrix[i][rightPrevious.getArtifact() == null ? 0 : rightPrevious.getArtifact().getSequenceNumber()]);
						IntObjectMap<Node.Op> previousCell = this.alignMemoizedRec(matrix, left, right, leftNodes, rightNodes, leftNode, rightPrevious);
						rightCell.putAll(previousCell);
					}
					// check which score is higher and use it for current cell
					if (leftCell.size() >= rightCell.size()) // TODO: change this to ">"
						matrix[i][j] = leftCell;
					else
						matrix[i][j] = rightCell;
				}
			}
			return matrix[i][j];
		}


		//private
		default void alignMemoizedFixed(PartialOrderGraph.Op other) {
			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
			Map<Pair, IntObjectMap<Node.Op>> matrix = Maps.mutable.empty();

			// recursive memoized lcs
			State leftState = new State();
			leftState.counters.put(this.getTail(), 0);
			State rightState = new State();
			rightState.counters.put(other.getTail(), 0);
			IntObjectMap<Node.Op> result = this.alignMemoizedFixedRec(leftState, rightState, matrix);

			// set sequence number of matched artifacts
			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			result.forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
		}

		public class Pair {
			public State leftState;
			public State rightState;

			public Pair(State leftState, State rightState) {
				this.leftState = leftState;
				this.rightState = rightState;
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				Pair pair = (Pair) o;
				return Objects.equals(leftState, pair.leftState) &&
						Objects.equals(rightState, pair.rightState);
			}

			@Override
			public int hashCode() {
				return Objects.hash(leftState, rightState);
			}
		}

		public class State {
			public Map<Node.Op, Integer> counters;

			public State() {
				this.counters = Maps.mutable.empty();
			}

			public State(State other) {
				this.counters = Maps.mutable.empty();
				this.counters.putAll(other.counters);
			}

			public void advance(Node.Op node) {
				// remove node from counters
				this.counters.remove(node);
				// add previous of node to counters
				for (Node.Op previousNode : node.getPrevious()) {
					this.counters.putIfAbsent(previousNode, 0);
					this.counters.computeIfPresent(previousNode, (op, integer) -> integer + 1);
				}
			}

			public boolean isStart() {
				return this.counters.keySet().size() == 1 && this.counters.keySet().iterator().next().getNext().isEmpty();
			}

			public boolean isEnd() {
				return this.counters.keySet().size() == 1 && this.counters.keySet().iterator().next().getPrevious().isEmpty();
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				State state = (State) o;
				return Objects.equals(counters, state.counters);
			}

			@Override
			public int hashCode() {

				return Objects.hash(counters);
			}
		}

		//private
		default IntObjectMap<Node.Op> alignMemoizedFixedRec(State leftState, State rightState, Map<Pair, IntObjectMap<Node.Op>> matrix) {
			// check if value is already memoized
			Pair pair = new Pair(leftState, rightState);
			IntObjectMap<Node.Op> value = matrix.get(pair);
			if (value == null) {
				// compute value
				if (leftState.isEnd() || rightState.isEnd()) {
					// if we reached the head of either of the two pogs
					value = IntObjectMaps.immutable.empty();
				} else if (leftState.isStart() && rightState.isStart()) {
					// if we are at the tail of both of the two pogs
					State newLeftState = new State(leftState);
					for (Node.Op node : leftState.counters.keySet())
						newLeftState.advance(node);
					State newRightState = new State(rightState);
					for (Node.Op node : rightState.counters.keySet())
						newRightState.advance(node);
					value = this.alignMemoizedFixedRec(newLeftState, newRightState, matrix);
				} else {
					// find matches
					boolean matchFound = false;
					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
						Node.Op rightNode = rightEntry.getKey();
						if (rightEntry.getValue() == rightNode.getNext().size()) {
							for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
								Node.Op leftNode = leftEntry.getKey();
								if (leftEntry.getValue() == leftNode.getNext().size()) {
									if (leftNode.getArtifact() != null && leftNode.getArtifact().getData() != null && rightNode.getArtifact() != null && leftNode.getArtifact().getData().equals(rightNode.getArtifact().getData())) {
										State newLeftState = new State(leftState);
										newLeftState.advance(leftNode);
										State newRightState = new State(rightState);
										newRightState.advance(rightNode);

										IntObjectMap<Node.Op> previousValue = this.alignMemoizedFixedRec(newLeftState, newRightState, matrix);
										MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
										newValue.putAll(previousValue);
										newValue.put(leftNode.getArtifact().getSequenceNumber(), rightNode);
										value = newValue;

										matchFound = true;
										break;
									}
								}
							}
						}
						if (matchFound)
							break;
					}

					// if there is not a single match recurse previous of all left and right nodes
					if (!matchFound) {
						IntObjectMap<Node.Op> currentBest = null;
						for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
							Node.Op leftNode = leftEntry.getKey();
							if (leftEntry.getValue() == leftNode.getNext().size()) {
								State newLeftState = new State(leftState);
								newLeftState.advance(leftNode);

								IntObjectMap<Node.Op> previousValue = this.alignMemoizedFixedRec(newLeftState, rightState, matrix);

								if (currentBest == null || previousValue.size() > currentBest.size()) {
									MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
									newValue.putAll(previousValue);
									currentBest = newValue;
								}
							}
						}
						for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
							Node.Op rightNode = rightEntry.getKey();
							if (rightEntry.getValue() == rightNode.getNext().size()) {
								State newRightState = new State(rightState);
								newRightState.advance(rightNode);

								IntObjectMap<Node.Op> previousValue = this.alignMemoizedFixedRec(leftState, newRightState, matrix);

								if (currentBest == null || previousValue.size() > currentBest.size()) {
									MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
									newValue.putAll(previousValue);
									currentBest = newValue;
								}
							}
						}
						value = currentBest;
					}
				}
				matrix.put(pair, value);
			}
			return value;
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

			// check if alignment is valid
			this.checkAlignment(other, shared);

			// merge other partial order graph into this partial order graph
			this.mergeRec(this.getHead(), other.getHead(), shared);
			//this.mergeRecNew(this.getHead(), other.getHead(), shared);
			//this.trimRec(this.getHead());

			// check if graph has cycles and throw exception if it does
			this.checkConsistency();
		}

		//private
		default void mergeRec(Node.Op left, Node.Op right, Map<Integer, Node.Op> shared) {
			//System.out.println("MERGE: LEFT: " + left + " / RIGHT: " + right);

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// add all unshared (i.e. new) symbols in right children to left children. add tail as their child. assign new sequence number to them.
				for (Node.Op childRight : right.getNext()) {
					// check if right symbol is unshared
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

									if (!childLeft.getArtifact().equals(childRight.getArtifact()))
										throw new EccoException("This should not happen!");

									// make sure left node cannot be reached from childLeft
									if (canReach(childLeft, left.getArtifact()))
										throw new EccoException("Introduced cycle!");

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


		//private
		default void trimRec(Node.Op node) {
			// trim transitives, i.e. remove direct children that can be reached indirectly via any of the other children

			Map<PartialOrderGraph.Node.Op, Integer> counters = new HashMap<>();
			Stack<PartialOrderGraph.Node.Op> stack = new Stack<>();
			stack.push(node);

			while (!stack.isEmpty()) {
				Node.Op current = stack.pop();

				// process node
				Iterator<Node.Op> it = current.getNext().iterator();
				while (it.hasNext()) {
					Node.Op child = it.next();

					for (Node.Op otherChild : current.getNext()) {
						if (otherChild != child && canReach(otherChild, child.getArtifact())) {
							// we do not need connection -> delete it
							it.remove();
							child.getPrevious().remove(current);
							System.out.println("Removed node " + child + " as child from node " + current);
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

		//private
		default void mergeRecNew(Node.Op left, Node.Op right, Map<Integer, Node.Op> shared) {
			//System.out.println("MERGE: LEFT: " + left + " / RIGHT: " + right);

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {

				// add all unshared (i.e. new) symbols in right children to left children. add tail as their child. assign new sequence number to them.
				for (Node.Op childRight : right.getNext()) {
					// check if right symbol is unshared
					if (childRight.getArtifact() != null && !shared.containsKey(childRight.getArtifact().getSequenceNumber())) { // && childRight.getArtifact().getSequenceNumber() == PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
						// create node for new artifact
						Node.Op newLeft = this.createNode(childRight.getArtifact());
						// assign new sequence number to new artifact
						newLeft.getArtifact().setSequenceNumber(this.getMaxIdentifier());
						this.incMaxIdentifier();
						// add it to left
						left.addChild(newLeft);
//						// add tail as child of new node
//						newLeft.addChild(this.getTail());
						//System.out.println("Added new node " + newLeft + " as child to node " + left);
					}
				}

				// find shared symbols that are in right and not in left. ADD those that cannot be reached from left node because that means the right graph is more restrictive.
				for (Node.Op childRight : right.getNext()) {
					// check if right symbol is shared
					if (childRight.getArtifact() == null || childRight.getArtifact() != null && shared.containsKey(childRight.getArtifact().getSequenceNumber())) {
						Node.Op matchingChildLeft = null;
						for (Node.Op childLeft : left.getNext()) {
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

									// make sure left node cannot be reached from childLeft
									if (canReach(childLeft, left.getArtifact()))
										throw new EccoException("Introduced cycle!");

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
					if ((childLeft.getArtifact() == null && childRight.getArtifact() == null) || (childLeft.getArtifact() != null && childLeft.getArtifact().equals(childRight.getArtifact()))) {
						this.mergeRecNew(childLeft, childRight, shared);
						foundMatchingChildRight = true;
						break;
					}
				}
				// if left is unshared and was not added from right before then rec right here
				if (!foundMatchingChildRight && childLeft.getArtifact() != null && !shared.containsKey(childLeft.getArtifact().getSequenceNumber()))
					this.mergeRecNew(childLeft, right, shared);
			}
		}


		/**
		 * Checks whether an artifact can be reached from a given node.
		 *
		 * @param node     The node to start from.
		 * @param artifact The artifact to look for.
		 * @return
		 */
		//private
		static boolean canReach(Node node, Artifact<?> artifact) {
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


		/**
		 * checks if the alignments of this pog and the other pog are compatible
		 */
		default void checkAlignment(PartialOrderGraph.Op other, Map<Integer, Node.Op> shared) {
			// TODO: try to traverse other pog until the very end. if this is not possible the alignments are not compatible.
			// NOTE: use NOT_MATCHED_SEQUENCE_NUMBER instead of shared. anything in other that is not NOT_MATCHED_SEQUENCE_NUMBER is shared.


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
		public Map<Node.Op, List<Node.Op>> counters;
		public Artifact.Op<?> matchedArtifact;

		public Alignment() {
			this.counters = new HashMap<>();
			this.matchedArtifact = null;
		}

		public Alignment(Alignment other) {
			this();
			this.counters.putAll(other.counters);
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
