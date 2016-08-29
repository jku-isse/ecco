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

	private int cur_best_cost = Integer.MAX_VALUE;


	// # OPERATIONS #################################################################


	public void sequence(SequenceGraph other) {
		// TODO: align other to this.sequenceGraph
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

		if (num_symbols != this.sequenceGraph.getCurrentSequenceNumber()) {
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
		}
	}


	public int[] align(List<Artifact<?>> artifacts) throws EccoException {
		int[] alignment_array = new int[artifacts.size()]; // +1? maybe remove node_right_index and use instead alignment[0]?

		this.cur_best_cost = Integer.MAX_VALUE;
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
		int cur_min_cost = this.cur_best_cost;

		// base case: if left has no more elements and right does
		if (left.getChildren().size() <= 0) {

			int temp_cost = cost + artifacts.size() - node_right_index;

			// update current best cost if necessary
			if (this.cur_best_cost > temp_cost) {
				this.cur_best_cost = temp_cost;
			}

			return temp_cost;
		}

		// base case: done when left and right have no remaining elements
		if (node_right_index >= artifacts.size() && left.getChildren().size() <= 0) {

			// update current best cost if necessary
			if (this.cur_best_cost > cost) {
				this.cur_best_cost = cost;
			}

			return cost;
		}

		boolean found_match = false;
		boolean skipped_right = false;

		// abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.cur_best_cost) {
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
			if (cost + i - node_right_index >= cur_min_cost || cost + i - node_right_index > this.cur_best_cost)
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
