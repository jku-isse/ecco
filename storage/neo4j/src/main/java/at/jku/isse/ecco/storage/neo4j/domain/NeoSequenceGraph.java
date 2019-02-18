package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class NeoSequenceGraph extends NeoEntity implements SequenceGraph, SequenceGraph.Op {

    @Property("pol")
	private boolean pol;

    @Relationship("HAS")
	private Node.Op root;

    @Property("cur_seq_number")
	private int cur_seq_number;


	public NeoSequenceGraph() {
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
		return new NeoSequenceGraphNode(pol);
	}

}
