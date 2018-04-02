package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public sequence graph interface.
 */
public interface SequenceGraph extends Persistable {

	public Node getRoot();


	/**
	 * Private sequence graph interface.
	 */
	public interface Op extends SequenceGraph {

		public Node.Op getRoot();


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


		public int getGlobalBestCost();

		public void setGlobalBestCost(int cost);


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

			// set sequence number of all symbols (artifacts) in other sequence graph to -1 prior to alignment to left sequence graph
			for (Artifact.Op symbol : rightArtifacts) {
				symbol.setSequenceNumber(-1);
			}

			// align other sequence graph to this sequence graph
			this.alignSequenceGraph(other);

			// assign new sequence numbers to symbols (artifacts) in other sequence graph that do not yet have one (i.e. a sequence number of -1)
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
			this.invertPol();
			Map<Set<Artifact.Op<?>>, Node.Op> nodes = new HashMap<>();
			nodes.put(new HashSet<>(), this.getRoot());
			this.updateSequenceGraphRec(this.getRoot(), other.getRoot(), new HashSet<>(), nodes, shared_symbols);
		}

		/**
		 * Assigns new sequence numbers to symbols (artifacts) in given sequence graph that do not yet have one (i.e. a sequence number of -1) using numbers from the pool of this sequence graph.
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
				if (child.getKey().getSequenceNumber() == -1) {
					child.getKey().setSequenceNumber(this.nextSequenceNumber());
				}

				this.assignNewSequenceNumbersRec(child.getValue(), newPol);
			}
		}

		/**
		 * Aligns the given sequence graph to this sequence graph. Sets the sequence number of symbols in the right sequence graphs to the sequence numbers of the matching symbols in this sequence graph.
		 *
		 * @param other The other sequence graph to align to this one.
		 */
		default void alignSequenceGraph(SequenceGraph.Op other) {
			this.setGlobalBestCost(Integer.MAX_VALUE);
			this.alignSequenceGraphRec(this.getRoot(), other.getRoot(), 0);
		}

//		// TODO: implement optimized sequence graph merge
//		//private
//		default int alignSequenceGraphRecFast(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, int cost) {
//			//int local_best_cost = this.global_best_cost;
//			int local_best_cost = Integer.MAX_VALUE;
//
//			// base case 1: abort and don't update alignment if we already had a better or equal solution.
//			if (cost >= this.global_best_cost) {
//				return Integer.MAX_VALUE;
//			}
//
//			// base case 2: both sequence graphs have no more nodes
//			else if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
//				// update current best cost (we would not have gotten here if cost > global_best_cost)
//				this.global_best_cost = cost;
//				return cost;
//			}
//
//			// recursion case 1: left has no more nodes, right does
//			else if (left.getChildren().isEmpty() && !right.getChildren().isEmpty()) {
//				for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
//					int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost + 1);
//
//					if (temp_cost < local_best_cost)
//						local_best_cost = temp_cost;
//
//					if (temp_cost <= this.global_best_cost) { // part of currently best alignment
//						if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != -1)
//							System.out.println("WARNING1! " + rightChildEntry.getKey() + ": " + -1);
//
//						rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
//					}
//				}
//			}
//
//			// recursion case 2: right has no more nodes, left does
//			else if (right.getChildren().isEmpty() && !left.getChildren().isEmpty()) {
//				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
//					int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);
//
//					if (temp_cost < local_best_cost)
//						local_best_cost = temp_cost;
//				}
//			}
//
//			// recursion case
//			else {
//
//				// case 1: do matches first
//				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
//					for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
//
//						if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) { // we found a match -> pursue it
//							int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), rightChildEntry.getValue(), cost); // do not increment cost as it was a match
//
//							if (temp_cost < local_best_cost)
//								local_best_cost = temp_cost;
//
//							if (temp_cost <= this.global_best_cost) { // part of currently best alignment
//								if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != leftChildEntry.getKey().getSequenceNumber())
//									System.out.println("WARNING2! " + leftChildEntry.getKey() + ": " + leftChildEntry.getKey().getSequenceNumber());
//
//								rightChildEntry.getKey().setSequenceNumber(leftChildEntry.getKey().getSequenceNumber()); // set right sequence number to left sequence number
//							}
//						}
//
//					}
//				}
//
////			// find match by skipping left
////			SequenceGraphNode tempLeftNode = left.getChildren().values().iterator().next();
////			boolean found_match = false;
////			while (tempLeftNode.getChildren().size() > 0) {
////				for (Map.Entry<Artifact.Op<?>, SequenceGraphNode> leftChildEntry : tempLeftNode.getChildren().entrySet()) {
////					for (Map.Entry<Artifact.Op<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {
////						if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) {
////							found_match = true;
////							// TODO
////							break;
////						}
////					}
////					if (found_match)
////						break;
////				}
////				if (!found_match) {
////					tempLeftNode = left.getChildren().values().iterator().next(); // just take first left child
////				} else {
////
////				}
////			}
//
//				// case 2: skip left
//				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
//					int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);
//
//					if (temp_cost < local_best_cost)
//						local_best_cost = temp_cost;
//				}
//
//				// case 3: skip right
//				for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
//					int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost);
//
//					if (temp_cost < local_best_cost)
//						local_best_cost = temp_cost;
//
//					if (temp_cost <= this.global_best_cost) { // part of currently best alignment
//						if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != -1)
//							System.out.println("WARNING3! " + rightChildEntry.getKey() + ": " + -1);
//
//						rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
//					}
//				}
//
//			}
//
//			return local_best_cost;
//		}

		/**
		 * This aligns the right sequence graph to the left sequence graph, assigning sequence numbers to the artifacts of the right sequence graph.
		 *
		 * @param left  The left node.
		 * @param right The right node.
		 * @param cost  The current cost.
		 * @return The final cost.
		 */
		//private
		default int alignSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, int cost) {
			int local_best_cost = Integer.MAX_VALUE;

			// base case 1: abort and don't update alignment if we already had a better or equal solution.
			if (cost >= this.getGlobalBestCost()) {
				return Integer.MAX_VALUE;
			}

			// base case 2: both sequence graphs have no more nodes
			else if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
				// update current best cost (we would not have gotten here if cost > global_best_cost)
				this.setGlobalBestCost(cost);
				return cost;
			}

			// recursion case 1: left has no more nodes, right does
			else if (left.getChildren().isEmpty() && !right.getChildren().isEmpty()) {
				for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
					int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost + 1);

					if (temp_cost < local_best_cost)
						local_best_cost = temp_cost;

					if (temp_cost <= this.getGlobalBestCost()) { // part of currently best alignment
						if (temp_cost == this.getGlobalBestCost() && rightChildEntry.getKey().getSequenceNumber() != -1)
							System.out.println("WARNING1! " + rightChildEntry.getKey() + ": " + -1);

						rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
					}
				}
			}

			// recursion case 2: right has no more nodes, left does
			else if (right.getChildren().isEmpty() && !left.getChildren().isEmpty()) {
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

					if (temp_cost < local_best_cost)
						local_best_cost = temp_cost;
				}
			}

			// recursion case
			else {

				// case 1: do matches first
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {

						if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) { // we found a match -> pursue it
							int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), rightChildEntry.getValue(), cost); // do not increment cost as it was a match

							if (temp_cost < local_best_cost)
								local_best_cost = temp_cost;

							if (temp_cost <= this.getGlobalBestCost()) { // part of currently best alignment
								if (temp_cost == this.getGlobalBestCost() && rightChildEntry.getKey().getSequenceNumber() != leftChildEntry.getKey().getSequenceNumber())
									System.out.println("WARNING2! " + leftChildEntry.getKey() + ": " + leftChildEntry.getKey().getSequenceNumber());

								rightChildEntry.getKey().setSequenceNumber(leftChildEntry.getKey().getSequenceNumber()); // set right sequence number to left sequence number
							}
						}

					}
				}

				// case 2: skip left
				for (SequenceGraph.Transition.Op leftChildEntry : left.getChildren()) {
					int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

					if (temp_cost < local_best_cost)
						local_best_cost = temp_cost;
				}

				// case 3: skip right
				for (SequenceGraph.Transition.Op rightChildEntry : right.getChildren()) {
					int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost);

					if (temp_cost < local_best_cost)
						local_best_cost = temp_cost;

					if (temp_cost <= this.getGlobalBestCost()) { // part of currently best alignment
						if (temp_cost == this.getGlobalBestCost() && rightChildEntry.getKey().getSequenceNumber() != -1)
							System.out.println("WARNING3! " + rightChildEntry.getKey() + ": " + -1);

						rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
					}
				}

			}

			return local_best_cost;
		}

		/**
		 * This updates the left sequence graph by merging the aligned right sequence graph into it.
		 *
		 * @param left           The left node.
		 * @param right          The right node.
		 * @param path           The current path.
		 * @param nodes          The current set of nodes.
		 * @param shared_symbols The set of shared symbols.
		 * @return The currently processed node.
		 */
		//private
		default SequenceGraph.Node.Op updateSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<Artifact.Op<?>> shared_symbols) {
			// get current graph node
			SequenceGraph.Node.Op sgn = nodes.get(path);
			if (sgn == null) {
				sgn = this.createSequenceGraphNode(!this.getPol());
				nodes.put(path, sgn);
			}

			// base case: node has already been visited
			if (sgn.getPol() == this.getPol())
				return sgn;

			// set node to visited
			sgn.setPol(this.getPol());

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

			// if shared symbol -> cut it if onesided or take it when on both sides
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
						System.out.println("MATCH SHARED");
						HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
						new_path.add(leftEntry.getKey());
						SequenceGraph.Node.Op child = this.updateSequenceGraphRec(leftEntry.getValue(), rightEntry.getValue(), new_path, nodes, shared_symbols);
						new_children.put(leftEntry.getKey(), child);
					} else { // no match for shared symbol -> cut graph
						System.out.println("MATCH CUT");
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


		public default void sequence(at.jku.isse.ecco.tree.Node.Op node) {
			if (node.getArtifact().isOrdered())
				sequenceNodes(node.getChildren());
			else
				throw new EccoException("Only ordered nodes can be sequenced.");
		}

		public default void sequenceNodes(List<? extends at.jku.isse.ecco.tree.Node.Op> nodes) {
			List<Artifact.Op<?>> artifacts = nodes.stream().map((Function<at.jku.isse.ecco.tree.Node.Op, ? extends Artifact.Op<?>>) at.jku.isse.ecco.tree.Node.Op::getArtifact).collect(Collectors.toList());
			sequenceArtifacts(artifacts);
		}

		public default void sequenceArtifacts(List<? extends Artifact.Op<?>> artifacts) {
			int[] alignment_array = align(artifacts);

			// assign new sequence numbers to artifacts that could not be matched during alignment
			int num_symbols = this.getCurrentSequenceNumber();
			for (int i = 0; i < alignment_array.length; i++) {
				if (alignment_array[i] == -1 || alignment_array[i] == 0) {
					alignment_array[i] = this.nextSequenceNumber();
					artifacts.get(i).setSequenceNumber(alignment_array[i]);
				}
			}

			// compute shared symbols
			Set<Artifact.Op<?>> shared_symbols = new HashSet<>();
			for (Artifact.Op<?> symbol : artifacts) {
				if (symbol.getSequenceNumber() < num_symbols)
					shared_symbols.add(symbol);
			}

			// update this sequence graph
			this.invertPol();
			this.updateSequenceRec(new HashSet<>(), this.collectPathMap(), shared_symbols, this.getRoot(), 0, artifacts, this.getPol());
		}

		/**
		 * Does not modify sequence graph in any way.
		 * Assigns sequence numbers to artifacts that could be matched during alignment.
		 *
		 * @param artifacts The list of artifacts to be aligned to this sequence graph.
		 * @return The result of the alignment in the form of an array of assigned sequence numbers.
		 */
		public default int[] align(List<? extends Artifact.Op<?>> artifacts) {
			int[] alignment_array = new int[artifacts.size()]; // +1? maybe remove node_right_index and use instead alignment[0]?

			this.setGlobalBestCost(Integer.MAX_VALUE);
			this.alignSequenceRec(this.getRoot(), artifacts, 0, alignment_array, 0);

			// assign sequence numbers to artifacts that had a match during alignment
			for (int i = 0; i < alignment_array.length; i++) {
				if (alignment_array[i] != -1 && alignment_array[i] != 0) {
					artifacts.get(i).setSequenceNumber(alignment_array[i]);
				}
			}

			return alignment_array;
		}

		//private
		default int alignSequenceRec(SequenceGraph.Node.Op left, List<? extends Artifact.Op<?>> artifacts, int node_right_index, int[] alignment, int cost) {
			//int cur_min_cost = Integer.MAX_VALUE;
			int cur_min_cost = this.getGlobalBestCost();

			// base case: if left has no more elements and right does
			if (left.getChildren().size() <= 0) {

				int temp_cost = cost + artifacts.size() - node_right_index;

				// update current best cost if necessary
				if (this.getGlobalBestCost() > temp_cost) {
					this.setGlobalBestCost(temp_cost);
				}

				return temp_cost;
			}

			// base case: done when left and right have no remaining elements
			if (node_right_index >= artifacts.size() && left.getChildren().size() <= 0) {

				// update current best cost if necessary
				if (this.getGlobalBestCost() > cost) {
					this.setGlobalBestCost(cost);
				}

				return cost;
			}

			boolean found_match = false;
			boolean skipped_right = false;

			// abort and don't update alignment if we already had a better or equal solution.
			if (cost >= this.getGlobalBestCost()) {
				return Integer.MAX_VALUE;
			}

			// first do the match
			if (node_right_index < artifacts.size() && left.getChildren().size() > 0) {
				for (SequenceGraph.Transition.Op entry : left.getChildren()) {
					// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
					if (entry.getKey().equals(artifacts.get(node_right_index))) {
						found_match = true;
						int temp_cost = alignSequenceRec(entry.getValue(), artifacts, node_right_index + 1, alignment, cost);
						if (temp_cost < cur_min_cost) {
							cur_min_cost = temp_cost;
							alignment[node_right_index] = entry.getKey().getSequenceNumber();
						}
					}
				}
			}

			// find other matches in right
			for (int i = node_right_index + 1; i < artifacts.size() && !found_match; i++) {
				if (cost + i - node_right_index >= cur_min_cost || cost + i - node_right_index > this.getGlobalBestCost())
					break;
				if (i < artifacts.size() && left.getChildren().size() > 0) {
					for (SequenceGraph.Transition.Op entry : left.getChildren()) {
						// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
						if (entry.getKey().equals(artifacts.get(i))) {
							found_match = true;
							skipped_right = true;
							int temp_cost = alignSequenceRec(entry.getValue(), artifacts, i + 1, alignment, cost + i - node_right_index);
							if (temp_cost < cur_min_cost) {
								cur_min_cost = temp_cost;
								alignment[i] = entry.getKey().getSequenceNumber();

								// set skipped in right to -1 because the costs were cheaper here
								for (int j = node_right_index; j < i; j++) {
									alignment[j] = -1;
								}
							}
						}
					}
				}
			}

			if (left.getChildren().size() > 0 && (skipped_right || !found_match)) { // skip left (only if for this left we skipped a right previously for a match)
				for (SequenceGraph.Transition.Op entry : left.getChildren()) {
					int temp_cost = alignSequenceRec(entry.getValue(), artifacts, node_right_index, alignment, cost + 1);
					if (temp_cost < cur_min_cost) {
						cur_min_cost = temp_cost;
						// no changes to the alignment
					}
				}
			}

			return cur_min_cost; // NOTE: this must never be Integer.MAX_VALUE
		}

		//private
		default SequenceGraph.Node.Op updateSequenceRec(HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<? extends Artifact.Op<?>> shared_symbols, SequenceGraph.Node.Op node, int alignment_index, List<? extends Artifact.Op<?>> aligned_nodes, boolean new_pol) {
			// get current graph node
			boolean new_node = false;
			SequenceGraph.Node.Op gn = nodes.get(path);
			if (gn == null) {
				//gn = new SequenceGraphNode(!new_pol);
				gn = (SequenceGraph.Node.Op) this.createSequenceGraphNode(!new_pol);
				nodes.put(path, gn);
				new_node = true;
			}

			// base case: node has already been visited
			if (gn.getPol() == new_pol)
				return gn;

			// set node to visited
			gn.setPol(new_pol);

			// determine all possible successor paths
			Map<Artifact.Op<?>, SequenceGraph.Node.Op> new_children = new HashMap<>();
			Artifact.Op<?> right = null;
			if (alignment_index < aligned_nodes.size())
				right = aligned_nodes.get(alignment_index);

			// if right unshared we can take the path (this is the adding part)
			if (right != null && !shared_symbols.contains(right)) {
				// compute new path
				@SuppressWarnings("unchecked")
				HashSet<Artifact.Op<?>> new_path = (HashSet<Artifact.Op<?>>) path.clone();
				new_path.add(right);
				// take it
				SequenceGraph.Node.Op new_gn = updateSequenceRec(new_path, nodes, shared_symbols, node, alignment_index + 1, aligned_nodes, new_pol);
				new_children.put(right, new_gn);
			}

			// gn.children.putAll(new_children); // NOTE: can this cause a concurrent modification exception?

			// for every left child (this is the cutting part)
			Iterator<SequenceGraph.Transition.Op> it = node.getChildren().iterator();
			while (it.hasNext()) {
				SequenceGraph.Transition.Op entry = it.next();
				// if left child unshared we can take it
				if (!shared_symbols.contains(entry.getKey())) {
					// compute new path
					@SuppressWarnings("unchecked")
					HashSet<Artifact.Op<?>> new_path = (HashSet<Artifact.Op<?>>) path.clone();
					new_path.add(entry.getKey());
					// take it
					SequenceGraph.Node.Op new_gn = updateSequenceRec(new_path, nodes, shared_symbols, entry.getValue(), alignment_index, aligned_nodes, new_pol);
					// X not a new child. do nothing with new_children.
					//if (new_node)
					new_children.put(entry.getKey(), new_gn);
				} else { // left child shared
					// if left child and right are equal we can take it
					if (right != null && right.equals(entry.getKey())) {
						// compute new path
						@SuppressWarnings("unchecked")
						HashSet<Artifact.Op<?>> new_path = (HashSet<Artifact.Op<?>>) path.clone();
						new_path.add(entry.getKey());
						// take it
						SequenceGraph.Node.Op new_gn = updateSequenceRec(new_path, nodes, shared_symbols, entry.getValue(), alignment_index + 1, aligned_nodes, new_pol);
						// X not a new child. do nothing with new_children.
						//if (new_node)
						new_children.put(entry.getKey(), new_gn);
					} else { // TODO: do this only when right child is also shared? consider doing first adding then removing separately.
						// cut the branch
						it.remove();
					}
				}
			}

			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : new_children.entrySet()) {
				gn.addTransition(entry.getKey(), entry.getValue());
			}
			//gn.getChildren().addAll(new_children);

			return gn;
		}


		// ###############################################


		public default void updateArtifactReferences() {
			// update node list
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
			this.setPol(!this.getPol());
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
			Set<Artifact.Op<?>> path = new HashSet<>();
			this.setPol(!this.getPol());
			this.collectPathMapRec(this.getRoot(), path, nodes);
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
			this.setPol(!this.getPol());
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

			this.setPol(!this.getPol());
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

			other.setPol(!other.getPol());
			//other.setPol(!other.getRoot().getPol());

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

}
