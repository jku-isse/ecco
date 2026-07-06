package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		this.head.addChild(this.tail);
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


	// The next/previous links of each node are transient (see MemPartialOrderGraphNode), since a plain
	// recursive default serialization of them can overflow the stack on large graphs. Instead, all nodes
	// are collected iteratively and their links are serialized as indices into that node list.
	private void writeObject(ObjectOutputStream out) throws IOException {
		List<Node.Op> nodes = new ArrayList<>(this.collectNodes());
		Map<Node.Op, Integer> indexOf = new HashMap<>();
		for (int i = 0; i < nodes.size(); i++) {
			indexOf.put(nodes.get(i), i);
		}

		out.defaultWriteObject();

		out.writeInt(nodes.size());
		for (Node.Op node : nodes) {
			out.writeObject(node);

			int[] nextIndices = new int[node.getNext().size()];
			int i = 0;
			for (Node.Op next : node.getNext())
				nextIndices[i++] = indexOf.get(next);
			out.writeObject(nextIndices);

			int[] previousIndices = new int[node.getPrevious().size()];
			i = 0;
			for (Node.Op previous : node.getPrevious())
				previousIndices[i++] = indexOf.get(previous);
			out.writeObject(previousIndices);
		}
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		int size = in.readInt();
		List<MemPartialOrderGraphNode> nodes = new ArrayList<>(size);
		List<int[]> nextIndices = new ArrayList<>(size);
		List<int[]> previousIndices = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			nodes.add((MemPartialOrderGraphNode) in.readObject());
			nextIndices.add((int[]) in.readObject());
			previousIndices.add((int[]) in.readObject());
		}

		for (int i = 0; i < size; i++) {
			MemPartialOrderGraphNode node = nodes.get(i);
			node.init();
			for (int nextIndex : nextIndices.get(i))
				node.addNext(nodes.get(nextIndex));
			for (int previousIndex : previousIndices.get(i))
				node.addPrevious(nodes.get(previousIndex));
		}
	}

}
