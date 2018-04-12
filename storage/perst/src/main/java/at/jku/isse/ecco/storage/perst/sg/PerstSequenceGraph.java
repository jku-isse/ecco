package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.garret.perst.Persistent;

public class PerstSequenceGraph extends Persistent implements SequenceGraph, SequenceGraph.Op {

	private boolean pol;

	private Node.Op root;

	private int cur_seq_number;


	public PerstSequenceGraph() {
		this.pol = true;
		this.root = this.createSequenceGraphNode(this.pol);
		this.cur_seq_number = SequenceGraph.INITIAL_SEQUENCE_NUMBER;
	}


	public void storeRecursively() {
		this.store();

		for (Node node : this.collectNodes()) {
			((PerstSequenceGraphNode) node).store();
		}
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}

	@Override
	public void setRoot(Node.Op root) {
		this.root = root;
	}


	public int nextSequenceNumber() throws EccoException {
		if (this.cur_seq_number + 1 < -1)
			throw new EccoException("Sequence number overflow!");
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


	public Node.Op createSequenceGraphNode(boolean pol) {
		return new PerstSequenceGraphNode(pol);
	}

}
