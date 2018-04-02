package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.garret.perst.Persistent;

public class PerstSequenceGraph extends Persistent implements SequenceGraph, SequenceGraph.Op {

	private boolean pol;

	private Node.Op root;

	private int cur_seq_number;

	private int global_best_cost;


	public PerstSequenceGraph() {
		this.pol = true;
		this.root = this.createSequenceGraphNode(this.pol);
		this.cur_seq_number = 1;
		this.global_best_cost = Integer.MAX_VALUE;
	}


	public void storeRecursively() {
		this.store();

//		Set<SequenceGraphNode> nodes = new HashSet<>();
//		this.collectNodes(this.getRoot(), nodes);

//		// store all nodes
//		for (SequenceGraphNode node : this.nodes.values()) {
//			//if (node instanceof PerstSequenceGraphNode)
//			((PerstSequenceGraphNode) node).store();
//		}

		for (Node node : this.collectNodes()) {
			((PerstSequenceGraphNode) node).store();
		}
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}


	public int nextSequenceNumber() throws EccoException {
		if (this.cur_seq_number + 1 < -1)
			throw new EccoException("WARNING: sequence number overflow!");
		return this.cur_seq_number++;
	}


	public int getCurrentSequenceNumber() {
		return this.cur_seq_number;
	}

	@Override
	public void setCurrentSequenceNumber(int sn) {
		this.cur_seq_number = sn;
	}


	public boolean getPol() {
		return this.pol;
	}

	public void setPol(boolean pol) {
		this.pol = pol;
	}


	@Override
	public int getGlobalBestCost() {
		return this.global_best_cost;
	}

	@Override
	public void setGlobalBestCost(int cost) {
		this.global_best_cost = cost;
	}


	public Node.Op createSequenceGraphNode(boolean pol) {
		return new PerstSequenceGraphNode(pol);
	}

}
