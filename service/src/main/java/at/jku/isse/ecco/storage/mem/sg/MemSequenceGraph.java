package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.sg.SequenceGraphOperator;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class MemSequenceGraph implements SequenceGraph, SequenceGraph.Op {

	private transient SequenceGraphOperator operator = new SequenceGraphOperator(this);


	private boolean pol;

	private Node.Op root;

	private int cur_seq_number;


	public MemSequenceGraph() {
		this.pol = true;
		this.root = this.createSequenceGraphNode(this.pol);
		this.cur_seq_number = 1;
	}


	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		this.operator = new SequenceGraphOperator(this);
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}

	@Override
	public void sequence(at.jku.isse.ecco.tree.Node.Op node) throws EccoException {
		this.operator.sequence(node);
	}

	@Override
	public void sequenceNodes(List<? extends at.jku.isse.ecco.tree.Node.Op> nodes) throws EccoException {
		this.operator.sequenceNodes(nodes);
	}

	@Override
	public void sequenceArtifacts(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
		this.operator.sequenceArtifacts(artifacts);
	}

	@Override
	public int[] align(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
		return this.operator.align(artifacts);
	}

	@Override
	public void sequence(SequenceGraph.Op other) {
		this.operator.sequence(other);
	}

	@Override
	public void updateArtifactReferences() {
		this.operator.updateArtifactReferences();
	}

	@Override
	public void copy(SequenceGraph.Op other) {
		this.operator.copy(other);
	}

	@Override
	public Collection<? extends Artifact.Op<?>> getSymbols() {
		return this.operator.collectSymbols();
	}

	@Override
	public void trim(Collection<? extends Artifact.Op<?>> symbols) {
		this.operator.trim(symbols);
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
	public int nextSequenceNumber() throws EccoException {
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
	public Node.Op createSequenceGraphNode(boolean pol) {
		return new MemSequenceGraphNode(pol);
	}

}
