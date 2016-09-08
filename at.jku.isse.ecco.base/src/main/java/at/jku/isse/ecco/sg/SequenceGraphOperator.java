package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.util.*;
import java.util.stream.Collectors;

public class SequenceGraphOperator {

	private EntityFactory entityFactory;

	private SequenceGraphOperand sequenceGraph;

	public SequenceGraphOperator(SequenceGraphOperand sequenceGraph) {
		this.sequenceGraph = sequenceGraph;
	}

	public SequenceGraphOperator(SequenceGraphOperand sequenceGraph, EntityFactory entityFactory) {
		this.sequenceGraph = sequenceGraph;
		this.entityFactory = entityFactory;
	}

	private int global_best_cost = Integer.MAX_VALUE;


	// # NEW SG OPERATIONS #################################################################


	public Collection<Artifact<?>> collectSymbols() {
		Set<Artifact<?>> symbols = new HashSet<>();
		this.collectSymbolsRec(this.sequenceGraph.getRoot(), symbols);
		return symbols;
	}

	private void collectSymbolsRec(SequenceGraphNode sgn, Collection<Artifact<?>> symbols) {
		if (sgn.getPol() == this.sequenceGraph.getPol()) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		for (Map.Entry<Artifact<?>, SequenceGraphNode> entry : sgn.getChildren().entrySet()) {
			symbols.add(entry.getKey());

			this.collectSymbolsRec(entry.getValue(), symbols);
		}
	}


	/**
	 * Trims the sequence graph by removing all symbols that are not contained in the collection of given symbols.
	 *
	 * @param symbols Symbols to keep.
	 */
	public void trim(Collection<Artifact<?>> symbols) {
		SequenceGraphNode tempLeftRoot = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
		tempLeftRoot.getChildren().putAll(this.sequenceGraph.getRoot().getChildren()); // copy all children over to temporary root node
		this.sequenceGraph.getRoot().getChildren().clear(); // clear all children of real root node

		Set<Artifact<?>> path = new HashSet<>();

		this.sequenceGraph.getNodes().clear(); // clear node list of sequence graph
		this.sequenceGraph.getNodes().put(path, this.sequenceGraph.getRoot()); // add root node back into node list

		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.trimRec(symbols, path, this.sequenceGraph.getNodes(), tempLeftRoot, this.sequenceGraph.getRoot());
	}

	private void trimRec(Collection<Artifact<?>> symbols, Set<Artifact<?>> path, Map<Set<Artifact<?>>, SequenceGraphNode> rightNodes, SequenceGraphNode left, SequenceGraphNode right) {
		if (left.getPol() == this.sequenceGraph.getPol()) // node already visited
			return;

		left.setPol(this.sequenceGraph.getPol()); // set to visited

		for (Map.Entry<Artifact<?>, SequenceGraphNode> leftEntry : left.getChildren().entrySet()) {
			if (symbols.contains(leftEntry.getKey())) { // put it into right
				Set<Artifact<?>> newPath = new HashSet<>(path);
				newPath.add(leftEntry.getKey());

				SequenceGraphNode rightChild = rightNodes.get(newPath);
				if (rightChild == null) {
					rightChild = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
					rightNodes.put(newPath, rightChild);
				}
				right.getChildren().put(leftEntry.getKey(), rightChild);

				this.trimRec(symbols, newPath, rightNodes, leftEntry.getValue(), rightChild);
			} else {
				this.trimRec(symbols, path, rightNodes, leftEntry.getValue(), right);
			}
		}
	}


	/**
	 * Creates a copy of the sequence graph. Uses the same artifacts.
	 */
	public void copy(SequenceGraph sg) {
		if (!this.sequenceGraph.getRoot().getChildren().isEmpty())
			throw new EccoException("Sequence graph must be empty to copy another.");

		if (!(sg instanceof SequenceGraphOperand))
			throw new EccoException("Copy requires two sequence graph operands.");
		SequenceGraphOperand other = (SequenceGraphOperand) sg;

		other.setPol(!other.getPol());
		//this.sequenceGraph.getNodes().clear();
		this.copyRec(new HashSet<Artifact<?>>(), this.sequenceGraph.getRoot(), other.getRoot(), other.getPol());

		this.sequenceGraph.setCurrentSequenceNumber(other.getCurrentSequenceNumber());
	}

	private void copyRec(Set<Artifact<?>> path, SequenceGraphNode left, SequenceGraphNode right, boolean newPol) {
//		SequenceGraphNode leftNode = this.sequenceGraph.getNodes().get(path);
//		if (leftNode == null) {
//			leftNode = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
//			this.sequenceGraph.getNodes().put(path, leftNode);
//
//			if (right.getPol() == newPol)
//				throw new EccoException("Right node has already been visited yet left one does not exist.");
//		}
//
//		if (right.getPol() == newPol) // already visited
//			return leftNode;

		if (right.getPol() == newPol) // already visited
			return;

		right.setPol(this.sequenceGraph.getPol()); // mark as visited

		for (Map.Entry<Artifact<?>, SequenceGraphNode> rightEntry : right.getChildren().entrySet()) {
			Set<Artifact<?>> newPath = new HashSet<>(path);
			newPath.add(rightEntry.getKey());

			SequenceGraphNode leftNode = this.sequenceGraph.getNodes().get(newPath);
			if (leftNode == null) {
				leftNode = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
				this.sequenceGraph.getNodes().put(newPath, leftNode);
			}
			left.getChildren().put(rightEntry.getKey(), leftNode);

			this.copyRec(newPath, leftNode, rightEntry.getValue(), newPol);

//			SequenceGraphNode child = this.copyRec(newPath, leftNode, rightEntry.getValue(), newPol);

//			left.getChildren().put(rightEntry.getKey(), child);
		}
	}


	/**
	 * Sequences another sequence graph into this sequence graph.
	 *
	 * @param sg The other sequence graph to sequence into this one.
	 */
	public void sequence(SequenceGraph sg) {
		if (!(sg instanceof SequenceGraphOperand))
			throw new EccoException("Copy requires two sequence graph operands.");
		SequenceGraphOperand other = (SequenceGraphOperand) sg;

		// set sequence number of all artifacts in right sequence graph to -1 prior to alignment to left sequence graph.
		for (Artifact symbol : other.getSymbols()) {
			symbol.setSequenceNumber(-1);
		}

		// align right to left
		this.global_best_cost = Integer.MAX_VALUE;
		this.alignSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), 0);

		// assign new sequence numbers to right
		int num_symbols = this.sequenceGraph.getCurrentSequenceNumber();
		Set<Artifact<?>> rightArtifacts = new HashSet<>();
		other.setPol(!other.getPol());
		this.assignNewSequenceNumbersRec(other.getRoot(), rightArtifacts, other.getPol());

		// update left
		Set<Artifact<?>> shared_symbols = new HashSet<>();
		for (Artifact symbol : rightArtifacts) {
			if (symbol.getSequenceNumber() < num_symbols) {
				shared_symbols.add(symbol);
				System.out.println("SHARED: " + symbol.getSequenceNumber());
			}
		}
		System.out.println("AAA: " + this.sequenceGraph.getNodes().size());
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.updateSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), new HashSet<Artifact<?>>(), shared_symbols);
		System.out.println("BBB: " + this.sequenceGraph.getNodes().size());

		// remove all graphnodes that were not visited
		Iterator<SequenceGraphNode> it = this.sequenceGraph.getNodes().values().iterator();
		while (it.hasNext()) {
			SequenceGraphNode gn = it.next();
			if (gn.getPol() != this.sequenceGraph.getPol()) {
				it.remove();
			}
		}
	}

	// TODO: should this be a util function? or a public function? because this should actually be called on the other sg not on this one!
	private void assignNewSequenceNumbersRec(SequenceGraphNode sgn, Set<Artifact<?>> artifacts, boolean newPol) {
		if (sgn.getPol() == newPol) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		for (Map.Entry<Artifact<?>, SequenceGraphNode> child : sgn.getChildren().entrySet()) {
			if (child.getKey().getSequenceNumber() == -1) {
				child.getKey().setSequenceNumber(this.sequenceGraph.nextSequenceNumber());
			}
			artifacts.add(child.getKey()); // add symbol after it had its new sequence number assigned, otherwise there may be identical symbols.

			this.assignNewSequenceNumbersRec(child.getValue(), artifacts, newPol);
		}
	}

	// TODO: implement optimized sequence graph merge
	private int alignSequenceGraphRecFast(SequenceGraphNode left, SequenceGraphNode right, int cost) {
		//int local_best_cost = this.global_best_cost;
		int local_best_cost = Integer.MAX_VALUE;

		// base case 1: abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.global_best_cost) {
			return Integer.MAX_VALUE;
		}

		// base case 2: both sequence graphs have no more nodes
		else if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
			// update current best cost (we would not have gotten here if cost > global_best_cost)
			this.global_best_cost = cost;
			return cost;
		}

		// recursion case 1: left has no more nodes, right does
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

		// recursion case 2: right has no more nodes, left does
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

					if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) { // we found a match -> pursue it
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

//			// find match by skipping left
//			SequenceGraphNode tempLeftNode = left.getChildren().values().iterator().next();
//			boolean found_match = false;
//			while (tempLeftNode.getChildren().size() > 0) {
//				for (Map.Entry<Artifact<?>, SequenceGraphNode> leftChildEntry : tempLeftNode.getChildren().entrySet()) {
//					for (Map.Entry<Artifact<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {
//						if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) {
//							found_match = true;
//							// TODO
//							break;
//						}
//					}
//					if (found_match)
//						break;
//				}
//				if (!found_match) {
//					tempLeftNode = left.getChildren().values().iterator().next(); // just take first left child
//				} else {
//
//				}
//			}

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
	 * This aligns the right sequence graph to the left sequence graph, assigning sequence numbers to the artifacts of the right sequence graph.
	 * <p>
	 * NOTE: Changing the sequence number of artifacts changes their hashCode() and equals()! This makes the right sequence graph's children maps inconsistent!
	 * In other words: in its current state this method destroys the right sequence graph!
	 */
	private int alignSequenceGraphRec(SequenceGraphNode left, SequenceGraphNode right, int cost) {
		int local_best_cost = Integer.MAX_VALUE;

		// base case 1: abort and don't update alignment if we already had a better or equal solution.
		if (cost >= this.global_best_cost) {
			return Integer.MAX_VALUE;
		}

		// base case 2: both sequence graphs have no more nodes
		else if (left.getChildren().isEmpty() && right.getChildren().isEmpty()) {
			// update current best cost (we would not have gotten here if cost > global_best_cost)
			this.global_best_cost = cost;
			return cost;
		}

		// recursion case 1: left has no more nodes, right does
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

		// recursion case 2: right has no more nodes, left does
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

					if (leftChildEntry.getKey().equals(rightChildEntry.getKey())) { // we found a match -> pursue it
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
	private SequenceGraphNode updateSequenceGraphRec(SequenceGraphNode left, SequenceGraphNode right, HashSet<Artifact<?>> path, Set<Artifact<?>> shared_symbols) {
		// get current graph node
		SequenceGraphNode sgn = this.sequenceGraph.getNodes().get(path);
		if (sgn == null) {
			sgn = this.sequenceGraph.createSequenceGraphNode(!this.sequenceGraph.getPol());
			this.sequenceGraph.getNodes().put(path, sgn);
		}

		// base case: node has already been visited
		if (sgn.getPol() == this.sequenceGraph.getPol())
			return sgn;

		// set node to visited
		sgn.setPol(this.sequenceGraph.getPol());

		HashMap<Artifact<?>, SequenceGraphNode> new_children = new HashMap<>();

		// if unshared symbol left -> advance
		for (Map.Entry<Artifact<?>, SequenceGraphNode> leftEntry : left.getChildren().entrySet()) {
			if (!shared_symbols.contains(leftEntry.getKey())) {
				System.out.println("LEFT UNSHARED");
				HashSet<Artifact<?>> new_path = new HashSet<>(path);
				new_path.add(leftEntry.getKey());
				SequenceGraphNode child = this.updateSequenceGraphRec(leftEntry.getValue(), right, new_path, shared_symbols);
				new_children.put(leftEntry.getKey(), child);
			}
		}

		// if unshared symbol right -> add it left and advance
		for (Map.Entry<Artifact<?>, SequenceGraphNode> rightEntry : right.getChildren().entrySet()) {
			if (!shared_symbols.contains(rightEntry.getKey())) {
				System.out.println("RIGHT UNSHARED");
				HashSet<Artifact<?>> new_path = new HashSet<>(path);
				new_path.add(rightEntry.getKey());
				SequenceGraphNode child = this.updateSequenceGraphRec(left, rightEntry.getValue(), new_path, shared_symbols); // this should be a new node
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
					System.out.println("MATCH SHARED");
					HashSet<Artifact<?>> new_path = new HashSet<>(path);
					new_path.add(leftEntry.getKey());
					SequenceGraphNode child = this.updateSequenceGraphRec(leftEntry.getValue(), rightEntry.getValue(), new_path, shared_symbols);
					new_children.put(leftEntry.getKey(), child);
				} else { // no match for shared symbol -> cut graph
					System.out.println("MATCH CUT");
					it.remove();
				}
			}
		}

		sgn.getChildren().clear();
		sgn.getChildren().putAll(new_children);

		return sgn;
	}


	// # OPERATIONS #################################################################

	public void updateArtifactReferences() {
//		// update graph
//		this.updateArtifactReferencesRec(this.sequenceGraph.getRoot());

		// update node list
		Iterator<Map.Entry<Set<Artifact<?>>, SequenceGraphNode>> it = this.sequenceGraph.getNodes().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Set<Artifact<?>>, SequenceGraphNode> entry = it.next();
			Set<Artifact<?>> artifacts = entry.getKey();
			SequenceGraphNode sgn = entry.getValue();

			// update references in artifacts path
			Set<Artifact<?>> updatedArtifacts = new HashSet<>();
			Iterator<Artifact<?>> artifactsIterator = artifacts.iterator();
			while (artifactsIterator.hasNext()) {
				Artifact<?> artifact = artifactsIterator.next();

				if (artifact.getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact<?> replacing = artifact.<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					replacing.setSequenceNumber(artifact.getSequenceNumber());

					updatedArtifacts.add(replacing);
					artifactsIterator.remove();
				}
			}
			artifacts.addAll(updatedArtifacts);

			// update references in children
			Map<Artifact<?>, SequenceGraphNode> updatedChildren = new HashMap<>();
			Iterator<Map.Entry<Artifact<?>, SequenceGraphNode>> childrenIterator = sgn.getChildren().entrySet().iterator();
			while (childrenIterator.hasNext()) {
				Map.Entry<Artifact<?>, SequenceGraphNode> childEntry = childrenIterator.next();
				if (childEntry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact<?> replacing = childEntry.getKey().<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					replacing.setSequenceNumber(childEntry.getKey().getSequenceNumber());

					updatedChildren.put(replacing, childEntry.getValue());
					childrenIterator.remove();
				}
			}
			sgn.getChildren().putAll(updatedChildren);
		}
	}

//	private void updateArtifactReferencesRec(SequenceGraphNode sgn) {
//		// update references in children
//		Map<Artifact<?>, SequenceGraphNode> updatedChildren = new HashMap<>();
//		Iterator<Map.Entry<Artifact<?>, SequenceGraphNode>> it = sgn.getChildren().entrySet().iterator();
//		while (it.hasNext()) {
//			Map.Entry<Artifact<?>, SequenceGraphNode> entry = it.next();
//			if (entry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
//				Artifact<?> replacing = entry.getKey().<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
//				replacing.setSequenceNumber(entry.getKey().getSequenceNumber());
//
//				updatedChildren.put(replacing, entry.getValue());
//				it.remove();
//			}
//		}
//		sgn.getChildren().putAll(updatedChildren);
//
//		// traverse children
//		for (Map.Entry<Artifact<?>, SequenceGraphNode> child : sgn.getChildren().entrySet()) {
//			this.updateArtifactReferencesRec(child.getValue());
//		}
//	}


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
		public Map<Set<Artifact<?>>, SequenceGraphNode> getNodes(); // TODO: this may be unneeded!


		public int getCurrentSequenceNumber();

		public void setCurrentSequenceNumber(int sn);

		public int nextSequenceNumber() throws EccoException;


		public boolean getPol();

		public void setPol(boolean pol);


		public SequenceGraphNode createSequenceGraphNode(boolean pol);
	}

}
