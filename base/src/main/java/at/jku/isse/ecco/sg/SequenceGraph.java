package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;

import java.util.*;

/**
 * Public sequence graph interface.
 */
public interface SequenceGraph extends Persistable {

	public static final boolean SEQUENCE_LISTS_AS_GRAPHS = false;

	/**
	 * A constant indicating that an artifact does not yet have a sequence number assigned.
	 */
	public static final int UNASSIGNED_SEQUENCE_NUMBER = 0;

	public static final int INITIAL_SEQUENCE_NUMBER = 1;

	public static final int NOT_MATCHED_SEQUENCE_NUMBER = -1;


	public Node getRoot();


	/**
	 * Private sequence graph interface.
	 */
	public interface Op extends SequenceGraph {

		public Node.Op getRoot();

		public void setRoot(Node.Op root);


		public int getCurrentSequenceNumber();

		public void setCurrentSequenceNumber(int sn);

		public int nextSequenceNumber();


		public boolean getPol();

		public void setPol(boolean pol);

		public default void invertPol() {
			this.setPol(!this.getPol());
			if (this.getPol() == this.getRoot().getPol())
				throw new EccoException("After polarity inversion the sequence graph should have a different polarity than its root node.");
		}


		public Node.Op createSequenceGraphNode(boolean pol);


		// ###############################################

		/**
		 * Sequences another sequence graph into this sequence graph.
		 *
		 * @param other The other sequence graph to sequence into this one.
		 */
		public default void sequence(SequenceGraph.Op other) {
			// collect all symbols (artifacts) in other sequence graph
			Collection<Artifact.Op<?>> rightArtifacts = other.collectSymbols();

			// set sequence number of all symbols (artifacts) in other sequence graph to NOT_MATCHED_SEQUENCE_NUMBER prior to alignment to left sequence graph
			for (Artifact.Op symbol : rightArtifacts) {
				symbol.setSequenceNumber(UNASSIGNED_SEQUENCE_NUMBER);
			}

			// align other sequence graph to this sequence graph
			this.alignSequenceGraphRec(this.getRoot(), other.getRoot(), 0, Integer.MAX_VALUE);

			// assign new sequence numbers to symbols (artifacts) in other sequence graph that do not yet have one (i.e. a sequence number of NOT_MATCHED_SEQUENCE_NUMBER)
			int num_symbols = this.getCurrentSequenceNumber();
			other.invertPol();
			this.assignNewSequenceNumbersRec(other.getRoot(), other.getPol());

			// update this sequence graph
			Set<Artifact.Op<?>> shared_symbols = new HashSet<>();
			for (Artifact.Op<?> symbol : rightArtifacts) {
				if (symbol.getSequenceNumber() < num_symbols) {
					shared_symbols.add(symbol);
				}
			}

			//this.invertPol();
			this.setPol(true);
//			this.getRoot().setPol(false);
//			Map<Set<Artifact.Op<?>>, Node.Op> nodes = new HashMap<>();
//			nodes.put(new HashSet<>(), this.getRoot());
			Node.Op root = this.updateSequenceGraphRec(this.getRoot(), other.getRoot(), new HashSet<>(), new HashMap<>(), shared_symbols);
			this.setRoot(root);
		}

		/**
		 * Assigns new sequence numbers to symbols (artifacts) in given sequence graph that do not yet have one (i.e. a sequence number of NOT_MATCHED_SEQUENCE_NUMBER) using numbers from the pool of this sequence graph.
		 *
		 * @param sgn    The sequence graph node to start traversing from.
		 * @param newPol The new polarity to assign.
		 */
		//private
		default void assignNewSequenceNumbersRec(SequenceGraph.Node.Op sgn, boolean newPol) {
			if (sgn.getPol() == newPol) // already visited
				return;

			sgn.setPol(this.getPol()); // mark as visited

			for (SequenceGraph.Transition.Op child : sgn.getChildren()) {
				if (child.getKey().getSequenceNumber() == NOT_MATCHED_SEQUENCE_NUMBER) {
					child.getKey().setSequenceNumber(this.nextSequenceNumber());
				}

				this.assignNewSequenceNumbersRec(child.getValue(), newPol);
			}
		}

		/**
		 * This aligns the right sequence graph to the left sequence graph, assigning sequence numbers to the artifacts of the right sequence graph.
		 *
		 * @param left             The left node.
		 * @param right            The right node.
		 * @param cost             The current cost.
		 * @param global_best_cost The overall best cost found so far.
		 * @return The final cost.
		 */
		//private
		default int alignSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, int cost, int global_best_cost) {
			// BASE CASES

			// base case 1: abort and don't update alignment if we already had a better or equal solution
			if (cost >= global_best_cost) {
				return cost;
			}

			// base case 2: both sequence graphs have no more nodes
			if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
				return cost;
			}

			// base case 3: right has no more nodes, left does
			if (!left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
				return cost;
			}

			// RECURSION CASES

			int local_best_cost = global_best_cost;

			// recursion case 1: left has no more nodes, right does
			if (left.getChildren().isEmpty() && !right.getChildren().isEmpty()) {
				for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
					int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost + 1, local_best_cost);

					if (temp_cost < local_best_cost) { // part of currently best alignment
						local_best_cost = temp_cost;

						// indicate that this artifact needs a new sequence number assigned
						rightChildEntry.getKey().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER);
					}
				}
			}

			// recursion case 3
			else {

				boolean found_match = false;

				// case 1: do matches first
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {

						if (leftChildEntry.getKey().equalsIgnoreSequenceNumber(rightChildEntry.getKey())) { // we found a match -> pursue it
							int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), rightChildEntry.getValue(), cost, local_best_cost); // do not increment cost as it was a match

							found_match = true;

							if (temp_cost < local_best_cost) { // part of currently best alignment
								local_best_cost = temp_cost;

								// set right sequence number to left sequence number
								rightChildEntry.getKey().setSequenceNumber(leftChildEntry.getKey().getSequenceNumber());
							}

							// we do not need to look for another match because it can lead to at best equal cost
							break;
						}

					}
					if (found_match)
						break;
				}

				// case 1.1: find next match directly by skipping left
				// TODO

				// case 1.2: find next match directly by skipping right
				// TODO

				if (!found_match) { // only skip if there was no match (match is always better than skip)
					// case 2: skip left
					for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
						int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost, local_best_cost); // do not increase cost when skipping left as we do not need to assign a new sequence number

						if (temp_cost < local_best_cost)
							local_best_cost = temp_cost;
					}

					// case 3: skip right
					for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
						int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost + 1, local_best_cost); // increase cost when skipping right because we need to assign a new sequence number

						if (temp_cost < local_best_cost) { // part of currently best alignment
							local_best_cost = temp_cost;

							// indicate that this artifact needs a new sequence number assigned
							rightChildEntry.getKey().setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER);
						}
					}
				}

			}

			return local_best_cost;
		}

		/**
		 * This updates the left sequence graph by merging the aligned right sequence graph into it.
		 * Actually it rebuilds the left sequence graph, except for the root node.
		 *
		 * @param left           The left node.
		 * @param right          The right node.
		 * @param path           The current path.
		 * @param nodes          The map of current nodes (value) with their path (key).
		 * @param shared_symbols The set of shared symbols.
		 * @return The currently processed node.
		 */
		//private
		default SequenceGraph.Node.Op updateSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<Artifact.Op<?>> shared_symbols) {
			// get current graph node
			SequenceGraph.Node.Op sgn = nodes.get(path);
			if (sgn == null) {
				sgn = this.createSequenceGraphNode(false);
				nodes.put(path, sgn);
			}

			// base case: node has already been visited
			if (sgn.getPol())
				return sgn;

			// set node to visited
			sgn.setPol(true);

			Map<Artifact.Op<?>, SequenceGraph.Node.Op> new_children = new HashMap<>();

			// if unshared symbol left -> advance
			for (SequenceGraph.Transition.Op leftEntry : left.getChildren()) {
				if (!shared_symbols.contains(leftEntry.getKey())) {
					System.out.println("LEFT UNSHARED");
					HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
					new_path.add(leftEntry.getKey());
					SequenceGraph.Node.Op child = this.updateSequenceGraphRec(leftEntry.getValue(), right, new_path, nodes, shared_symbols);
					new_children.put(leftEntry.getKey(), child);
				}
			}

			// if unshared symbol right -> add it left and advance
			for (SequenceGraph.Transition.Op rightEntry : right.getChildren()) {
				if (!shared_symbols.contains(rightEntry.getKey())) {
					System.out.println("RIGHT UNSHARED");
					HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
					new_path.add(rightEntry.getKey());
					SequenceGraph.Node.Op child = this.updateSequenceGraphRec(left, rightEntry.getValue(), new_path, nodes, shared_symbols); // this should be a new node
					new_children.put(rightEntry.getKey(), child);
				}
			}

			// if shared symbol -> cut it if one-sided or take it when on both sides
			Iterator<SequenceGraph.Transition.Op> it = left.getChildren().iterator();
			while (it.hasNext()) {
				SequenceGraph.Transition.Op leftEntry = it.next();
				if (shared_symbols.contains(leftEntry.getKey())) {
					SequenceGraph.Transition.Op rightEntry = null;
					for (SequenceGraph.Transition.Op tempRightEntry : right.getChildren()) {
						if (tempRightEntry.getKey().equals(leftEntry.getKey())) {
							rightEntry = tempRightEntry;
							break;
						}
					}
					if (rightEntry != null) { // matching shared symbols -> take them
						System.out.println("SHARED MATCH");
						HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
						new_path.add(leftEntry.getKey());
						SequenceGraph.Node.Op child = this.updateSequenceGraphRec(leftEntry.getValue(), rightEntry.getValue(), new_path, nodes, shared_symbols);
						new_children.put(leftEntry.getKey(), child);
					} else { // no match for shared symbol -> cut graph
						System.out.println("SHARED CUT");
						it.remove();
					}
				}
			}

			sgn.getChildren().clear();
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : new_children.entrySet()) {
				sgn.addTransition(entry.getKey(), entry.getValue());
			}

			return sgn;
		}


		// ###############################################


		public default void sequence(List<? extends Artifact.Op<?>> artifacts) {
			if (SEQUENCE_LISTS_AS_GRAPHS) {
				// create temporary sequence graph for list of artifacts and align it to this sequence graph
				if (!artifacts.isEmpty()) {
					SequenceGraph.Op other = artifacts.get(0).createSequenceGraph();
					Node.Op currentNode = other.getRoot();
					for (Artifact.Op<?> artifact : artifacts) {
						Node.Op nextNode = other.createSequenceGraphNode(other.getPol());
						currentNode.addTransition(artifact, nextNode);
						currentNode = nextNode;
					}
					this.sequence(other);
				}
			} else {
				this.align(artifacts);

				// assign new sequence numbers to artifacts that could not be matched during alignment
				int num_symbols = this.getCurrentSequenceNumber();
				for (Artifact.Op<?> artifact : artifacts) {
					if (artifact.getSequenceNumber() == NOT_MATCHED_SEQUENCE_NUMBER) {
						artifact.setSequenceNumber(this.nextSequenceNumber());
					}
				}

				// compute shared symbols
				Set<Artifact.Op<?>> shared_symbols = new HashSet<>();
				for (Artifact.Op<?> symbol : artifacts) {
					if (symbol.getSequenceNumber() < num_symbols)
						shared_symbols.add(symbol);
				}

				// update this sequence graph
				//Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes = this.collectPathMap();
				//this.invertPol();
				this.setPol(true);
				Node.Op root = this.updateRec(this.getRoot(), artifacts, 0, new HashSet<>(), new HashMap<>(), shared_symbols);
				this.setRoot(root);
			}
		}

		/**
		 * Does not modify sequence graph in any way.
		 * Assigns sequence numbers to artifacts that could be matched during alignment.
		 *
		 * @param artifacts The list of artifacts to be aligned to this sequence graph.
		 */
		public default void align(List<? extends Artifact.Op<?>> artifacts) {
			int finalCost = this.alignRec(this.getRoot(), artifacts, 0, 0, Integer.MAX_VALUE);

			// check if either a sequence number or NOT_MATCHED_SEQUENCE_NUMBER was assigned to every artifact
			for (Artifact artifact : artifacts) {
				if (artifact.getSequenceNumber() == 0) {
					throw new EccoException("An element has not been processed during alignment (i.e. neither a sequence number nor NOT_MATCHED_SEQUENCE_NUMBER was assigned).");
				}
			}
		}

		//private
		default int alignRec(SequenceGraph.Node.Op left, List<? extends Artifact.Op<?>> artifacts, int right_index, int cost, int global_best_cost) {
			// BASE CASES

			// base case 1: abort and don't update alignment if we already had a better or equal solution
			if (cost >= global_best_cost) {
				return cost;
			}

			// base case 2: left and right have no remaining elements
			if (left.getChildren().isEmpty() && right_index >= artifacts.size()) {
				return cost;
			}

			// base case 3: right has no more elements, left does
			if (!left.getChildren().isEmpty() && right_index >= artifacts.size()) {
				return cost;
			}

			// base case 4: left has no more elements, right does
			if (left.getChildren().isEmpty() && right_index < artifacts.size()) {
				int temp_cost = cost + artifacts.size() - right_index;

				if (temp_cost < global_best_cost) {
					// indicate that the remaining artifacts need a new sequence number assigned
					for (int i = right_index; i < artifacts.size(); i++) {
						artifacts.get(i).setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER);
					}
				}

				return temp_cost;
			}

			// RECURSION CASES

			boolean found_match = false;
			int local_best_cost = global_best_cost;

			// at this point neither left nor right are empty

			// first do the match
			for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
				// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
				if (leftChildEntry.getKey().equalsIgnoreSequenceNumber(artifacts.get(right_index))) {
					found_match = true;
					int temp_cost = alignRec(leftChildEntry.getValue(), artifacts, right_index + 1, cost, local_best_cost);
					if (temp_cost < local_best_cost) {
						local_best_cost = temp_cost;
						artifacts.get(right_index).setSequenceNumber(leftChildEntry.getKey().getSequenceNumber());
					}
				}
			}

			boolean skipped_right = false;

			// skip right: directly find other matches in right
			for (int i = right_index + 1; i < artifacts.size() && !found_match && cost + i - right_index < local_best_cost; i++) {
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
					if (leftChildEntry.getKey().equalsIgnoreSequenceNumber(artifacts.get(i))) {
						found_match = true;
						skipped_right = true;
						int temp_cost = alignRec(leftChildEntry.getValue(), artifacts, i + 1, cost + i - right_index, local_best_cost);
						if (temp_cost < local_best_cost) {
							local_best_cost = temp_cost;
							artifacts.get(i).setSequenceNumber(leftChildEntry.getKey().getSequenceNumber());

							// set sequence numbers of skipped elements in right to NOT_MATCHED_SEQUENCE_NUMBER because the costs were cheaper here
							for (int j = right_index; j < i; j++) {
								artifacts.get(j).setSequenceNumber(NOT_MATCHED_SEQUENCE_NUMBER);
							}
						}
					}
				}
			}

			// skip left: if we did not find a match or if we found a match by skipping right because we might still find a better solution by skipping left first
			if (!found_match || skipped_right) {
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					int temp_cost = alignRec(leftChildEntry.getValue(), artifacts, right_index, cost, local_best_cost);
					if (temp_cost < local_best_cost) {
						local_best_cost = temp_cost;
						// no changes to the alignment
					}
				}
			}

			return local_best_cost;
		}

		//private
		default SequenceGraph.Node.Op updateRec(SequenceGraph.Node.Op left, List<? extends Artifact.Op<?>> artifacts, int right_index, HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<? extends Artifact.Op<?>> shared_symbols) {
			// get current graph node
			SequenceGraph.Node.Op sgn = nodes.get(path);
			if (sgn == null) {
				sgn = this.createSequenceGraphNode(false);
				nodes.put(path, sgn);
			}

			// base case: node has already been visited
			if (sgn.getPol())
				return sgn;

			// set node to visited
			sgn.setPol(true);

			// determine all possible successor paths
			Map<Artifact.Op<?>, SequenceGraph.Node.Op> new_children = new HashMap<>();
			Artifact.Op<?> right = null;
			if (right_index < artifacts.size())
				right = artifacts.get(right_index);

			// if right unshared we can take the path (this is the adding part)
			if (right != null && !shared_symbols.contains(right)) {
				// compute new path
				HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
				new_path.add(right);
				// take it
				SequenceGraph.Node.Op child = updateRec(left, artifacts, right_index + 1, new_path, nodes, shared_symbols);
				if (child != null) // in this case child should never be null because we are adding new symbols
					new_children.put(right, child);
			}

			// for every left child (this is the cutting part)
			Iterator<SequenceGraph.Transition.Op> it = left.getChildren().iterator();
			while (it.hasNext()) {
				SequenceGraph.Transition.Op entry = it.next();
				// if left child unshared we can take it
				if (!shared_symbols.contains(entry.getKey())) {
					// compute new path
					HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
					new_path.add(entry.getKey());
					// take it
					SequenceGraph.Node.Op child = updateRec(entry.getValue(), artifacts, right_index, new_path, nodes, shared_symbols);
					if (child != null) // if child is null it already existed and we do not need to add it
						new_children.put(entry.getKey(), child);
				} else { // left child shared
					// if left child and right are equal we can take it
					if (right != null && right.equals(entry.getKey())) {
						// compute new path
						HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
						new_path.add(entry.getKey());
						// take it
						SequenceGraph.Node.Op child = updateRec(entry.getValue(), artifacts, right_index + 1, new_path, nodes, shared_symbols);
						if (child != null) // if child is null it already existed and we do not need to add it
							new_children.put(entry.getKey(), child);
					} else {
						// cut the transition
						it.remove();
					}
				}
			}

			sgn.getChildren().clear();
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : new_children.entrySet()) {
				sgn.addTransition(entry.getKey(), entry.getValue());
			}

			return sgn;
		}


		// ###############################################


		public default void updateArtifactReferences() {
			for (SequenceGraph.Node.Op sgn : this.collectNodes()) {
				// update references in children
				Map<Artifact.Op<?>, SequenceGraph.Node.Op> updatedChildren = new HashMap<>();
				Iterator<SequenceGraph.Transition.Op> childrenIterator = sgn.getChildren().iterator();
				while (childrenIterator.hasNext()) {
					SequenceGraph.Transition.Op childEntry = childrenIterator.next();
					if (childEntry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
						Artifact.Op<?> replacing = childEntry.getKey().<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
						replacing.setSequenceNumber(childEntry.getKey().getSequenceNumber());

						updatedChildren.put(replacing, childEntry.getValue());
						childrenIterator.remove();
					}
				}
				for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : updatedChildren.entrySet()) {
					sgn.addTransition(entry.getKey(), entry.getValue());
				}
			}
		}

		//private
		default void updateArtifactReferencesRec(SequenceGraph.Node.Op sgn) {
			// update references in children
			Map<Artifact.Op<?>, SequenceGraph.Node.Op> updatedChildren = new HashMap<>();
			Iterator<SequenceGraph.Transition.Op> it = sgn.getChildren().iterator();
			while (it.hasNext()) {
				SequenceGraph.Transition.Op entry = it.next();
				if (entry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact.Op<?> replacing = entry.getKey().<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					replacing.setSequenceNumber(entry.getKey().getSequenceNumber());

					updatedChildren.put(replacing, entry.getValue());
					it.remove();
				}
			}
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : updatedChildren.entrySet()) {
				sgn.addTransition(entry.getKey(), entry.getValue());
			}

			// traverse children
			for (SequenceGraph.Transition.Op child : sgn.getChildren()) {
				this.updateArtifactReferencesRec(child.getValue());
			}
		}


		public default Collection<SequenceGraph.Node.Op> collectNodes() {
			Collection<SequenceGraph.Node.Op> nodes = new ArrayList<>();
			this.invertPol();
			this.collectNodesRec(this.getRoot(), nodes);
			return nodes;
		}

		//private
		default void collectNodesRec(SequenceGraph.Node.Op sgn, Collection<SequenceGraph.Node.Op> nodes) {
			if (sgn.getPol() == this.getPol()) // already visited
				return;

			sgn.setPol(this.getPol()); // mark as visited

			nodes.add(sgn);

			for (SequenceGraph.Transition.Op entry : sgn.getChildren()) {
				this.collectNodesRec(entry.getValue(), nodes);
			}
		}


		public default Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> collectPathMap() {
			Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes = new HashMap<>();
			this.invertPol();
			this.collectPathMapRec(this.getRoot(), new HashSet<>(), nodes);
			return nodes;
		}

		//private
		default void collectPathMapRec(SequenceGraph.Node.Op sgn, Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes) {
			if (sgn.getPol() == this.getPol()) // already visited
				return;

			sgn.setPol(this.getPol()); // mark as visited

			nodes.put(path, sgn);

			for (SequenceGraph.Transition.Op entry : sgn.getChildren()) {
				Set<Artifact.Op<?>> newPath = new HashSet<>(path);
				newPath.add(entry.getKey());

				this.collectPathMapRec(entry.getValue(), newPath, nodes);
			}
		}


		/**
		 * Collects and returns all the symbols (i.e. artifacts) in the sequence graph.
		 *
		 * @return The collection of symbols in the sequence graph.
		 */
		public default Collection<Artifact.Op<?>> collectSymbols() {
			Set<Artifact.Op<?>> symbols = new HashSet<>();
			this.invertPol();
			this.collectSymbolsRec(this.getRoot(), symbols);
			return symbols;
		}

		//private
		default void collectSymbolsRec(SequenceGraph.Node.Op sgn, Collection<Artifact.Op<?>> symbols) {
			if (sgn.getPol() == this.getPol()) // already visited
				return;

			sgn.setPol(this.getPol()); // mark as visited

			for (SequenceGraph.Transition.Op entry : sgn.getChildren()) {
				symbols.add(entry.getKey());

				this.collectSymbolsRec(entry.getValue(), symbols);
			}
		}


		/**
		 * Trims the sequence graph by removing all symbols that are not contained in the collection of given symbols.
		 *
		 * @param symbols Symbols to keep.
		 */
		public default void trim(Collection<? extends Artifact.Op<?>> symbols) {
			SequenceGraph.Node.Op tempLeftRoot = this.createSequenceGraphNode(this.getPol());
			tempLeftRoot.getChildren().addAll(this.getRoot().getChildren()); // copy all children over to temporary root node
			this.getRoot().getChildren().clear(); // clear all children of real root node

			Set<Artifact.Op<?>> path = new HashSet<>();

			Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> rigthNodes = new HashMap<>();
			rigthNodes.put(path, this.getRoot());

			this.invertPol();
			this.trimRec(symbols, path, rigthNodes, tempLeftRoot, this.getRoot());
		}

		//private
		default void trimRec(Collection<? extends Artifact.Op<?>> symbols, Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> rightNodes, SequenceGraph.Node.Op left, SequenceGraph.Node.Op right) {
			if (left.getPol() == this.getPol()) // node already visited
				return;

			left.setPol(this.getPol()); // set to visited

			for (SequenceGraph.Transition.Op leftEntry : left.getChildren()) {
				if (symbols.contains(leftEntry.getKey())) { // put it into right
					Set<Artifact.Op<?>> newPath = new HashSet<>(path);
					newPath.add(leftEntry.getKey());

					SequenceGraph.Node.Op rightChild = rightNodes.get(newPath);
					if (rightChild == null) {
						rightChild = this.createSequenceGraphNode(this.getPol());
						rightNodes.put(newPath, rightChild);
					}
					right.addTransition(leftEntry.getKey(), rightChild);

					this.trimRec(symbols, newPath, rightNodes, leftEntry.getValue(), rightChild);
				} else {
					this.trimRec(symbols, path, rightNodes, leftEntry.getValue(), right);
				}
			}
		}


		/**
		 * Creates a copy of the sequence graph. Uses the same artifacts.
		 *
		 * @param sg The other sequence graph to copy into this sequence graph.
		 */
		public default void copy(SequenceGraph sg) {
			if (!this.getRoot().getChildren().isEmpty())
				throw new EccoException("Sequence graph must be empty to copy another.");

			if (!(sg instanceof SequenceGraph.Op))
				throw new EccoException("Copy requires two sequence graph operands.");
			SequenceGraph.Op other = (SequenceGraph.Op) sg;

			other.invertPol();

			HashSet<Artifact.Op<?>> path = new HashSet<>();
			Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> leftNodes = new HashMap<>();
			leftNodes.put(path, this.getRoot());

			this.copyRec(path, leftNodes, this.getRoot(), other.getRoot(), other.getPol());

			this.setCurrentSequenceNumber(other.getCurrentSequenceNumber());
		}

		//private
		default void copyRec(Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> leftNodes, SequenceGraph.Node.Op leftNode, SequenceGraph.Node.Op rightNode, boolean newPol) {
			if (rightNode.getPol() == newPol) // already visited
				return;

			rightNode.setPol(newPol); // mark as visited

			for (SequenceGraph.Transition.Op rightEntry : rightNode.getChildren()) {
				Set<Artifact.Op<?>> newPath = new HashSet<>(path);
				newPath.add(rightEntry.getKey());

				SequenceGraph.Node.Op leftChildNode = leftNodes.get(newPath);
				if (leftChildNode == null) {
					leftChildNode = this.createSequenceGraphNode(this.getPol());
					leftNodes.put(newPath, leftChildNode);
				}
				leftNode.addTransition(rightEntry.getKey(), leftChildNode);

				this.copyRec(newPath, leftNodes, leftChildNode, rightEntry.getValue(), newPol);
			}
		}

	}


	public default void checkConsistency() {
		// TODO
	}


	/**
	 * Sequence graph node.
	 */
	public interface Node extends Persistable {
		//public Map<? extends Artifact.Op<?>, ? extends Node> getChildren();
		public Collection<? extends SequenceGraph.Transition> getChildren();

		public interface Op extends Node {
			@Override
			public Collection<SequenceGraph.Transition.Op> getChildren();

			public boolean getPol();

			public void setPol(boolean pol);

			public Transition.Op addTransition(Artifact.Op<?> key, SequenceGraph.Node.Op value);
		}
	}

	/**
	 * Sequence graph transition.
	 */
	public interface Transition extends Persistable {
		public Artifact<?> getKey();

		public SequenceGraph.Node getValue();

		public interface Op extends Transition {
			@Override
			public Artifact.Op<?> getKey();

			@Override
			public SequenceGraph.Node.Op getValue();
		}
	}

	public interface Symbol extends Persistable {

		public boolean equalsIgnoreSequenceNumber(Object obj);

		/**
		 * Returns the assigned sequence number in case this artifact is the child of an ordered artifact that has already been sequenced, or {@link at.jku.isse.ecco.sg.SequenceGraph#UNASSIGNED_SEQUENCE_NUMBER} otherwise.
		 *
		 * @return The assigned sequence number in case this artifact is the child of an ordered artifact that has already been sequenced, or {@link at.jku.isse.ecco.sg.SequenceGraph#UNASSIGNED_SEQUENCE_NUMBER} otherwise.
		 */
		public int getSequenceNumber();

		public interface Op extends Symbol {
			/**
			 * Sets the sequence number of the artifact. This is used by the sequence graph.
			 *
			 * @param sequenceNumber The sequence number to assign to this artifact.
			 */
			public void setSequenceNumber(int sequenceNumber);
		}
	}

}
