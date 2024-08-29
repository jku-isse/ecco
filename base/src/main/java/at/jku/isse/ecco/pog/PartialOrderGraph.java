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
	int INITIAL_SEQUENCE_NUMBER = 1;
	int NOT_MATCHED_SEQUENCE_NUMBER = -1;
	int UNASSIGNED_SEQUENCE_NUMBER = -2;
	int HEAD_SEQUENCE_NUMBER = -3;
	int TAIL_SEQUENCE_NUMBER = -4;


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

		//private
		Cell ZERO_CELL = new Cell();

		//private
		default void alignMemoizedBacktracking(PartialOrderGraph.Op other) {
			// matrix that stores the maps of matching nodes (sequence numbers to matching right nodes)
			Map<Pair, Cell> matrix = Maps.mutable.empty();

			// recursive memoized lcs
			State leftState = new State();
			leftState.counters.put(this.getTail(), 0);
			State rightState = new State();
			rightState.counters.put(other.getTail(), 0);
			//this.alignMemoizedBacktrackingRec(leftState, rightState, matrix);
			this.alignMemoizedBacktrackingIter(leftState, rightState, matrix);

			//IntObjectMap<Node.Op> result = this.backtrackingRec(leftState, rightState, matrix);
			IntObjectMap<Node.Op> result = this.backtrackingIter(leftState, rightState, matrix);

			// set sequence number of matched artifacts
			other.collectNodes().stream().filter(op -> op.getArtifact() != null).forEach(op -> op.getArtifact().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER));
			result.forEachKeyValue((key, value) -> value.getArtifact().setSequenceNumber(key));
		}


		// (ugly) transformation from recursive method to iterative method due to StackOverflows
		class AlignMemoizedBacktrackingState {
			public int position;

			// method variables
			public State leftState;
			public State rightState;
			public Pair pair;
			public Cell value;
			public State newLeftState;
			public State newRightState;
			public boolean matchFound;
			public Map.Entry<Node.Op, Integer> rightEntry;
			public Node.Op rightNode;
			public Map.Entry<Node.Op, Integer> leftEntry;
			public Node.Op leftNode;
			public Artifact.Op leftArtifact;
			public Artifact.Op rightArtifact;
			public Cell previousValue;
			public Cell localBest;
			public Set<Map.Entry<Node.Op, Integer>> loop3Todo;
			public Set<Map.Entry<Node.Op, Integer>> loop4Todo;

			public AlignMemoizedBacktrackingState(State leftState, State rightState){
				this.leftState = leftState;
				this.rightState = rightState;
				this.position = 0;
			}
		}

		default Cell alignMemoizedBacktrackingIter(State leftState, State rightState, Map<Pair, Cell> matrix) {
			// transformation of the recursive method to an iterative method (because of stack overflow)
			// basically, the method imitates what the compiler would do in the stack
			// a loop represents a method call
			AlignMemoizedBacktrackingState methodState = new AlignMemoizedBacktrackingState(leftState, rightState);
			Stack<AlignMemoizedBacktrackingState> methodStack = new Stack<>();
			methodStack.push(methodState);
			Cell lastResult = null;
			while (!methodStack.empty()) {
				methodState = methodStack.pop();

				if (methodState.position == 0){
					methodState.pair = new Pair(methodState.leftState, methodState.rightState);
					methodState.value = matrix.get(methodState.pair);
					if (methodState.value == null) {
						// compute value
						if (methodState.leftState.isEnd() || methodState.rightState.isEnd()) {
							// if we reached the head of either of the two pogs
							methodState.value = this.ZERO_CELL;
						} else if (methodState.leftState.isStart() && methodState.rightState.isStart()) {
							// if we are at the tail of both of the two pogs
							methodState.newLeftState = new State(methodState.leftState);
							for (Node.Op node : methodState.leftState.counters.keySet())
								methodState.newLeftState.advance(node);
							methodState.newRightState = new State(methodState.rightState);
							for (Node.Op node : methodState.rightState.counters.keySet())
								methodState.newRightState.advance(node);

							methodState.position = 1;
							methodStack.push(methodState);
							methodStack.push(new AlignMemoizedBacktrackingState(methodState.newLeftState, methodState.newRightState));
							// position 1
							// value = this.alignMemoizedBacktrackingRec(newLeftState, newRightState, matrix);
							continue;
						} else {
							// find matches
							methodState.matchFound = false;
							for (Map.Entry<Node.Op, Integer> rightEntry : methodState.rightState.counters.entrySet()) {
								methodState.rightEntry = rightEntry;
								methodState.rightNode = methodState.rightEntry.getKey();
								if (methodState.rightEntry.getValue() == methodState.rightNode.getNext().size()) {
									for (Map.Entry<Node.Op, Integer> leftEntry : methodState.leftState.counters.entrySet()) {
										methodState.leftEntry = leftEntry;
										methodState.leftNode = methodState.leftEntry.getKey();
										if (leftEntry.getValue() == methodState.leftNode.getNext().size()) {
											methodState.leftArtifact = methodState.leftNode.getArtifact();
											methodState.rightArtifact = methodState.rightNode.getArtifact();
											if (methodState.leftArtifact != null && methodState.leftArtifact.getData() != null && methodState.rightArtifact != null && methodState.leftArtifact.getData().equals(methodState.rightArtifact.getData())) {
												methodState.newLeftState = new State(methodState.leftState);
												methodState.newLeftState.advance(methodState.leftNode);
												methodState.newRightState = new State(methodState.rightState);
												methodState.newRightState.advance(methodState.rightNode);

												methodState.position = 2;
												methodStack.push(methodState);
												methodStack.push(new AlignMemoizedBacktrackingState(methodState.newLeftState, methodState.newRightState));
												// position 2
												// previousValue = this.alignMemoizedBacktrackingRec(newLeftState, newRightState, matrix);

												methodState.matchFound = true;
												break;
											}
										}
									}
								}
								if (methodState.matchFound)
									break;
							}

							if (methodState.matchFound){
								continue;
							} else {
								boolean continueLoop = false;
								// if there is not a single match recurse previous of all left and right nodes
								methodState.localBest = null;
								methodState.loop3Todo = new HashSet<>(methodState.leftState.counters.entrySet());
								for (Map.Entry<Node.Op, Integer> leftEntry : methodState.leftState.counters.entrySet()) {
									methodState.loop3Todo.remove(leftEntry);
									methodState.leftEntry = leftEntry;
									methodState.leftNode = methodState.leftEntry.getKey();
									if (methodState.leftEntry.getValue() == methodState.leftNode.getNext().size()) {
										methodState.newLeftState = new State(methodState.leftState);
										methodState.newLeftState.advance(methodState.leftNode);

										methodState.position = 3;
										methodStack.push(methodState);
										methodStack.push(new AlignMemoizedBacktrackingState(methodState.newLeftState, methodState.rightState));
										// position 3
										// previousValue = this.alignMemoizedBacktrackingRec(newLeftState, rightState, matrix);
										continueLoop = true;
										break;
									}
								}
								if (continueLoop) { continue; }
								methodState.loop4Todo = new HashSet<>(methodState.rightState.counters.entrySet());
								for (Map.Entry<Node.Op, Integer> rightEntry : methodState.rightState.counters.entrySet()) {
									methodState.loop4Todo.remove(rightEntry);
									methodState.rightEntry = rightEntry;
									methodState.rightNode = methodState.rightEntry.getKey();
									if (methodState.rightEntry.getValue() == methodState.rightNode.getNext().size()) {
										methodState.newRightState = new State(methodState.rightState);
										methodState.newRightState.advance(methodState.rightNode);

										methodState.position = 4;
										methodStack.push(methodState);
										methodStack.push(new AlignMemoizedBacktrackingState(methodState.leftState, methodState.newRightState));
										// position 4
										// previousValue = this.alignMemoizedBacktrackingRec(leftState, newRightState, matrix);
										continueLoop = true;
										break;
									}
								}
								if (continueLoop) { continue; }
								if (methodState.value == null || methodState.localBest != null && !methodState.value.isBetterThan(methodState.localBest))
									methodState.value = methodState.localBest;
							}
						}
						matrix.put(methodState.pair, methodState.value);
					}
					lastResult = methodState.value;
				} else if (methodState.position == 1){
					methodState.value = lastResult;
					matrix.put(methodState.pair, methodState.value);
					lastResult = methodState.value;
				} else if (methodState.position == 2){
					methodState.previousValue = lastResult;
					methodState.value = new Cell(methodState.previousValue);
					methodState.value.score += 1;
					matrix.put(methodState.pair, methodState.value);
					lastResult = methodState.value;
				} else if (methodState.position == 3){
					methodState.previousValue = lastResult;

					if (methodState.localBest == null || methodState.previousValue.isBetterThan(methodState.localBest)) {
						methodState.localBest = methodState.previousValue;
					}

					boolean continueLoop = false;
					for (Map.Entry<Node.Op, Integer> leftEntry : methodState.loop3Todo) {
						methodState.loop3Todo.remove(leftEntry);
						methodState.leftEntry = leftEntry;
						methodState.leftNode = methodState.leftEntry.getKey();
						if (methodState.leftEntry.getValue() == methodState.leftNode.getNext().size()) {
							methodState.newLeftState = new State(methodState.leftState);
							methodState.newLeftState.advance(methodState.leftNode);

							methodState.position = 3;
							methodStack.push(methodState);
							methodStack.push(new AlignMemoizedBacktrackingState(methodState.newLeftState, methodState.rightState));
							// position 3
							// previousValue = this.alignMemoizedBacktrackingRec(newLeftState, newRightState, matrix);
							continueLoop = true;
							break;
						}
					}
					if (continueLoop) { continue; }
					methodState.loop4Todo = new HashSet<>(methodState.rightState.counters.entrySet());
					for (Map.Entry<Node.Op, Integer> rightEntry : methodState.rightState.counters.entrySet()) {
						methodState.loop4Todo.remove(rightEntry);
						methodState.rightEntry = rightEntry;
						methodState.rightNode = methodState.rightEntry.getKey();
						if (methodState.rightEntry.getValue() == methodState.rightNode.getNext().size()) {
							methodState.newRightState = new State(methodState.rightState);
							methodState.newRightState.advance(methodState.rightNode);

							methodState.position = 4;
							methodStack.push(methodState);
							methodStack.push(new AlignMemoizedBacktrackingState(methodState.leftState, methodState.newRightState));
							// position 4
							// previousValue = this.alignMemoizedBacktrackingRec(leftState, newRightState, matrix);
							continueLoop = true;
							break;
						}
					}
					if (continueLoop) { continue; }
					if (methodState.value == null || methodState.localBest != null && !methodState.value.isBetterThan(methodState.localBest))
						methodState.value = methodState.localBest;
					matrix.put(methodState.pair, methodState.value);
					lastResult = methodState.value;
				} else if (methodState.position == 4){
					methodState.previousValue = lastResult;

					if (methodState.localBest == null || methodState.previousValue.isBetterThan(methodState.localBest)) {
						methodState.localBest = methodState.previousValue;
					}
					boolean continueLoop = false;
					for (Map.Entry<Node.Op, Integer> rightEntry : methodState.loop4Todo) {
						methodState.loop4Todo.remove(rightEntry);
						methodState.rightEntry = rightEntry;
						methodState.rightNode = methodState.rightEntry.getKey();
						if (methodState.rightEntry.getValue() == methodState.rightNode.getNext().size()) {
							methodState.newRightState = new State(methodState.rightState);
							methodState.newRightState.advance(methodState.rightNode);

							methodState.position = 4;
							methodStack.push(methodState);
							methodStack.push(new AlignMemoizedBacktrackingState(methodState.leftState, methodState.newRightState));
							// position 4
							// previousValue = this.alignMemoizedBacktrackingRec(leftState, newRightState, matrix);
							continueLoop = true;
							break;
						}
					}
					if (continueLoop) { continue; }
					if (methodState.value == null || methodState.localBest != null && !methodState.value.isBetterThan(methodState.localBest))
						methodState.value = methodState.localBest;
					matrix.put(methodState.pair, methodState.value);
					lastResult = methodState.value;
				}
			}
			return lastResult;
		}


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
									if (leftArtifact != null && leftArtifact.getData() != null && rightArtifact != null && leftArtifact.getData().equals(rightArtifact.getData())) {
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
						if (value == null || localBest != null && !value.isBetterThan(localBest))
							value = localBest;
					}
				}
				matrix.put(pair, value);
			}
			return value;
		}


		// (ugly) iterative backtracking implementation

		class BacktrackingState {
			public int position = 0;
			public MutableIntObjectMap<Node.Op> result;
			public Set<Map.Entry<Node.Op, Integer>> rightEntriesTodo;
			public Set<Map.Entry<Node.Op, Integer>> leftEntriesTodo;


			// method variables
			public State leftState;
			public State rightState;
			public Map.Entry<Node.Op, Integer> rightEntry;
			public Node.Op rightNode;
			public Map.Entry<Node.Op, Integer> leftEntry;
			public Node.Op leftNode;
			public Artifact.Op leftArtifact;
			public Artifact.Op rightArtifact;
			public State newLeftState;
			public State newRightState;
			public Pair pair;
			public Cell value;

			public BacktrackingState(State leftState, State rightState){
				this.leftState = leftState;
				this.rightState = rightState;
			}
		}

		default MutableIntObjectMap<Node.Op> backtrackingIter(State leftState, State rightState, Map<Pair, Cell> matrix){
			// transformation of the recursive method to an iterative method (because of stack overflow)
			// basically, the method imitates what the compiler would do in the stack
			// a loop represents a method call
			BacktrackingState methodState = new BacktrackingState(leftState, rightState);
			Stack<BacktrackingState> methodStack = new Stack<>();
			methodStack.push(methodState);
			MutableIntObjectMap<Node.Op> lastResult = null;

			while (!methodStack.isEmpty()) {
				methodState = methodStack.pop();

				if (methodState.position == 0) {
					if (methodState.leftState.isEnd() || methodState.rightState.isEnd()) {
						// if we reached the head of either of the two pogs: do nothing
						lastResult = IntObjectMaps.mutable.empty();
					} else if (methodState.leftState.isStart() && methodState.rightState.isStart()) {
						// if we are at the tail of both of the two pogs
						State newLeftState = new State(methodState.leftState);
						for (Node.Op node : methodState.leftState.counters.keySet())
							newLeftState.advance(node);
						State newRightState = new State(methodState.rightState);
						for (Node.Op node : methodState.rightState.counters.keySet())
							newRightState.advance(node);
						methodStack.push(new BacktrackingState(newLeftState, newRightState));
					} else {
						// find matches
						boolean nextMethodLoop = false;
						methodState.rightEntriesTodo = new HashSet<>(methodState.rightState.counters.entrySet());
						for (Map.Entry<Node.Op, Integer> rightEntry : methodState.rightState.counters.entrySet()) {
							methodState.rightEntriesTodo.remove(rightEntry);
							methodState.rightEntry = rightEntry;
							methodState.rightNode = methodState.rightEntry.getKey();
							if (methodState.rightEntry.getValue() == methodState.rightNode.getNext().size()) {
								methodState.leftEntriesTodo = new HashSet<>(methodState.leftState.counters.entrySet());
								for (Map.Entry<Node.Op, Integer> leftEntry : methodState.leftState.counters.entrySet()) {
									methodState.leftEntriesTodo.remove(leftEntry);
									methodState.leftEntry = leftEntry;
									methodState.leftNode = methodState.leftEntry.getKey();
									if (methodState.leftEntry.getValue() == methodState.leftNode.getNext().size()) {
										methodState.leftArtifact = methodState.leftNode.getArtifact();
										methodState.rightArtifact = methodState.rightNode.getArtifact();
										if (methodState.leftArtifact != null && methodState.leftArtifact.getData() != null && methodState.rightArtifact != null && methodState.leftArtifact.getData().equals(methodState.rightArtifact.getData())) {
											methodState.newLeftState = new State(methodState.leftState);
											methodState.newLeftState.advance(methodState.leftNode);
											methodState.newRightState = new State(methodState.rightState);
											methodState.newRightState.advance(methodState.rightNode);

											methodState.pair = new Pair(methodState.newLeftState, methodState.newRightState);
											methodState.value = matrix.get(methodState.pair);
											if (methodState.value == null) {
												System.out.println("WARNING: No cell value at this position in sparse matrix during match. This should not happen!");
											} else {
												methodState.position = 1;
												methodStack.push(methodState);
												methodStack.push(new BacktrackingState(methodState.newLeftState, methodState.newRightState));
												// position 1
												//methodState.returnedAlignment = this.backtrackingRec(newLeftState, newRightState, matrix);
												nextMethodLoop = true;
												break;
											}
										}
									}
								}
								if (nextMethodLoop) {
									break;
								}
							}
						}
						if (nextMethodLoop) {
							continue;
						}

						// if there is not a single match recurse previous of all left and right nodes
						State bestLeftState = null;
						State bestRightState = null;
						Cell currentBest = null;
						for (Map.Entry<Node.Op, Integer> leftEntry : methodState.leftState.counters.entrySet()) {
							Node.Op leftNode = leftEntry.getKey();
							if (leftEntry.getValue() == leftNode.getNext().size()) {
								State newLeftState = new State(methodState.leftState);
								newLeftState.advance(leftNode);

								Pair pair = new Pair(newLeftState, methodState.rightState);
								Cell value = matrix.get(pair);
								if (value == null) {
									System.out.println("WARNING: No cell value at this position in sparse matrix. This should not happen!");
								} else if (currentBest == null || value.isBetterThan(currentBest)) {
									currentBest = value;
									bestLeftState = newLeftState;
									bestRightState = methodState.rightState;
								}
							}
						}
						for (Map.Entry<Node.Op, Integer> rightEntry : methodState.rightState.counters.entrySet()) {
							Node.Op rightNode = rightEntry.getKey();
							if (rightEntry.getValue() == rightNode.getNext().size()) {
								State newRightState = new State(methodState.rightState);
								newRightState.advance(rightNode);

								Pair pair = new Pair(methodState.leftState, newRightState);
								Cell value = matrix.get(pair);
								if (value == null) {
									System.out.println("WARNING: No cell value at this position in sparse matrix. This should not happen!");
								} else if (currentBest == null || value.isBetterThan(currentBest)) {
									currentBest = value;
									bestLeftState = methodState.leftState;
									bestRightState = newRightState;
								}
							}
						}
						methodStack.push(new BacktrackingState(bestLeftState, bestRightState));
					}
				} else if (methodState.position == 1){
					lastResult.put(methodState.leftNode.getArtifact().getSequenceNumber(), methodState.rightNode);
				}
			}

			return lastResult;
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
//								Association.Op leftAssociation = leftArtifact.getContainingNode().getContainingAssociation();
//								if (leftArtifact != null && (leftAssociation == null || leftAssociation.isVisible()) && leftArtifact.getData() != null && rightArtifact != null && leftArtifact.getData().equals(rightArtifact.getData())) {
								if (leftArtifact != null && leftArtifact.getData() != null && rightArtifact != null && leftArtifact.getData().equals(rightArtifact.getData())) {
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

		}
	}








}
