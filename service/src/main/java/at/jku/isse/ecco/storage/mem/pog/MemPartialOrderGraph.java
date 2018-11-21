package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemPartialOrderGraph implements PartialOrderGraph, PartialOrderGraph.Op {

	public static final long serialVersionUID = 1L;

	private Node.Op head;
	private Node.Op tail;
	private List<Node.Op> nodes;

	public MemPartialOrderGraph() {
		this.head = new MemPartialOrderGraphNode();
		this.tail = new MemPartialOrderGraphNode();
		this.nodes = new ArrayList<>();
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
	public List<Node.Op> getNodes() {
		return Collections.unmodifiableList(this.nodes);
	}

	@Override
	public Node.Op createNode(Artifact.Op<?> artifact) {
		Node.Op node = new MemPartialOrderGraphNode(artifact);
		this.nodes.add(node);
		return node;
	}

	@Override
	public Op createPartialOrderGraph() {
		return new MemPartialOrderGraph();
	}

}
