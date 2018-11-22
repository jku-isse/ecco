package at.jku.isse.ecco.storage.neo4j.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sg.SequenceGraph;

public class MemSequenceGraph implements SequenceGraph, SequenceGraph.Op {

	public static final long serialVersionUID = 1L;


	private boolean pol;

	private Node.Op root;

	private int cur_seq_number;


	public MemSequenceGraph() {
		this.pol = true;
		this.root = this.createSequenceGraphNode(this.pol);
		this.cur_seq_number = SequenceGraph.INITIAL_SEQUENCE_NUMBER;
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}

	@Override
	public void setRoot(Node.Op root) {
		this.root = root;
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
			throw new EccoException("Sequence number overflow!");
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
	public Node.Op createSequenceGraphNode(boolean pol) {
		return new MemSequenceGraphNode(pol);
	}

}
