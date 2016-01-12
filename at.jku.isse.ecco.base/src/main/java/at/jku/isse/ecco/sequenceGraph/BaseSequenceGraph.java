package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;

import java.util.*;
import java.util.Map.Entry;

public class BaseSequenceGraph implements SequenceGraph {

	public SequenceGraphNode createSequenceGraphNode(boolean pol) {
		return new BaseSequenceGraphNode(pol);
	}


	// #########################################################


	// enable alignment
	private static final boolean ALIGNMENT = true;
	// enable sequencing
	private static final boolean SEQUENCING = true;

	public BaseSequenceGraph() {
		this.pol = true;
		this.root = (BaseSequenceGraphNode) this.createSequenceGraphNode(this.pol);
		this.nodes.put(new HashSet<Node>(), this.root);
	}

	public void sequence(OrderedNode node) {
		int num_symbols = this.cur_seq_number;
//		System.out.println("start alignment: (" + this.cur_seq_number + ")" + node.getArtifact() + ";" + node.getArtifact().getParent());
		int[] alignment = this.align(node);
//		System.out.println("done alignment: " + this.num_comparisons);

		//if (SEQUENCING && !node.isSequenced()) {
		if (SEQUENCING && num_symbols != this.cur_seq_number) {
//			System.out.println("start sequencing");
			Set<Node> shared_symbols = new HashSet<Node>();
			for (Node symbol : node.getOrderedChildren()) {
				if (symbol.getSequenceNumber() < num_symbols)
					shared_symbols.add(symbol);
			}

			this.update_rec(new HashSet<Node>(), shared_symbols, this.root, 0, node.getOrderedChildren(), !this.pol);
			this.pol = !this.pol;

			// remove all graphnodes that were not visited
			Iterator<SequenceGraphNode> it = this.nodes.values().iterator();
			while (it.hasNext()) {
				SequenceGraphNode gn = it.next();
				if (gn.getPol() != this.pol) {
					it.remove();
				}
			}
//			System.out.println("done sequencing");
		}

		// add ordered_nodes to all and unique nodes...
		for (Node n : node.getOrderedChildren()) {
			node.getAllChildren().add(n);
			node.getUniqueChildren().add(n);
		}

		node.setSequenced(true);
		node.setSequenceGraph(this);
	}

	// NOTE: consider corner case of root changing? can that happen?


	// TODO: use path only when necessary: i.e. when adding a new path
	// TODO: use for path not the nodes but just the integer
	// TODO: use for graph also only integers instead of nodes and for alignment look up the matches between integers and identifiers in a string array.

	/*
	 * There is a lot of optimisation potential in this current implementation!!!
	 */
	private SequenceGraphNode update_rec(HashSet<Node> path, Set<Node> shared_symbols, SequenceGraphNode node, int alignment_index, List<Node> aligned_nodes,
										 boolean new_pol) {

		// get current graph node
		boolean new_node = false;
		SequenceGraphNode gn = this.nodes.get(path);
		if (gn == null) {
			//gn = new SequenceGraphNode(!new_pol);
			gn = (BaseSequenceGraphNode) this.createSequenceGraphNode(!new_pol);
			this.nodes.put(path, gn);
			new_node = true;
		}

		// base case: node has already been visited
		if (gn.getPol() == new_pol)
			return gn;

		// set node to visited
		gn.setPol(new_pol);

		// determine all possible successor paths
		HashMap<Node, SequenceGraphNode> new_children = new HashMap<Node, SequenceGraphNode>();
		Node right = null;
		if (alignment_index < aligned_nodes.size())
			right = aligned_nodes.get(alignment_index);

		// if right unshared we can take the path (this is the adding part)
		if (right != null && !shared_symbols.contains(right)) {
			// compute new path
			@SuppressWarnings("unchecked")
			HashSet<Node> new_path = (HashSet<Node>) path.clone();
			new_path.add(right);
			// take it
			SequenceGraphNode new_gn = this.update_rec(new_path, shared_symbols, node, alignment_index + 1, aligned_nodes, new_pol);
			new_children.put(right, new_gn);
		}

		// gn.children.putAll(new_children); // NOTE: can this cause a concurrent modification exception?

		// for every left child (this is the cutting part)
		Iterator<Entry<Node, SequenceGraphNode>> it = node.getChildren().entrySet().iterator();
		while (it.hasNext()) {
			Entry<Node, SequenceGraphNode> entry = it.next();
			// if left child unshared we can take it
			if (!shared_symbols.contains(entry.getKey())) {
				// compute new path
				@SuppressWarnings("unchecked")
				HashSet<Node> new_path = (HashSet<Node>) path.clone();
				new_path.add(entry.getKey());
				// take it
				SequenceGraphNode new_gn = this.update_rec(new_path, shared_symbols, entry.getValue(), alignment_index, aligned_nodes, new_pol);
				// X not a new child. do nothing with new_children.
				//if (new_node)
				new_children.put(entry.getKey(), new_gn);
			} else { // left child shared
				// if left child and right are equal we can take it
				if (right != null && right.equals(entry.getKey())) {
					// compute new path
					@SuppressWarnings("unchecked")
					HashSet<Node> new_path = (HashSet<Node>) path.clone();
					new_path.add(entry.getKey());
					// take it
					SequenceGraphNode new_gn = this.update_rec(new_path, shared_symbols, entry.getValue(), alignment_index + 1, aligned_nodes, new_pol);
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

	// ####################### ALIGNMENT ############################

	public int[] align(OrderedNode node) {
		int[] alignment_array = new int[node.getOrderedChildren().size()]; // +1? maybe remove node_right_index and use instead alignment[0]?

		this.num_comparisons = 0;

		this.cur_best_cost = Integer.MAX_VALUE;
		// this.align_rec(this.root, node, 0, alignment_array, 0);
		this.align_rec_fast(this.root, node, 0, alignment_array, 0);
		// TODO: this is only for testing!

		// TODO: try this out again! "found better solution" and "mismatch"!
//		this.cur_best_cost = Integer.MAX_VALUE;
//		int[] alignment_array2 = new int[node.ordered_children.size()];
//		this.align_rec(this.root, node, 0, alignment_array2, 0);
//		boolean mismatch = false;
//		for (int i = 0; i < alignment_array.length && ! mismatch; i++) {
//			if (alignment_array[i] != alignment_array2[i] && alignment_array[i] != 0)
//				mismatch = true;
//		}
//		if (mismatch) {
//			System.out.println("MISMATCH: " + this.cur_seq_number);
//			for (int i = 0; i < alignment_array.length; i++)
//				System.out.print(alignment_array[i] + ", ");
//			System.out.println();
//			for (int i = 0; i < alignment_array2.length; i++)
//				System.out.print(alignment_array2[i] + ", ");
//			System.out.println();
//		}

//		if (node.getArtifact().getIdentifier().contains("getDefaultPerspectives")) {
//			System.out.print("AAA: " + this.cur_seq_number + ";" + this.cur_best_cost + ";");
//			for (int i = 0; i < alignment_array.length; i++)
//				System.out.print(alignment_array[i] + ", ");
//		}


		boolean do_sequencing = false;

		// finalize alignment
		for (int i = 0; i < alignment_array.length; i++) {
			//System.out.println(alignment_array[i] + "; ");
			if (alignment_array[i] == -1 || alignment_array[i] == 0) {
				alignment_array[i] = this.nextSequenceNumber();
				do_sequencing = true;
			}
		}


//		if (node.getArtifact().getIdentifier().contains("getDefaultPerspectives")) {
//		System.out.print("AAA: " + this.cur_seq_number + ";" + this.cur_best_cost + ";");
//			for (int i = 0; i < alignment_array.length; i++)
//				System.out.print(alignment_array[i] + ", ");
//		}


		// set sequence number in nodes according to alignment
		for (int i = 0; i < alignment_array.length; i++) {
			if (alignment_array[i] == 0)
				System.out.println("Error: no sequence number assigned! (" + i + ")");
			node.getOrderedChildren().get(i).setSequenceNumber(alignment_array[i]);
		}

		node.setAligned(true);

//		if (!do_sequencing) {
//			node.setSequenced(true);
//		}

		return alignment_array;
	}

	private int num_comparisons;

	private int align_rec_fast(SequenceGraphNode left, OrderedNode right, int node_right_index, int[] alignment, int cost) {

		this.num_comparisons++;

//		if (left.children.size() > 1) {
//			System.out.println("BRANCHING!");
//		}

		//int cur_min_cost = Integer.MAX_VALUE;
		int cur_min_cost = this.cur_best_cost;

		// base case: if left has no more elements and right does
		if (left.getChildren().size() <= 0) {

			int temp_cost = cost + right.getOrderedChildren().size() - node_right_index;

			//System.out.println("COST: " + temp_cost);

			// update current best cost if necessary
			if (this.cur_best_cost > temp_cost) {
				this.cur_best_cost = temp_cost;

				// TODO: also make this fix for other right skips that are not at the end!!!
//				for (int i = node_right_index; i < right.ordered_children.size(); i++)
//					alignment[i] = -1;
			}

//			System.out.println("DONE1: " + temp_cost + " / " + this.cur_best_cost);

			return temp_cost;
		}

		// base case: done when left and right have no remaining elements
		if (node_right_index >= right.getOrderedChildren().size() && left.getChildren().size() <= 0) {

			// update current best cost if necessary
			if (this.cur_best_cost > cost) {
				this.cur_best_cost = cost;
			}

//			Artifact temp = right.getArtifact();
//			String ident = temp.getIdentifier() + ";";
//			while (temp.getParent() != null) {
//				temp = temp.getParent();
//				ident += temp.getIdentifier() + ";";
//			}
//
//			System.out.println("DONE: " + ident + "; " + this.cur_best_cost + ";" + cost + ";" + this.cur_seq_number);
//			if (right.ordered_children.size() > 0) {
//				System.out.println("GGG: " + right.ordered_children.get(0).getArtifact());
//
//				if (right.ordered_children.get(0).getArtifact().getIdentifier().startsWith("LOG.info(\"Loading to exte"))
//					System.out.println("VVV: " + cost);
//			}
//

//			System.out.println("DONE2" + cost + " / " + this.cur_best_cost);

			return cost;
		}

		boolean found_match = false;
		boolean skipped_right = false;

		// abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.cur_best_cost) {
			//System.out.println("ABORT: " + cost + " / " + this.cur_best_cost);
			return Integer.MAX_VALUE;
		}

		// first do the match
		if (node_right_index < right.getOrderedChildren().size() && left.getChildren().size() > 0) {
			for (Entry<Node, SequenceGraphNode> entry : left.getChildren().entrySet()) {
				// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
				if (entry.getKey().getArtifact().equals(right.getOrderedChildren().get(node_right_index).getArtifact())) {
					//System.out.println("MATCHCCCCC: " + entry.getKey().getArtifact());
					found_match = true;
					int temp_cost = this.align_rec_fast(entry.getValue(), right, node_right_index + 1, alignment, cost);
					if (temp_cost < cur_min_cost) {
						//System.out.println("Setting1 " + node_right_index + " to " + entry.getKey().hashCode() + " with " + temp_cost + "/" + cur_min_cost);
						cur_min_cost = temp_cost;
//						if (this.cur_best_cost > cur_min_cost)
//							this.cur_best_cost = cur_min_cost;
						//alignment[node_right_index] = entry.getKey().hashCode(); // NOTE: this must be the sequence number!
						alignment[node_right_index] = entry.getKey().getSequenceNumber();
						//System.out.println("Setting1 " + node_right_index + " to " + entry.getKey().hashCode() + " with " + temp_cost + "/" + cur_min_cost);
					}
				}
			}
		}


		// abort and don't update alignment if we already had a better or equal solution.
//		if (cur_min_cost > this.cur_best_cost) {
//			return Integer.MAX_VALUE;
//		}


//if (right.getArtifact().getIdentifier().contains("getDefaultPerspectives"))
//System.out.println("mismatch");
		// find other matches in right
		//for (int i = node_right_index + 1; i < right.ordered_children.size() && i < node_right_index + 12 && !found_match; i++) {
		for (int i = node_right_index + 1; i < right.getOrderedChildren().size() && !found_match; i++) {
			if (cost + i - node_right_index >= cur_min_cost || cost + i - node_right_index > this.cur_best_cost)
				break;
			if (i < right.getOrderedChildren().size() && left.getChildren().size() > 0) {
				for (Entry<Node, SequenceGraphNode> entry : left.getChildren().entrySet()) {
					// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
					if (entry.getKey().getArtifact().equals(right.getOrderedChildren().get(i).getArtifact())) {
						//System.out.println("MATCHBBBBBB at " + i);
						found_match = true;
						skipped_right = true;
						int temp_cost = this.align_rec_fast(entry.getValue(), right, i + 1, alignment, cost + i - node_right_index);
						if (temp_cost < cur_min_cost) {
							cur_min_cost = temp_cost;
//							if (this.cur_best_cost > cur_min_cost)
//								this.cur_best_cost = cur_min_cost;
							//alignment[i] = entry.getKey().hashCode(); // NOTE: this must be the sequence number!
							alignment[i] = entry.getKey().getSequenceNumber();
							//System.out.println("Setting2 " + i + " to " + entry.getKey().hashCode());

							// set skipped in right to -1 because the costs were cheaper here
							for (int j = node_right_index; j < i; j++) {
								alignment[j] = -1;
							}
						}
					}
				}
			}
		}


		// abort and don't update alignment if we already had a better or equal solution.
//		if (cur_min_cost >= this.cur_best_cost) {
//			return Integer.MAX_VALUE;
//		}

//		if (cost + right.ordered_children.size() - node_right_index + this.cur_seq_number < cur_min_cost) {
//			int temp_cost = cost + right.ordered_children.size() + this.cur_seq_number - node_right_index;
//			if (temp_cost < cur_min_cost) {
//				cur_min_cost = temp_cost;
////				if (this.cur_best_cost > cur_min_cost)
////					this.cur_best_cost = cur_min_cost;
//				for (int j = node_right_index; j < right.ordered_children.size(); j++) {
//					alignment[j] = -1; // HERE IS THE PROBLEM
//				}
//			}
//		}

		if (left.getChildren().size() > 0 && (skipped_right || !found_match)) { // skip left (only if for this left we skipped a right previously for a match)
			//for (SequenceGraphNode n : left.children.values()) {
			for (Entry<Node, SequenceGraphNode> entry : left.getChildren().entrySet()) {
				//System.out.println("SKIPLEFT: " + entry.getKey().getArtifact());
				int temp_cost = this.align_rec_fast(entry.getValue(), right, node_right_index, alignment, cost + 1);
				if (temp_cost < cur_min_cost) {
					cur_min_cost = temp_cost;
					// no changes to the alignment

//					if (this.cur_best_cost > cur_min_cost)
//						this.cur_best_cost = cur_min_cost;
				}
			}
		}

		// do right remainder
//		for (int i = node_right_index + 12; i < right.ordered_children.size(); i++) {
//			if (cost + i - node_right_index >= cur_min_cost || cost + i - node_right_index > this.cur_best_cost)
//				break;
//			if (i < right.ordered_children.size() && left.children.size() > 0) {
//				for (Entry<Node, SequenceGraphNode> entry : left.children.entrySet()) {
//					// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
//					if (entry.getKey().getArtifact().equals(right.ordered_children.get(i).getArtifact())) {
//						//System.out.println("MATCHAAAAA at " + i);
//						int temp_cost = this.align_rec_fast(entry.getValue(), right, i + 1, alignment, cost + i - node_right_index);
//						if (temp_cost < cur_min_cost) {
//							cur_min_cost = temp_cost;
////							if (this.cur_best_cost > cur_min_cost)
////								this.cur_best_cost = cur_min_cost;
//							alignment[i] = entry.getKey().hashCode(); // NOTE: this must be the sequence number!
//
//							// set skipped in right to -1 because the costs were cheaper here
////							for (int j = node_right_index; j < i; j++) {
////								alignment[j] = -1;
////							}
//						}
//					}
//				}
//			}
//		}

		return cur_min_cost; // NOTE: this must never be Integer.MAX_VALUE
	}

	private int align_rec(SequenceGraphNode left, OrderedNode right, int node_right_index, int[] alignment, int cost) {

		int cur_min_cost = Integer.MAX_VALUE;

		// base case: done when left and right have no remaining elements
		if (node_right_index >= right.getOrderedChildren().size() && left.getChildren().size() <= 0) {

			// update current best cost if necessary
			if (this.cur_best_cost > cost) {
				//System.out.println("FOUND BETTER SOLUTION!: DONE: " + this.cur_best_cost + " vs. " + cost);
				this.cur_best_cost = cost;
			}
			return cost;
		}

		// abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.cur_best_cost) {
			return Integer.MAX_VALUE;
		}

		// first do the match
		if (node_right_index < right.getOrderedChildren().size() && left.getChildren().size() > 0) {
			for (Entry<Node, SequenceGraphNode> entry : left.getChildren().entrySet()) {
				// compare artifacts of nodes. this is necessary because left node is already using a sequence number and right is not.
				if (entry.getKey().getArtifact().equals(right.getOrderedChildren().get(node_right_index).getArtifact())) {
					int temp_cost = this.align_rec(entry.getValue(), right, node_right_index + 1, alignment, cost);
					if (temp_cost < cur_min_cost) {
						cur_min_cost = temp_cost;
						if (this.cur_best_cost > cur_min_cost)
							this.cur_best_cost = cur_min_cost;
						//alignment[node_right_index] = entry.getKey().hashCode(); // NOTE: this must be the sequence number!
						alignment[node_right_index] = entry.getKey().getSequenceNumber();
					}
					/*
					 * TODO: for every node (seq_id) there can only be one successor per symbol (deterministic), so we can break out of the loop as soon as a
					 * match was found?
					 */
					// break;
				}
			}
		}

		if (node_right_index < right.getOrderedChildren().size()) { // skip right
			int temp_cost = this.align_rec(left, right, node_right_index + 1, alignment, cost + 1);
			if (temp_cost < cur_min_cost) {
				cur_min_cost = temp_cost;
				alignment[node_right_index] = -1;
			}
		}

		if (left.getChildren().size() > 0) { // skip left
			for (SequenceGraphNode n : left.getChildren().values()) {
				int temp_cost = this.align_rec(n, right, node_right_index, alignment, cost + 1);
				if (temp_cost < cur_min_cost) {
					cur_min_cost = temp_cost;
					// no changes to the alignment
				}
			}
		}

		return cur_min_cost; // NOTE: this must never be Integer.MAX_VALUE
	}

	// ####################### SEQUENCE GRAPH ########################

	private int nextSequenceNumber() {
		if (this.cur_seq_number + 1 < -1)
			System.out.println("WARNING: sequence number overflow!"); // TODO: use ecco exception and logger here!
		return this.cur_seq_number++;
	}

	private SequenceGraphNode root = null;

	public SequenceGraphNode getRoot() {
		return this.root;
	}

	public int getCurSeqNumber() {
		return this.cur_seq_number;
	}

	private int cur_seq_number = 1;

	private int cur_best_cost = Integer.MAX_VALUE;

	private Map<Set<Node>, SequenceGraphNode> nodes = new HashMap<Set<Node>, SequenceGraphNode>();

	private boolean pol = true;

}
