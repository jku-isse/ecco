package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

public class MemPartialOrderGraph implements PartialOrderGraph, PartialOrderGraph.Op {

	public static final long serialVersionUID = 1L;

	private Node.Op head;
	private Node.Op tail;
	private int maxIdentifier = INITIAL_SEQUENCE_NUMBER;

	public MemPartialOrderGraph() {
//		this.head = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("HEAD")));
//		this.head.getArtifact().setSequenceNumber(HEAD_SEQUENCE_NUMBER);
//		this.tail = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("TAIL")));
//		this.tail.getArtifact().setSequenceNumber(TAIL_SEQUENCE_NUMBER);
		this.head = new MemPartialOrderGraphNode(null);
		this.tail = new MemPartialOrderGraphNode(null);
//		this.head.addChild(this.tail);
	}

	@Override
	public Node.Op getHead() {
		return this.head;
	}

	@Override
	public Node.Op getTail() {
		return this.tail;
	}

	@Override
	public int getMaxIdentifier() {
		return this.maxIdentifier;
	}

	@Override
	public void setMaxIdentifier(int value) {
		this.maxIdentifier = value;
	}

	@Override
	public void incMaxIdentifier() {
		this.maxIdentifier++;
	}

	@Override
	public Node.Op createNode(Artifact.Op<?> artifact) {
		return new MemPartialOrderGraphNode(artifact);
	}

	@Override
	public Op createPartialOrderGraph() {
		return new MemPartialOrderGraph();
	}

}
