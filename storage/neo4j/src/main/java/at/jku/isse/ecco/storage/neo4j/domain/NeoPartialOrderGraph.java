package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

public class NeoPartialOrderGraph extends NeoEntity implements PartialOrderGraph, PartialOrderGraph.Op {

	@Relationship
	private Node.Op head;

	@Relationship
	private Node.Op tail;

	@Property
	private int maxIdentifier = INITIAL_SEQUENCE_NUMBER;

	public NeoPartialOrderGraph() {
//		this.head = new NeoPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("HEAD")));
//		this.head.getArtifact().setSequenceNumber(HEAD_SEQUENCE_NUMBER);
//		this.tail = new NeoPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("TAIL")));
//		this.tail.getArtifact().setSequenceNumber(TAIL_SEQUENCE_NUMBER);
		this.head = new NeoPartialOrderGraphNode(null);
		this.tail = new NeoPartialOrderGraphNode(null);
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
		return new NeoPartialOrderGraphNode(artifact);
	}

	@Override
	public Op createPartialOrderGraph() {
		return new NeoPartialOrderGraph();
	}

}
