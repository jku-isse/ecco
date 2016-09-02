package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;

import java.util.*;
import java.util.stream.Collectors;

public class SequenceGraphOperator {

	private SequenceGraphOperand sequenceGraph;

	public SequenceGraphOperator(SequenceGraphOperand sequenceGraph) {
		this.sequenceGraph = sequenceGraph;
	}

	private int global_best_cost = Integer.MAX_VALUE;


	// # SG #################################################################


	public void sequence(SequenceGraph other) {
//		 TODO: FIX THIS!
//
//		 align other to this.sequenceGraph
//
//		 traverse other to retrieve every order and sequence it into the new sequence graph.
//		this.traverseSequenceGraphForOrder(other.getRoot(), new ArrayList<Artifact<?>>());


		// align right to left
		this.global_best_cost = Integer.MAX_VALUE;
		this.alignSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), 0);

		// assign new sequence numbers to right
		int num_symbols = this.sequenceGraph.getCurrentSequenceNumber();
		Set<Artifact<?>> rightArtifacts = new HashSet<>();
		this.assignNewSequenceNumbers(other.getRoot(), rightArtifacts);


		// update left
		Set<Artifact<?>> shared_symbols = new HashSet<>();
		for (Artifact symbol : rightArtifacts) {
			if (symbol.getSequenceNumber() < num_symbols)
				shared_symbols.add(symbol);
		}
		this.updateSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), new HashSet<Artifact<?>>(), shared_symbols, !this.sequenceGraph.getPol());
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());

		// remove all graphnodes that were not visited
		Iterator<SequenceGraphNode> it = this.sequenceGraph.getNodes().values().iterator();
		while (it.hasNext()) {
			SequenceGraphNode gn = it.next();
			if (gn.getPol() != this.sequenceGraph.getPol()) {
				it.remove();
			}
		}
	}

	private void assignNewSequenceNumbers(SequenceGraphNode sgn, Set<Artifact<?>> artifacts) {
		for (Map.Entry<Artifact<?>, SequenceGraphNode> child : sgn.getChildren().entrySet()) {
			artifacts.add(child.getKey());

			if (child.getKey().getSequenceNumber() == -1)
				child.getKey().setSequenceNumber(this.sequenceGraph.nextSequenceNumber());

			this.assignNewSequenceNumbers(child.getValue(), artifacts);
		}
	}

//	private void traverseSequenceGraphForOrder(SequenceGraphNode sgn, List<Artifact<?>> order) {
//		if (sgn.getChildren().isEmpty()) {
//			// we have reached the end and the current path is a possible plath
//			System.out.println("ORDER1: " + order + ", " + this.sequenceGraph.getNodes().size());
//			this.sequenceGraph.sequenceArtifacts(order);
//			System.out.println("ORDER2: " + order + ", " + this.sequenceGraph.getNodes().size());
//		} else {
//			// keep traversing
//			for (Map.Entry<Artifact<?>, SequenceGraphNode> child : sgn.getChildren().entrySet()) {
//				order.add(child.getKey());
//
//				this.traverseSequenceGraphForOrder(child.getValue(), order);
//
//				// when we are done remove current from order
//				order.remove(order.size() - 1);
//			}
//		}
//	}

	/**
	 * This aligns the right sequence graph to the left sequence graph, assigning sequence numbers to the artifacts of the right sequence graph.
	 * <p>
	 * NOTES:
	 * - careful: changing the sequence number of artifacts changes their hashCode() and equals()!
	 */
	private int alignSequenceGraphRec(SequenceGraphNode left, SequenceGraphNode right, int cost) {

		//int local_best_cost = this.global_best_cost;
		int local_best_cost = Integer.MAX_VALUE;

		// base case 0: abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.global_best_cost) {
			return Integer.MAX_VALUE;
		}

		// base case 1: both sequence graphs have no more nodes
		else if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
			// update current best cost (we would not have gotten here if cost > global_best_cost)
			this.global_best_cost = cost;
			return cost;
		}

		// base case 2: left has no more nodes, right does
		else if (left.getChildren().isEmpty() && !right.getChildren().isEmpty()) {
			for (Map.Entry<Artifact<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;

				if (temp_cost <= this.global_best_cost) { // part of currently best alignment
					if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != -1)
						System.out.println("WARNING1! " + rightChildEntry.getKey() + ": " + -1);

					rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
				}
			}
		}

		// base case 3: right has no more nodes, left does
		else if (right.getChildren().isEmpty() && !left.getChildren().isEmpty()) {
			for (Map.Entry<Artifact<?>, SequenceGraphNode> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}
		}

		// recursion case
		else {

			// case 1: do matches first
			for (Map.Entry<Artifact<?>, SequenceGraphNode> leftChildEntry : left.getChildren().entrySet()) {
				for (Map.Entry<Artifact<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {

					if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) {
						if (rightChildEntry != null) { // we found a match -> pursue it
							int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), rightChildEntry.getValue(), cost); // do not increment cost as it was a match

							if (temp_cost < local_best_cost)
								local_best_cost = temp_cost;

							if (temp_cost <= this.global_best_cost) { // part of currently best alignment
								if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != leftChildEntry.getKey().getSequenceNumber())
									System.out.println("WARNING2! " + leftChildEntry.getKey() + ": " + leftChildEntry.getKey().getSequenceNumber());

								rightChildEntry.getKey().setSequenceNumber(leftChildEntry.getKey().getSequenceNumber()); // set right sequence number to left sequence number
							}
						}
					}

				}
			}

			// case 2: skip left
			for (Map.Entry<Artifact<?>, SequenceGraphNode> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}

			// case 3: skip right
			for (Map.Entry<Artifact<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(left, rightChildEntry.getValue(), cost);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;

				if (temp_cost <= this.global_best_cost) { // part of currently best alignment
					if (temp_cost == this.global_best_cost && rightChildEntry.getKey().getSequenceNumber() != -1)
						System.out.println("WARNING3! " + rightChildEntry.getKey() + ": " + -1);

					rightChildEntry.getKey().setSequenceNumber(-1); // indicate that this artifact needs a new sequence number assigned
				}
			}

		}

		return local_best_cost;
	}

	/**
	 * This updates the left sequence graph by merging the aligned right sequence graph into it.
	 */
	private SequenceGraphNode updateSequenceGraphRec(SequenceGraphNode left, SequenceGraphNode right, HashSet<Artifact<?>> path, Set<Artifact<?>> shared_symbols, boolean new_pol) {

		// get current graph node
		SequenceGraphNode sgn = this.sequenceGraph.getNodes().get(path);
		if (sgn == null) {
			sgn = this.sequenceGraph.createSequenceGraphNode(!new_pol);
			this.sequenceGraph.getNodes().put(path, sgn);
		}

		// base case: node has already been visited
		if (sgn.getPol() == new_pol)
			return sgn;

		// set node to visited
		sgn.setPol(new_pol);

		HashMap<Artifact<?>, SequenceGraphNode> new_children = new HashMap<>();


		// if unshared symbol left -> advance
		for (Map.Entry<Artifact<?>, SequenceGraphNode> leftEntry : left.getChildren().entrySet()) {
			if (!shared_symbols.contains(leftEntry.getKey())) {
				HashSet<Artifact<?>> new_path = new HashSet<>(path);
				new_path.add(leftEntry.getKey());
				SequenceGraphNode child = this.updateSequenceGraphRec(leftEntry.getValue(), right, new_path, shared_symbols, new_pol);
				new_children.put(leftEntry.getKey(), child);
			}
		}

		// if unshared symbol right -> add it left and advance
		for (Map.Entry<Artifact<?>, SequenceGraphNode> rightEntry : right.getChildren().entrySet()) {
			if (!shared_symbols.contains(rightEntry.getKey())) {
				HashSet<Artifact<?>> new_path = new HashSet<>(path);
				new_path.add(rightEntry.getKey());
				SequenceGraphNode child = this.updateSequenceGraphRec(left, rightEntry.getValue(), new_path, shared_symbols, new_pol); // this should be a new node
				new_children.put(rightEntry.getKey(), child);
			}
		}

		// if shared symbol -> cut it if onesided or take it when on both sides
		Iterator<Map.Entry<Artifact<?>, SequenceGraphNode>> it = left.getChildren().entrySet().iterator();
		//for (Map.Entry<Artifact<?>, SequenceGraphNode> leftEntry : left.getChildren().entrySet()) {
		while (it.hasNext()) {
			Map.Entry<Artifact<?>, SequenceGraphNode> leftEntry = it.next();
			if (shared_symbols.contains(leftEntry.getKey())) {
				Map.Entry<Artifact<?>, SequenceGraphNode> rightEntry = null;
				for (Map.Entry<Artifact<?>, SequenceGraphNode> tempRightEntry : right.getChildren().entrySet()) {
					if (tempRightEntry.getKey().equals(leftEntry.getKey())) {
						rightEntry = tempRightEntry;
						break;
					}
				}
				if (rightEntry != null) { // matching shared symbols -> take them
					HashSet<Artifact<?>> new_path = new HashSet<>(path);
					new_path.add(rightEntry.getKey());
					SequenceGraphNode child = this.updateSequenceGraphRec(leftEntry.getValue(), rightEntry.getValue(), new_path, shared_symbols, new_pol);
					new_children.put(leftEntry.getKey(), child);
				} else { // no match for shared symbol -> cut graph
					it.remove();
				}
			}
		}


		sgn.getChildren().putAll(new_children);

		return sgn;
	}


	// # OPERATIONS #################################################################


	public void updateArtifactReferences() {
		// update graph
		this.updateArtifactReferencesRec(this.sequenceGraph.getRoot());

		// update node list
		Iterator<Map.Entry<Set<Artifact<?>>, SequenceGraphNode>> it = this.sequenceGraph.getNodes().entrySet().iterator();
		while (it.hasNext()) {
			Set<Artifact<?>> artifacts = it.next().getKey();

			Set<Artifact<?>> updatedArtifacts = new HashSet<>();
			Iterator<Artifact<?>> it2 = artifacts.iterator();
			while (it2.hasNext()) {
				Artifact<?> artifact = it2.next();

				if (artifact.getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact<?> replacing = artifact.<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					replacing.setSequenceNumber(artifact.getSequenceNumber());

					updatedArtifacts.add(replacing);
					it2.remove();
				}
			}
			artifacts.addAll(updatedArtifacts);
		}

	}

	private void updateArtifactReferencesRec(SequenceGraphNode sgn) {
		// update references in children
		Map<Artifact<?>, SequenceGraphNode> updatedChildren = new HashMap<>();
		Iterator<Map.Entry<Artifact<?>, SequenceGraphNode>> it = sgn.getChildren().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Artifact<?>, SequenceGraphNode> entry = it.next();
			if (entry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
				Artifact<?> replacing = entry.getKey().<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
				replacing.setSequenceNumber(entry.getKey().getSequenceNumber());

				updatedChildren.put(replacing, entry.getValue());
				it.remove();
			}
		}
		sgn.getChildren().putAll(updatedChildren);

		// traverse children
		for (Map.Entry<Artifact<?>, SequenceGraphNode> child : sgn.getChildren().entrySet()) {
			this.updateArtifactReferencesRec(child.getValue());
		}
	}


	public void sequence(Node node) throws EccoException {
		if (node.getArtifact().isOrdered())
			sequenceNodes(node.getChildren());
		else
			throw new EccoException("Only ordered nodes can be sequenced.");
	}

	public void sequenceNodes(List<Node> nodes) throws EccoException {
		List<Artifact<?>> artifacts = nodes.stream().map((Node n) -> n.getArtifact()).collect(Collectors.toList());
		sequenceArtifacts(artifacts);
	}

	public void sequenceArtifacts(List<Artifact<?>> artifacts) throws EccoException {
		int num_symbols = this.sequenceGraph.getCurrentSequenceNumber();
		int[] alignment = align(artifacts);

		//if (num_symbols != this.sequenceGraph.getCurrentSequenceNumber()) {
		Set<Artifact<?>> shared_symbols = new HashSet<>();
		for (Artifact symbol : artifacts) {
			if (symbol.getSequenceNumber() < num_symbols)
				shared_symbols.add(symbol);
		}

		update_rec(new HashSet<Artifact<?>>(), shared_symbols, this.sequenceGraph.getRoot(), 0, artifacts, !this.sequenceGraph.getPol());
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());

		// remove all graphnodes that were not visited
		Iterator<SequenceGraphNode> it = this.sequenceGraph.getNodes().values().iterator();
		while (it.hasNext()) {
			SequenceGraphNode gn = it.next();
			if (gn.getPol() != this.sequenceGraph.getPol()) {
				it.remove();
			}
		}
		//}
	}


	public int[] align(List<Artifact<?>> artifacts) throws EccoException {
		int[] alignment_array = new int[artifacts.size()]; // +1? maybe remove node_right_index and use instead alignment[0]?

		this.global_best_cost = Integer.MAX_VALUE;
		align_rec_fast(this.sequenceGraph.getRoot(), artifacts, 0, alignment_array, 0);

		// finalize alignment
		for (int i = 0; i < alignment_array.length; i++) {
			if (alignment_array[i] == -1 || alignment_array[i] == 0) {
				alignment_array[i] = this.sequenceGraph.nextSequenceNumber();
			}
		}

		// set sequence number in nodes according to alignment
		for (int i = 0; i < alignment_array.length; i++) {
			if (alignment_array[i] == 0)
				throw new EccoException("Error: no sequence number assigned! (" + i + ")");
			artifacts.get(i).setSequenceNumber(alignment_array[i]);
		}

		return alignment_array;
	}


	private int align_rec_fast(SequenceGraphNode left, List<Artifact<?>> artifacts, int node_right_index, int[] alignment, int cost) {

		//int cur_min_cost = Integer.MAX_VALUE;
		int cur_min_cost = this.global_best_cost;

		// base case: if left has no more elements and right does
		if (left.getChildren().size() <= 0) {

			int temp_cost = cost + artifacts.size() - node_right_index;

			// update current best cost if necessary
			if (this.global_best_cost > temp_cost) {
				this.global_best_cost = temp_cost;
			}

			return temp_cost;
		}

		// base case: done when left and right have no remaining elements
		if (node_right_index >= artifacts.size() && left.getChildren().size() <= 0) {

			// update current best cost if necessary
			if (this.global_best_cost > cost) {
				this.global_best_cost = cost;
			}

			return cost;
		}

		boolean found_match = false;
		boolean skipped_right = false;

		// abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.global_best_cost) {
			return Integer.MAX_VALUE;
		}

		// first do the match
		if (node_right_index < artifacts.size() && left.getChildren().size() > 0) {
			for (Map.Entry<Artifact<?>, SequenceGraphNode> entry : left.getChildren().entrySet()) {
				// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
				if (entry.getKey().equals(artifacts.get(node_right_index))) {
					found_match = true;
					int temp_cost = align_rec_fast(entry.getValue(), artifacts, node_right_index + 1, alignment, cost);
					if (temp_cost < cur_min_cost) {
						cur_min_cost = temp_cost;
						alignment[node_right_index] = entry.getKey().getSequenceNumber();
					}
				}
			}
		}

		// find other matches in right
		for (int i = node_right_index + 1; i < artifacts.size() && !found_match; i++) {
			if (cost + i - node_right_index >= cur_min_cost || cost + i - node_right_index > this.global_best_cost)
				break;
			if (i < artifacts.size() && left.getChildren().size() > 0) {
				for (Map.Entry<Artifact<?>, SequenceGraphNode> entry : left.getChildren().entrySet()) {
					// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
					if (entry.getKey().equals(artifacts.get(i))) {
						found_match = true;
						skipped_right = true;
						int temp_cost = align_rec_fast(entry.getValue(), artifacts, i + 1, alignment, cost + i - node_right_index);
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
			for (Map.Entry<Artifact<?>, SequenceGraphNode> entry : left.getChildren().entrySet()) {
				int temp_cost = align_rec_fast(entry.getValue(), artifacts, node_right_index, alignment, cost + 1);
				if (temp_cost < cur_min_cost) {
					cur_min_cost = temp_cost;
					// no changes to the alignment
				}
			}
		}

		return cur_min_cost; // NOTE: this must never be Integer.MAX_VALUE
	}

	private SequenceGraphNode update_rec(HashSet<Artifact<?>> path, Set<Artifact<?>> shared_symbols, SequenceGraphNode node, int alignment_index, List<Artifact<?>> aligned_nodes, boolean new_pol) {

		// get current graph node
		boolean new_node = false;
		SequenceGraphNode gn = this.sequenceGraph.getNodes().get(path);
		if (gn == null) {
			//gn = new SequenceGraphNode(!new_pol);
			gn = (SequenceGraphNode) this.sequenceGraph.createSequenceGraphNode(!new_pol);
			this.sequenceGraph.getNodes().put(path, gn);
			new_node = true;
		}

		// base case: node has already been visited
		if (gn.getPol() == new_pol)
			return gn;

		// set node to visited
		gn.setPol(new_pol);

		// determine all possible successor paths
		HashMap<Artifact<?>, SequenceGraphNode> new_children = new HashMap<>();
		Artifact<?> right = null;
		if (alignment_index < aligned_nodes.size())
			right = aligned_nodes.get(alignment_index);

		// if right unshared we can take the path (this is the adding part)
		if (right != null && !shared_symbols.contains(right)) {
			// compute new path
			@SuppressWarnings("unchecked")
			HashSet<Artifact<?>> new_path = (HashSet<Artifact<?>>) path.clone();
			new_path.add(right);
			// take it
			SequenceGraphNode new_gn = update_rec(new_path, shared_symbols, node, alignment_index + 1, aligned_nodes, new_pol);
			new_children.put(right, new_gn);
		}

		// gn.children.putAll(new_children); // NOTE: can this cause a concurrent modification exception?

		// for every left child (this is the cutting part)
		Iterator<Map.Entry<Artifact<?>, SequenceGraphNode>> it = node.getChildren().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Artifact<?>, SequenceGraphNode> entry = it.next();
			// if left child unshared we can take it
			if (!shared_symbols.contains(entry.getKey())) {
				// compute new path
				@SuppressWarnings("unchecked")
				HashSet<Artifact<?>> new_path = (HashSet<Artifact<?>>) path.clone();
				new_path.add(entry.getKey());
				// take it
				SequenceGraphNode new_gn = update_rec(new_path, shared_symbols, entry.getValue(), alignment_index, aligned_nodes, new_pol);
				// X not a new child. do nothing with new_children.
				//if (new_node)
				new_children.put(entry.getKey(), new_gn);
			} else { // left child shared
				// if left child and right are equal we can take it
				if (right != null && right.equals(entry.getKey())) {
					// compute new path
					@SuppressWarnings("unchecked")
					HashSet<Artifact<?>> new_path = (HashSet<Artifact<?>>) path.clone();
					new_path.add(entry.getKey());
					// take it
					SequenceGraphNode new_gn = update_rec(new_path, shared_symbols, entry.getValue(), alignment_index + 1, aligned_nodes, new_pol);
					// X not a new child. do nothing with new_children.
					//if (new_node)
					new_children.put(entry.getKey(), new_gn);
				} else { // TODO: do this only when right child is also shared? consider doing first adding then removing separately.
					// cut the branch
					it.remove();
				}
			}
		}

		gn.getChildren().putAll(new_children);

		return gn;
	}


	// # INTERFACE #################################################################

	public interface SequenceGraphOperand extends SequenceGraph {
		public Map<Set<Artifact<?>>, SequenceGraphNode> getNodes();


		public int getCurrentSequenceNumber();

		public int nextSequenceNumber() throws EccoException;


		public boolean getPol();

		public void setPol(boolean pol);


		public SequenceGraphNode createSequenceGraphNode(boolean pol);
	}

}
