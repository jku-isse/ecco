package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SequenceGraphOperator {

	private EntityFactory entityFactory;

	private SequenceGraph.Op sequenceGraph;

	public SequenceGraphOperator(SequenceGraph.Op sequenceGraph) {
		this.sequenceGraph = sequenceGraph;
	}

	public SequenceGraphOperator(SequenceGraph.Op sequenceGraph, EntityFactory entityFactory) {
		this.sequenceGraph = sequenceGraph;
		this.entityFactory = entityFactory;
	}

	private int global_best_cost = Integer.MAX_VALUE;


	// # NEW SG OPERATIONS #################################################################


	public Collection<SequenceGraph.Node.Op> collectNodes() {
		Collection<SequenceGraph.Node.Op> nodes = new ArrayList<>();
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.collectNodesRec(this.sequenceGraph.getRoot(), nodes);
		return nodes;
	}

	private void collectNodesRec(SequenceGraph.Node.Op sgn, Collection<SequenceGraph.Node.Op> nodes) {
		if (sgn.getPol() == this.sequenceGraph.getPol()) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		nodes.add(sgn);

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : sgn.getChildren().entrySet()) {
			this.collectNodesRec(entry.getValue(), nodes);
		}
	}


	public Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> collectPathMap() {
		Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes = new HashMap<>();
		Set<Artifact.Op<?>> path = new HashSet<>();
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.collectPathMapRec(this.sequenceGraph.getRoot(), path, nodes);
		return nodes;
	}

	private void collectPathMapRec(SequenceGraph.Node.Op sgn, Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes) {
		if (sgn.getPol() == this.sequenceGraph.getPol()) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		nodes.put(path, sgn);

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : sgn.getChildren().entrySet()) {
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
	public Collection<Artifact.Op<?>> collectSymbols() {
		Set<Artifact.Op<?>> symbols = new HashSet<>();
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.collectSymbolsRec(this.sequenceGraph.getRoot(), symbols);
		return symbols;
	}

	private void collectSymbolsRec(SequenceGraph.Node.Op sgn, Collection<Artifact.Op<?>> symbols) {
		if (sgn.getPol() == this.sequenceGraph.getPol()) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : sgn.getChildren().entrySet()) {
			symbols.add(entry.getKey());

			this.collectSymbolsRec(entry.getValue(), symbols);
		}
	}


	/**
	 * Trims the sequence graph by removing all symbols that are not contained in the collection of given symbols.
	 *
	 * @param symbols Symbols to keep.
	 */
	public void trim(Collection<? extends Artifact.Op<?>> symbols) {
		SequenceGraph.Node.Op tempLeftRoot = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
		tempLeftRoot.getChildren().putAll(this.sequenceGraph.getRoot().getChildren()); // copy all children over to temporary root node
		this.sequenceGraph.getRoot().getChildren().clear(); // clear all children of real root node

		Set<Artifact.Op<?>> path = new HashSet<>();

//		this.sequenceGraph.getNodes().clear(); // clear node list of sequence graph
//		this.sequenceGraph.getNodes().put(path, this.sequenceGraph.getRoot()); // add root node back into node list
		Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> rigthNodes = new HashMap<>();
		rigthNodes.put(path, this.sequenceGraph.getRoot());

		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		this.trimRec(symbols, path, rigthNodes, tempLeftRoot, this.sequenceGraph.getRoot());
	}

	private void trimRec(Collection<? extends Artifact.Op<?>> symbols, Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> rightNodes, SequenceGraph.Node.Op left, SequenceGraph.Node.Op right) {
		if (left.getPol() == this.sequenceGraph.getPol()) // node already visited
			return;

		left.setPol(this.sequenceGraph.getPol()); // set to visited

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftEntry : left.getChildren().entrySet()) {
			if (symbols.contains(leftEntry.getKey())) { // put it into right
				Set<Artifact.Op<?>> newPath = new HashSet<>(path);
				newPath.add(leftEntry.getKey());

				SequenceGraph.Node.Op rightChild = rightNodes.get(newPath);
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

		if (!(sg instanceof SequenceGraph.Op))
			throw new EccoException("Copy requires two sequence graph operands.");
		SequenceGraph.Op other = (SequenceGraph.Op) sg;

		//other.setPol(!other.getPol());
		other.setPol(!other.getRoot().getPol());

		HashSet<Artifact.Op<?>> path = new HashSet<>();
		Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> leftNodes = new HashMap<>();
		leftNodes.put(path, this.sequenceGraph.getRoot());

		this.copyRec(path, leftNodes, this.sequenceGraph.getRoot(), other.getRoot(), other.getPol());

		this.sequenceGraph.setCurrentSequenceNumber(other.getCurrentSequenceNumber());
	}

	private void copyRec(Set<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> leftNodes, SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, boolean newPol) {
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

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightEntry : right.getChildren().entrySet()) {
			Set<Artifact.Op<?>> newPath = new HashSet<>(path);
			newPath.add(rightEntry.getKey());

			SequenceGraph.Node.Op leftNode = leftNodes.get(newPath);
			if (leftNode == null) {
				leftNode = this.sequenceGraph.createSequenceGraphNode(this.sequenceGraph.getPol());
				leftNodes.put(newPath, leftNode);
			}
			left.getChildren().put(rightEntry.getKey(), leftNode);

			this.copyRec(newPath, leftNodes, leftNode, rightEntry.getValue(), newPol);

//			SequenceGraphNode child = this.copyRec(newPath, leftNode, rightEntry.getValue(), newPol);

//			left.getChildren().put(rightEntry.getKey(), child);
		}
	}


	/**
	 * Sequences another sequence graph into this sequence graph.
	 *
	 * @param sg The other sequence graph to sequence into this one.
	 */
	public void sequence(SequenceGraph.Op sg) {
		if (!(sg instanceof SequenceGraph.Op))
			throw new EccoException("Copy requires two sequence graph operands.");
		SequenceGraph.Op other = (SequenceGraph.Op) sg;

		// set sequence number of all artifacts in right sequence graph to -1 prior to alignment to left sequence graph.
		for (Artifact.Op symbol : other.getSymbols()) {
			symbol.setSequenceNumber(-1);
		}

		// align right to left
		this.global_best_cost = Integer.MAX_VALUE;
		this.alignSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), 0);

		// assign new sequence numbers to right
		int num_symbols = this.sequenceGraph.getCurrentSequenceNumber();
		Set<Artifact.Op<?>> rightArtifacts = new HashSet<>();
		other.setPol(!other.getPol());
		this.assignNewSequenceNumbersRec(other.getRoot(), rightArtifacts, other.getPol());

		// update left
		Set<Artifact.Op<?>> shared_symbols = new HashSet<>();
		for (Artifact.Op<?> symbol : rightArtifacts) {
			if (symbol.getSequenceNumber() < num_symbols) {
				shared_symbols.add(symbol);
			}
		}
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());
		HashSet<Artifact.Op<?>> path = new HashSet<>();
		Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes = new HashMap<>();
		nodes.put(path, this.sequenceGraph.getRoot());
		this.updateSequenceGraphRec(this.sequenceGraph.getRoot(), other.getRoot(), new HashSet<>(), nodes, shared_symbols);

//		// remove all graphnodes that were not visited
//		Iterator<SequenceGraphNode> it = this.sequenceGraph.getNodes().values().iterator();
//		while (it.hasNext()) {
//			SequenceGraphNode gn = it.next();
//			if (gn.getPol() != this.sequenceGraph.getPol()) {
//				it.remove();
//			}
//		}
	}

	// TODO: should this be a util function? or a public function? because this should actually be called on the other sg not on this one!
	private void assignNewSequenceNumbersRec(SequenceGraph.Node.Op sgn, Set<Artifact.Op<?>> artifacts, boolean newPol) {
		if (sgn.getPol() == newPol) // already visited
			return;

		sgn.setPol(this.sequenceGraph.getPol()); // mark as visited

		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> child : sgn.getChildren().entrySet()) {
			if (child.getKey().getSequenceNumber() == -1) {
				child.getKey().setSequenceNumber(this.sequenceGraph.nextSequenceNumber());
			}
			artifacts.add(child.getKey()); // add symbol after it had its new sequence number assigned, otherwise there may be identical symbols.

			this.assignNewSequenceNumbersRec(child.getValue(), artifacts, newPol);
		}
	}

	// TODO: implement optimized sequence graph merge
	private int alignSequenceGraphRecFast(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, int cost) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}
		}

		// recursion case
		else {

			// case 1: do matches first
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {

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
//				for (Map.Entry<Artifact.Op<?>, SequenceGraphNode> leftChildEntry : tempLeftNode.getChildren().entrySet()) {
//					for (Map.Entry<Artifact.Op<?>, SequenceGraphNode> rightChildEntry : right.getChildren().entrySet()) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}

			// case 3: skip right
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {
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
	private int alignSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, int cost) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}
		}

		// recursion case
		else {

			// case 1: do matches first
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {

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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftChildEntry : left.getChildren().entrySet()) {
				int temp_cost = this.alignSequenceGraphRec(leftChildEntry.getValue(), right, cost + 1);

				if (temp_cost < local_best_cost)
					local_best_cost = temp_cost;
			}

			// case 3: skip right
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightChildEntry : right.getChildren().entrySet()) {
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
	private SequenceGraph.Node.Op updateSequenceGraphRec(SequenceGraph.Node.Op left, SequenceGraph.Node.Op right, HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<Artifact.Op<?>> shared_symbols) {
		// get current graph node
		SequenceGraph.Node.Op sgn = nodes.get(path);
		if (sgn == null) {
			sgn = this.sequenceGraph.createSequenceGraphNode(!this.sequenceGraph.getPol());
			nodes.put(path, sgn);
		}

		// base case: node has already been visited
		if (sgn.getPol() == this.sequenceGraph.getPol())
			return sgn;

		// set node to visited
		sgn.setPol(this.sequenceGraph.getPol());

		HashMap<Artifact.Op<?>, SequenceGraph.Node.Op> new_children = new HashMap<>();

		// if unshared symbol left -> advance
		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftEntry : left.getChildren().entrySet()) {
			if (!shared_symbols.contains(leftEntry.getKey())) {
				System.out.println("LEFT UNSHARED");
				HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
				new_path.add(leftEntry.getKey());
				SequenceGraph.Node.Op child = this.updateSequenceGraphRec(leftEntry.getValue(), right, new_path, nodes, shared_symbols);
				new_children.put(leftEntry.getKey(), child);
			}
		}

		// if unshared symbol right -> add it left and advance
		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightEntry : right.getChildren().entrySet()) {
			if (!shared_symbols.contains(rightEntry.getKey())) {
				System.out.println("RIGHT UNSHARED");
				HashSet<Artifact.Op<?>> new_path = new HashSet<>(path);
				new_path.add(rightEntry.getKey());
				SequenceGraph.Node.Op child = this.updateSequenceGraphRec(left, rightEntry.getValue(), new_path, nodes, shared_symbols); // this should be a new node
				new_children.put(rightEntry.getKey(), child);
			}
		}

		// if shared symbol -> cut it if onesided or take it when on both sides
		Iterator<Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op>> it = left.getChildren().entrySet().iterator();
		//for (Map.Entry<Artifact.Op<?>, SequenceGraphNode> leftEntry : left.getChildren().entrySet()) {
		while (it.hasNext()) {
			Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> leftEntry = it.next();
			if (shared_symbols.contains(leftEntry.getKey())) {
				Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> rightEntry = null;
				for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> tempRightEntry : right.getChildren().entrySet()) {
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
		sgn.getChildren().putAll(new_children);

		return sgn;
	}


	// # OPERATIONS #################################################################

	public void updateArtifactReferences() {
		// update node list
		for (SequenceGraph.Node.Op sgn : this.collectNodes()) {
			// update references in children
			Map<Artifact.Op<?>, SequenceGraph.Node.Op> updatedChildren = new HashMap<>();
			Iterator<Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op>> childrenIterator = sgn.getChildren().entrySet().iterator();
			while (childrenIterator.hasNext()) {
				Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> childEntry = childrenIterator.next();
				if (childEntry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
					Artifact.Op<?> replacing = childEntry.getKey().<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
					replacing.setSequenceNumber(childEntry.getKey().getSequenceNumber());

					updatedChildren.put(replacing, childEntry.getValue());
					childrenIterator.remove();
				}
			}
			sgn.getChildren().putAll(updatedChildren);
		}
	}

	private void updateArtifactReferencesRec(SequenceGraph.Node.Op sgn) {
		// update references in children
		Map<Artifact.Op<?>, SequenceGraph.Node.Op> updatedChildren = new HashMap<>();
		Iterator<Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op>> it = sgn.getChildren().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry = it.next();
			if (entry.getKey().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
				Artifact.Op<?> replacing = entry.getKey().<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
				replacing.setSequenceNumber(entry.getKey().getSequenceNumber());

				updatedChildren.put(replacing, entry.getValue());
				it.remove();
			}
		}
		sgn.getChildren().putAll(updatedChildren);

		// traverse children
		for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> child : sgn.getChildren().entrySet()) {
			this.updateArtifactReferencesRec(child.getValue());
		}
	}


	public void sequence(at.jku.isse.ecco.tree.Node.Op node) throws EccoException {
		if (node.getArtifact().isOrdered())
			sequenceNodes(node.getChildren());
		else
			throw new EccoException("Only ordered nodes can be sequenced.");
	}

	public void sequenceNodes(List<? extends at.jku.isse.ecco.tree.Node.Op> nodes) throws EccoException {
		List<Artifact.Op<?>> artifacts = nodes.stream().map((at.jku.isse.ecco.tree.Node.Op n) -> n.getArtifact()).collect(Collectors.toList());
		sequenceArtifacts(artifacts);
	}

	public void sequenceArtifacts(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
		int num_symbols = this.sequenceGraph.getCurrentSequenceNumber();
		int[] alignment = align(artifacts);

		//if (num_symbols != this.sequenceGraph.getCurrentSequenceNumber()) {
		Set<Artifact.Op<?>> shared_symbols = new HashSet<>();
		for (Artifact.Op<?> symbol : artifacts) {
			if (symbol.getSequenceNumber() < num_symbols)
				shared_symbols.add(symbol);
		}


		update_rec(new HashSet<Artifact.Op<?>>(), this.collectPathMap(), shared_symbols, this.sequenceGraph.getRoot(), 0, artifacts, !this.sequenceGraph.getPol());
		this.sequenceGraph.setPol(!this.sequenceGraph.getPol());

//		// remove all graphnodes that were not visited
//		Iterator<SequenceGraphNode> it = this.sequenceGraph.getNodes().values().iterator();
//		while (it.hasNext()) {
//			SequenceGraphNode gn = it.next();
//			if (gn.getPol() != this.sequenceGraph.getPol()) {
//				it.remove();
//			}
//		}
		//}
	}


	public int[] align(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
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


	private int align_rec_fast(SequenceGraph.Node.Op left, List<? extends Artifact.Op<?>> artifacts, int node_right_index, int[] alignment, int cost) {

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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : left.getChildren().entrySet()) {
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
				for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : left.getChildren().entrySet()) {
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
			for (Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry : left.getChildren().entrySet()) {
				int temp_cost = align_rec_fast(entry.getValue(), artifacts, node_right_index, alignment, cost + 1);
				if (temp_cost < cur_min_cost) {
					cur_min_cost = temp_cost;
					// no changes to the alignment
				}
			}
		}

		return cur_min_cost; // NOTE: this must never be Integer.MAX_VALUE
	}

	private SequenceGraph.Node.Op update_rec(HashSet<Artifact.Op<?>> path, Map<Set<Artifact.Op<?>>, SequenceGraph.Node.Op> nodes, Set<? extends Artifact.Op<?>> shared_symbols, SequenceGraph.Node.Op node, int alignment_index, List<? extends Artifact.Op<?>> aligned_nodes, boolean new_pol) {

		// get current graph node
		boolean new_node = false;
		SequenceGraph.Node.Op gn = nodes.get(path);
		if (gn == null) {
			//gn = new SequenceGraphNode(!new_pol);
			gn = (SequenceGraph.Node.Op) this.sequenceGraph.createSequenceGraphNode(!new_pol);
			nodes.put(path, gn);
			new_node = true;
		}

		// base case: node has already been visited
		if (gn.getPol() == new_pol)
			return gn;

		// set node to visited
		gn.setPol(new_pol);

		// determine all possible successor paths
		HashMap<Artifact.Op<?>, SequenceGraph.Node.Op> new_children = new HashMap<>();
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
			SequenceGraph.Node.Op new_gn = update_rec(new_path, nodes, shared_symbols, node, alignment_index + 1, aligned_nodes, new_pol);
			new_children.put(right, new_gn);
		}

		// gn.children.putAll(new_children); // NOTE: can this cause a concurrent modification exception?

		// for every left child (this is the cutting part)
		Iterator<Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op>> it = node.getChildren().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry = it.next();
			// if left child unshared we can take it
			if (!shared_symbols.contains(entry.getKey())) {
				// compute new path
				@SuppressWarnings("unchecked")
				HashSet<Artifact.Op<?>> new_path = (HashSet<Artifact.Op<?>>) path.clone();
				new_path.add(entry.getKey());
				// take it
				SequenceGraph.Node.Op new_gn = update_rec(new_path, nodes, shared_symbols, entry.getValue(), alignment_index, aligned_nodes, new_pol);
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
					SequenceGraph.Node.Op new_gn = update_rec(new_path, nodes, shared_symbols, entry.getValue(), alignment_index + 1, aligned_nodes, new_pol);
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

}
