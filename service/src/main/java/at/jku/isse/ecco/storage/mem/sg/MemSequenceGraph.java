package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sg.SequenceGraph;

public class MemSequenceGraph implements SequenceGraph, SequenceGraph.Op {

	private boolean pol;

	private Node.Op root;

	private int cur_seq_number;

	private int global_best_cost;


	public MemSequenceGraph() {
		this.pol = true;
		this.root = this.createSequenceGraphNode(this.pol);
		this.cur_seq_number = 1;
		this.global_best_cost = Integer.MAX_VALUE;
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}


	@Override
	public int getCurrentSequenceNumber() {
		return this.cur_seq_number;
	}

	@Override
	public void setCurrentSequenceNumber(int sn) {
		this.cur_seq_number = sn;
	}

	@Override
	public int nextSequenceNumber() {
		if (this.cur_seq_number + 1 < -1)
			throw new EccoException("WARNING: sequence number overflow!");
		return this.cur_seq_number++;
	}


	@Override
	public boolean getPol() {
		return this.pol;
	}

	@Override
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


	@Override
	public Node.Op createSequenceGraphNode(boolean pol) {
		return new MemSequenceGraphNode(pol);
	}

}
