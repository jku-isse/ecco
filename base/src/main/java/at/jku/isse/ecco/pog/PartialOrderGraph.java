package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
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
		 * Creates a new partial order graph (see {@link #fromList(List)}) reflecting the given list of artifacts and aligns it to this partial order graph (see {@link #align(PartialOrderGraph.Op)}).
		 *
		 * @param artifacts Sequence of artifacts to be aligned to this partial order graph.
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
		 * @param other Other partial order graph to be aligned to this partial order graph.
		 */
		public default void align(PartialOrderGraph.Op other) {
			this.alignMemoizedBacktracking(other);
		}


		//private
		class Pair {
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

		//private
		class State {
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

//			public int getBreadth() {
//				return this.counters.values().stream().reduce((i1, i2) -> i1 + i2).orElse(0);
//			}

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
		class Cell {
			public int score;

			public Cell() {
				this.score = 0;
			}

			public Cell(Cell other) {
				this.score = other.score;
			}

			public boolean isBetterThan(Cell other) {
				return this.score > other.score;
			}
		}


//		//private
//		default void alignMemoized(PartialOrderGraph.Op other) {
//			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
//			Map<Pair, IntObjectMap<Node.Op>> matrix = Maps.mutable.empty();
//
//			// recursive memoized lcs
//			State leftState = new State();
//			leftState.counters.put(this.getTail(), 0);
//			State rightState = new State();
//			rightState.counters.put(other.getTail(), 0);
//			IntObjectMap<Node.Op> result = this.alignMemoizedRec(leftState, rightState, matrix);
//
//			// set sequence number of matched artifacts
//			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
//			result.forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
//		}
//
//		//private
//		default IntObjectMap<Node.Op> alignMemoizedRec(State leftState, State rightState, Map<Pair, IntObjectMap<Node.Op>> matrix) {
//			// check if value is already memoized
//			Pair pair = new Pair(leftState, rightState);
//			IntObjectMap<Node.Op> value = matrix.get(pair);
//			if (value == null) {
//				// compute value
//				if (leftState.isEnd() || rightState.isEnd()) {
//					// if we reached the head of either of the two pogs
//					value = IntObjectMaps.immutable.empty();
//				} else if (leftState.isStart() && rightState.isStart()) {
//					// if we are at the tail of both of the two pogs
//					State newLeftState = new State(leftState);
//					for (Node.Op node : leftState.counters.keySet())
//						newLeftState.advance(node);
//					State newRightState = new State(rightState);
//					for (Node.Op node : rightState.counters.keySet())
//						newRightState.advance(node);
//					value = this.alignMemoizedRec(newLeftState, newRightState, matrix);
//				} else {
//					// find matches
//					boolean matchFound = false;
//					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//						Node.Op rightNode = rightEntry.getKey();
//						if (rightEntry.getValue() == rightNode.getNext().size()) {
//							for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//								Node.Op leftNode = leftEntry.getKey();
//								if (leftEntry.getValue() == leftNode.getNext().size()) {
//									if (leftNode.getArtifact() != null && leftNode.getArtifact().getData() != null && rightNode.getArtifact() != null && leftNode.getArtifact().getData().equals(rightNode.getArtifact().getData())) {
//										State newLeftState = new State(leftState);
//										newLeftState.advance(leftNode);
//										State newRightState = new State(rightState);
//										newRightState.advance(rightNode);
//
//										IntObjectMap<Node.Op> previousValue = this.alignMemoizedRec(newLeftState, newRightState, matrix);
//										MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
//										newValue.putAll(previousValue);
//										newValue.put(leftNode.getArtifact().getSequenceNumber(), rightNode);
//										value = newValue;
//
//										matchFound = true;
//										break;
//									}
//								}
//							}
//						}
//						if (matchFound)
//							break;
//					}
//
//					// if there is not a single match recurse previous of all left and right nodes
//					if (!matchFound) {
//						IntObjectMap<Node.Op> currentBest = null;
//						for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//							Node.Op leftNode = leftEntry.getKey();
//							if (leftEntry.getValue() == leftNode.getNext().size()) {
//								State newLeftState = new State(leftState);
//								newLeftState.advance(leftNode);
//
//								IntObjectMap<Node.Op> previousValue = this.alignMemoizedRec(newLeftState, rightState, matrix);
//
//								if (currentBest == null || previousValue.size() > currentBest.size()) {
//									MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
//									newValue.putAll(previousValue);
//									currentBest = newValue;
//								}
//							}
//						}
//						for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//							Node.Op rightNode = rightEntry.getKey();
//							if (rightEntry.getValue() == rightNode.getNext().size()) {
//								State newRightState = new State(rightState);
//								newRightState.advance(rightNode);
//
//								IntObjectMap<Node.Op> previousValue = this.alignMemoizedRec(leftState, newRightState, matrix);
//
//								if (currentBest == null || previousValue.size() > currentBest.size()) {
//									MutableIntObjectMap<Node.Op> newValue = IntObjectMaps.mutable.empty();
//									newValue.putAll(previousValue);
//									currentBest = newValue;
//								}
//							}
//						}
//						value = currentBest;
//					}
//				}
//				matrix.put(pair, value);
//			}
//			return value;
//		}


		//private
		default void alignMemoizedBacktracking(PartialOrderGraph.Op other) {
			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
			Map<Pair, Cell> matrix = Maps.mutable.empty();

			// recursive memoized lcs
			State leftState = new State();
			leftState.counters.put(this.getTail(), 0);
			State rightState = new State();
			rightState.counters.put(other.getTail(), 0);
			this.alignMemoizedBacktrackingRec(leftState, rightState, matrix);
			IntObjectMap<Node.Op> result = this.backtrackingRec(leftState, rightState, matrix);

			// set sequence number of matched artifacts
			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			result.forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
		}

		//private
		static final Cell ZERO_CELL = new Cell();

		//private
		default Cell alignMemoizedBacktrackingRec(State leftState, State rightState, Map<Pair, Cell> matrix) {
			// check if value is already memoized
			Pair pair = new Pair(leftState, rightState);
			Cell value = matrix.get(pair);
			if (value == null) {
				// compute value
				if (leftState.isEnd() || rightState.isEnd()) {
					// if we reached the head of either of the two pogs
					value = this.ZERO_CELL;
				} else if (leftState.isStart() && rightState.isStart()) {
					// if we are at the tail of both of the two pogs
					State newLeftState = new State(leftState);
					for (Node.Op node : leftState.counters.keySet())
						newLeftState.advance(node);
					State newRightState = new State(rightState);
					for (Node.Op node : rightState.counters.keySet())
						newRightState.advance(node);
					value = this.alignMemoizedBacktrackingRec(newLeftState, newRightState, matrix);
				} else {
					// find matches
					boolean matchFound = false;
					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
						Node.Op rightNode = rightEntry.getKey();
						if (rightEntry.getValue() == rightNode.getNext().size()) {
							for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
								Node.Op leftNode = leftEntry.getKey();
								if (leftEntry.getValue() == leftNode.getNext().size()) {
									Artifact.Op leftArtifact = leftNode.getArtifact();
									Artifact.Op rightArtifact = rightNode.getArtifact();
									Association.Op leftAssociation = leftArtifact.getContainingNode().getContainingAssociation();
									if (leftArtifact != null && (leftAssociation == null || leftAssociation.isVisible()) && leftArtifact.getData() != null && rightArtifact != null && leftArtifact.getData().equals(rightArtifact.getData())) {
										State newLeftState = new State(leftState);
										newLeftState.advance(leftNode);
										State newRightState = new State(rightState);
										newRightState.advance(rightNode);

										Cell previousValue = this.alignMemoizedBacktrackingRec(newLeftState, newRightState, matrix);
										value = new Cell(previousValue);
										value.score += 1;

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
						Cell localBest = null;
						for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
							Node.Op leftNode = leftEntry.getKey();
							if (leftEntry.getValue() == leftNode.getNext().size()) {
								State newLeftState = new State(leftState);
								newLeftState.advance(leftNode);

								Cell previousValue = this.alignMemoizedBacktrackingRec(newLeftState, rightState, matrix);

								if (localBest == null || previousValue.isBetterThan(localBest)) {
									localBest = previousValue;
								}
							}
						}
						for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
							Node.Op rightNode = rightEntry.getKey();
							if (rightEntry.getValue() == rightNode.getNext().size()) {
								State newRightState = new State(rightState);
								newRightState.advance(rightNode);

								Cell previousValue = this.alignMemoizedBacktrackingRec(leftState, newRightState, matrix);

								if (localBest == null || previousValue.isBetterThan(localBest)) {
									localBest = previousValue;
								}
							}
						}
						value = localBest;
					}
				}
				matrix.put(pair, value);
			}
			return value;
		}

		//private
		default MutableIntObjectMap<Node.Op> backtrackingRec(State leftState, State rightState, Map<Pair, Cell> matrix) {
			if (leftState.isEnd() || rightState.isEnd()) {
				// if we reached the head of either of the two pogs: do nothing
				return IntObjectMaps.mutable.empty();
			} else if (leftState.isStart() && rightState.isStart()) {
				// if we are at the tail of both of the two pogs
				State newLeftState = new State(leftState);
				for (Node.Op node : leftState.counters.keySet())
					newLeftState.advance(node);
				State newRightState = new State(rightState);
				for (Node.Op node : rightState.counters.keySet())
					newRightState.advance(node);
				return this.backtrackingRec(newLeftState, newRightState, matrix);
			} else {
				// find matches
				for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
					Node.Op rightNode = rightEntry.getKey();
					if (rightEntry.getValue() == rightNode.getNext().size()) {
						for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
							Node.Op leftNode = leftEntry.getKey();
							if (leftEntry.getValue() == leftNode.getNext().size()) {
								Artifact.Op leftArtifact = leftNode.getArtifact();
								Artifact.Op rightArtifact = rightNode.getArtifact();
								Association.Op leftAssociation = leftArtifact.getContainingNode().getContainingAssociation();
								if (leftArtifact != null && (leftAssociation == null || leftAssociation.isVisible()) && leftArtifact.getData() != null && rightArtifact != null && leftArtifact.getData().equals(rightArtifact.getData())) {
									State newLeftState = new State(leftState);
									newLeftState.advance(leftNode);
									State newRightState = new State(rightState);
									newRightState.advance(rightNode);

									Pair pair = new Pair(newLeftState, newRightState);
									Cell value = matrix.get(pair);
									if (value == null) {
										System.out.println("WARNING: No cell value at this position in sparse matrix during match. This should not happen!");
									} else {
										MutableIntObjectMap<Node.Op> returnedAlignment = this.backtrackingRec(newLeftState, newRightState, matrix);
										returnedAlignment.put(leftNode.getArtifact().getSequenceNumber(), rightNode);
										return returnedAlignment;
									}
								}
							}
						}
					}
				}

				// if there is not a single match recurse previous of all left and right nodes
				State bestLeftState = null;
				State bestRightState = null;
				Cell currentBest = null;
				for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
					Node.Op leftNode = leftEntry.getKey();
					if (leftEntry.getValue() == leftNode.getNext().size()) {
						State newLeftState = new State(leftState);
						newLeftState.advance(leftNode);

						Pair pair = new Pair(newLeftState, rightState);
						Cell value = matrix.get(pair);
						if (value == null) {
							System.out.println("WARNING: No cell value at this position in sparse matrix. This should not happen!");
						} else if (currentBest == null || value.isBetterThan(currentBest)) {
							currentBest = value;
							bestLeftState = newLeftState;
							bestRightState = rightState;
						}
					}
				}
				for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
					Node.Op rightNode = rightEntry.getKey();
					if (rightEntry.getValue() == rightNode.getNext().size()) {
						State newRightState = new State(rightState);
						newRightState.advance(rightNode);

						Pair pair = new Pair(leftState, newRightState);
						Cell value = matrix.get(pair);
						if (value == null) {
							System.out.println("WARNING: No cell value at this position in sparse matrix. This should not happen!");
						} else if (currentBest == null || value.isBetterThan(currentBest)) {
							currentBest = value;
							bestLeftState = leftState;
							bestRightState = newRightState;
						}
					}
				}
				return this.backtrackingRec(bestLeftState, bestRightState, matrix);
			}
		}


//		//private
//		class CellExtended extends Cell {
//			//public int stateBreadthDifference;
//			public int switches;
//			public SKIP last_skip;
//
//			enum SKIP {LEFT, RIGHT, BOTH}
//
//			public CellExtended() {
//				super();
//				//this.stateBreadthDifference = 0;
//				this.switches = 0;
//				this.last_skip = null;
//			}
//
//			public CellExtended(CellExtended other) {
//				super(other);
//				//this.stateBreadthDifference = other.stateBreadthDifference;
//				this.switches = other.switches;
//				this.last_skip = other.last_skip;
//			}
//
//			public boolean isBetterThan(CellExtended other) {
//				return super.isBetterThan(other) || this.score == other.score && this.switches < other.switches;
//			}
//		}
//
//		//private
//		default CellExtended alignMemoizedBacktrackingSwitchesRec(State leftState, State rightState, Map<Pair, CellExtended> matrix) {
//			// check if value is already memoized
//			Pair pair = new Pair(leftState, rightState);
//			CellExtended value = matrix.get(pair);
//			if (value == null) {
//				// compute value
//				if (leftState.isEnd() || rightState.isEnd()) {
//					// if we reached the head of either of the two pogs
//					value = new CellExtended();
//					if (!leftState.isEnd()) {
//						value.switches++;
//						value.last_skip = CellExtended.SKIP.LEFT;
//					}
//					if (!rightState.isEnd()) {
//						value.switches++;
//						value.last_skip = CellExtended.SKIP.RIGHT;
//					}
//				} else if (leftState.isStart() && rightState.isStart()) {
//					// if we are at the tail of both of the two pogs
//					State newLeftState = new State(leftState);
//					for (Node.Op node : leftState.counters.keySet())
//						newLeftState.advance(node);
//					State newRightState = new State(rightState);
//					for (Node.Op node : rightState.counters.keySet())
//						newRightState.advance(node);
//					value = this.alignMemoizedBacktrackingSwitchesRec(newLeftState, newRightState, matrix);
//				} else {
//					// find matches
//					boolean matchFound = false;
//					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//						Node.Op rightNode = rightEntry.getKey();
//						if (rightEntry.getValue() == rightNode.getNext().size()) {
//							for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//								Node.Op leftNode = leftEntry.getKey();
//								if (leftEntry.getValue() == leftNode.getNext().size()) {
//									if (leftNode.getArtifact() != null && leftNode.getArtifact().getData() != null && rightNode.getArtifact() != null && leftNode.getArtifact().getData().equals(rightNode.getArtifact().getData())) {
//										State newLeftState = new State(leftState);
//										newLeftState.advance(leftNode);
//										State newRightState = new State(rightState);
//										newRightState.advance(rightNode);
//
//										CellExtended previousValue = this.alignMemoizedBacktrackingSwitchesRec(newLeftState, newRightState, matrix);
//										value = new CellExtended(previousValue);
//										value.score += 1;
//										value.last_skip = CellExtended.SKIP.BOTH;
//										if (previousValue.last_skip != null && previousValue.last_skip != CellExtended.SKIP.BOTH)
//											value.switches++;
//
//										matchFound = true;
//										break;
//									}
//								}
//							}
//						}
//						if (matchFound)
//							break;
//					}
//
//					// if there is not a single match recurse previous of all left and right nodes
//					//if (!matchFound) {
//					CellExtended localBest = null;
//					for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//						Node.Op leftNode = leftEntry.getKey();
//						if (leftEntry.getValue() == leftNode.getNext().size()) {
//							State newLeftState = new State(leftState);
//							newLeftState.advance(leftNode);
//
//							CellExtended previousValue = this.alignMemoizedBacktrackingSwitchesRec(newLeftState, rightState, matrix);
//
//							CellExtended localValue;
//							if (previousValue.last_skip != CellExtended.SKIP.LEFT) {
//								localValue = new CellExtended(previousValue);
//								localValue.last_skip = CellExtended.SKIP.LEFT;
//								if (previousValue.last_skip != null)
//									localValue.switches++;
//							} else {
//								localValue = previousValue;
//							}
//
//							if (localBest == null || localValue.isBetterThan(localBest)) {
//								localBest = localValue;
//							}
//						}
//					}
//					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//						Node.Op rightNode = rightEntry.getKey();
//						if (rightEntry.getValue() == rightNode.getNext().size()) {
//							State newRightState = new State(rightState);
//							newRightState.advance(rightNode);
//
//							CellExtended previousValue = this.alignMemoizedBacktrackingSwitchesRec(leftState, newRightState, matrix);
//
//							CellExtended localValue;
//							if (previousValue.last_skip != CellExtended.SKIP.RIGHT) {
//								localValue = new CellExtended(previousValue);
//								localValue.last_skip = CellExtended.SKIP.RIGHT;
//								if (previousValue.last_skip != null)
//									localValue.switches++;
//							} else {
//								localValue = previousValue;
//							}
//
//							if (localBest == null || localValue.isBetterThan(localBest)) {
//								localBest = localValue;
//							}
//						}
//					}
//					value = localBest;
//					//}
//				}
//				matrix.put(pair, value);
//			}
//			return value;
//		}


//		//private
//		default void alignMemoizedBacktrackingCombined(PartialOrderGraph.Op other) {
//			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
//			Map<Pair, Integer> matrix = Maps.mutable.empty();
//
//			// recursive memoized lcs
//			State leftState = new State();
//			leftState.counters.put(this.getTail(), 0);
//			State rightState = new State();
//			rightState.counters.put(other.getTail(), 0);
//
//			// initialize sequence numbers of other pog
//			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
//
//			this.alignMemoizedBacktrackingCombinedRec(leftState, rightState, matrix, 0, 0);
//		}
//
//		//private
//		default int alignMemoizedBacktrackingCombinedRec(State leftState, State rightState, Map<Pair, Integer> matrix, int currentCount, int globalBest) {
//			// check if value is already memoized
//			Pair pair = new Pair(leftState, rightState);
//			Integer value = matrix.get(pair);
//			if (value == null) {
//				// compute value
//				if (leftState.isEnd() || rightState.isEnd()) {
//					// if we reached the head of either of the two pogs
//					value = 0;
//				} else if (leftState.isStart() && rightState.isStart()) {
//					// if we are at the tail of both of the two pogs
//					State newLeftState = new State(leftState);
//					for (Node.Op node : leftState.counters.keySet())
//						newLeftState.advance(node);
//					State newRightState = new State(rightState);
//					for (Node.Op node : rightState.counters.keySet())
//						newRightState.advance(node);
//					value = this.alignMemoizedBacktrackingCombinedRec(newLeftState, newRightState, matrix, currentCount + 1, globalBest);
//				} else {
//					// find matches
//					boolean matchFound = false;
//					for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//						Node.Op rightNode = rightEntry.getKey();
//						if (rightEntry.getValue() == rightNode.getNext().size()) {
//							for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//								Node.Op leftNode = leftEntry.getKey();
//								if (leftEntry.getValue() == leftNode.getNext().size()) {
//									if (leftNode.getArtifact() != null && leftNode.getArtifact().getData() != null && rightNode.getArtifact() != null && leftNode.getArtifact().getData().equals(rightNode.getArtifact().getData())) {
//										State newLeftState = new State(leftState);
//										newLeftState.advance(leftNode);
//										State newRightState = new State(rightState);
//										newRightState.advance(rightNode);
//
//										int previousValue = this.alignMemoizedBacktrackingCombinedRec(newLeftState, newRightState, matrix, currentCount + 1, globalBest);
//										value = previousValue + 1;
//										if (value > globalBest) { // TODO: also factor size of states into this decision
//											// use this as solution for now
//											rightNode.getArtifact().setSequenceNumber(leftNode.getArtifact().getSequenceNumber());
//										}
//
//										matchFound = true;
//										break;
//									}
//								}
//							}
//						}
//						if (matchFound)
//							break;
//					}
//
//					// if there is not a single match recurse previous of all left and right nodes
//					if (!matchFound) {
//						int localBest = globalBest;
//						for (Map.Entry<Node.Op, Integer> leftEntry : leftState.counters.entrySet()) {
//							Node.Op leftNode = leftEntry.getKey();
//							if (leftEntry.getValue() == leftNode.getNext().size()) {
//								State newLeftState = new State(leftState);
//								newLeftState.advance(leftNode);
//
//								int previousValue = this.alignMemoizedBacktrackingCombinedRec(newLeftState, rightState, matrix, currentCount, localBest);
//
//								if (previousValue > localBest) {
//									localBest = previousValue;
//								}
//							}
//						}
//						for (Map.Entry<Node.Op, Integer> rightEntry : rightState.counters.entrySet()) {
//							Node.Op rightNode = rightEntry.getKey();
//							if (rightEntry.getValue() == rightNode.getNext().size()) {
//								State newRightState = new State(rightState);
//								newRightState.advance(rightNode);
//
//								int previousValue = this.alignMemoizedBacktrackingCombinedRec(leftState, newRightState, matrix, currentCount, localBest);
//
//								if (previousValue > localBest) {
//									localBest = previousValue;
//								}
//							}
//						}
//						value = localBest;
//					}
//				}
//				matrix.put(pair, value);
//			}
//			return value;
//		}


		/**
		 * Creates a new partial order graph reflecting the given list of artifacts and merges it into this partial order graph.
		 *
		 * @param artifacts Sequence of artifacts to be merged into this partial order graph.
		 */
		public default void merge(List<? extends Artifact.Op<?>> artifacts) {
			this.merge(this.fromList(artifacts));
		}

		/**
		 * @param other Other partial order graph to be merged into this partial order graph.
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
			this.mergeRec(this.getHead(), other.getHead(), shared, new HashSet<>(), new HashMap<>());
			//this.mergeRecNew(this.getHead(), other.getHead(), shared);
			this.trimRec(this.getHead());

			// check if graph has cycles and throw exception if it does
			this.checkConsistency();
		}

		//private
		default void mergeRec(Node.Op left, Node.Op right, Map<Integer, Node.Op> shared, Set<Node.Op> leftVisited, Map<Node.Op, Node.Op> addedFromRight) {
			//System.out.println("MERGE: LEFT: " + left + " / RIGHT: " + right);
			leftVisited.add(left);

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// add all unshared (i.e. new) symbols in right children to left children. add tail as their child. assign new sequence number to them.
				for (Node.Op childRight : right.getNext()) {
					// check if right symbol is unshared
					if (childRight.getArtifact() != null && !shared.containsKey(childRight.getArtifact().getSequenceNumber())) { // && childRight.getArtifact().getSequenceNumber() == PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER) {
						Node.Op newLeft = addedFromRight.get(childRight);
						if (newLeft == null) {
							// create node for new artifact
							newLeft = this.createNode(childRight.getArtifact());
							// assign new sequence number to new artifact
							newLeft.getArtifact().setSequenceNumber(this.getMaxIdentifier());
							this.incMaxIdentifier();
							// add tail as child of new node
							newLeft.addChild(this.getTail());
							addedFromRight.put(childRight, newLeft);
						}
						// add it to left
						left.addChild(newLeft);
						//System.out.println("Added new node " + newLeft + " as child to node " + left);
					}
				}
			}

			// left and right are equal
			if (left.getArtifact() == null && right.getArtifact() == null || left.getArtifact() != null && left.getArtifact().equals(right.getArtifact())) {
				// find shared symbols that are in left and not in right. REMOVE those that can be reached from right node because that means that the right graph is more restrictive.
				{
					Iterator<? extends Node.Op> it = left.getNext().iterator();
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
									if (canReach(childLeft, left.getArtifact())) {
										StringBuilder sb = new StringBuilder();
										if (left.getArtifact() != null) {
											at.jku.isse.ecco.tree.Node current = left.getArtifact().getContainingNode();
											while (current != null) {
												sb.append(current.toString() + " - ");
												current = current.getParent();
											}
										}
										throw new EccoException("Introduced cycle! " + sb.toString());
									}

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
						if (!leftVisited.contains(childLeft))
							this.mergeRec(childLeft, childRight, shared, leftVisited, addedFromRight);
						foundMatchingChildRight = true;
						break;
					}
				}
				// if left is unshared and was not added from right before then rec right here
				if (!foundMatchingChildRight && childLeft.getArtifact() != null && !shared.containsKey(childLeft.getArtifact().getSequenceNumber()))
					if (!leftVisited.contains(childLeft))
						this.mergeRec(childLeft, right, shared, leftVisited, addedFromRight);
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
				Iterator<? extends Node.Op> it = current.getNext().iterator();
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
		 * Checks if the alignments of this pog and the other pog are compatible.
		 *
		 * @param other  The other partial order graph.
		 * @param shared A mapping of sequence numbers to nodes in this partial order graph that are shared with the other partial order graph.
		 */
		//private
		default void checkAlignment(PartialOrderGraph.Op other, Map<Integer, Node.Op> shared) {
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
							sb.append(current.toString() + " - ");
							current = current.getParent();
						}
					}
					throw new EccoException("The same partial order graph node is being visited twice (this indicates a cycle)! " + sb.toString());
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
						sb.append(current.toString() + " - ");
						current = current.getParent();
					}
				}
				throw new EccoException("Not all partial order graph nodes can be reached (this indicates a cycle or an orphan node without parent)! " + sb.toString());
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
			public Collection<? extends Node.Op> getPrevious();

			public Collection<? extends Node.Op> getNext();

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
