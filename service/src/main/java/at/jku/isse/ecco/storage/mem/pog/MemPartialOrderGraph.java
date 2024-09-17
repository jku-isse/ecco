package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

public class MemPartialOrderGraph implements PartialOrderGraph, PartialOrderGraph.Op {

	public static final long serialVersionUID = 1L;

	private Node.Op head;
	private Node.Op tail;
	private int maxIdentifier = INITIAL_SEQUENCE_NUMBER;

	// used to iteratively serialize in order not to overflow stack
	private Map<Integer, MemPartialOrderGraphNode> sequenceNumberNodeMap;

	public MemPartialOrderGraph() {
//		this.head = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("HEAD")));
//		this.head.getArtifact().setSequenceNumber(HEAD_SEQUENCE_NUMBER);
//		this.tail = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("TAIL")));
//		this.tail.getArtifact().setSequenceNumber(TAIL_SEQUENCE_NUMBER);
		this.head = new MemPartialOrderGraphNode(null);
		this.tail = new MemPartialOrderGraphNode(null);
		this.head.addChild(this.tail);
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		this.sequenceNumberNodeMap = new HashMap<>();
		List<Node.Op> nodes = this.collectNodes();
		if (nodes.size() > 2){
			System.out.print("");
		}
		for(Node.Op node : nodes){
			MemPartialOrderGraphNode memPartialOrderGraphNode = (MemPartialOrderGraphNode) node;
			Artifact<?> artifact = memPartialOrderGraphNode.getArtifact();
			if (artifact != null) {
				// head and tail will be serialized as field and must not be in the map
				Integer sequenceNumber = artifact.getSequenceNumber();
				if (this.sequenceNumberNodeMap.containsKey(sequenceNumber)) {
					throw new RuntimeException("Multiple occurences of the same sequence number!");
				}
				this.sequenceNumberNodeMap.put(sequenceNumber, memPartialOrderGraphNode);
			}
			memPartialOrderGraphNode.prepareSerialization();
		}
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		if (this.head == null) { this.head = new MemPartialOrderGraphNode(null); }
		if (this.tail == null) { this.tail = new MemPartialOrderGraphNode(null); }
		((MemPartialOrderGraphNode)this.head).init();
		((MemPartialOrderGraphNode)this.tail).init();

		// if next of head is empty and previous of tail is empty connect them
		if (this.sequenceNumberNodeMap == null || this.sequenceNumberNodeMap.size() == 0){
			this.wireHeadAndTail();
		}

		// fill collections of nodes using the map
		for(MemPartialOrderGraphNode node : this.sequenceNumberNodeMap.values()){
			node.deserializeCollections(this.sequenceNumberNodeMap);
		}

		MemPartialOrderGraphNode memPartialOrderGraphHead = ((MemPartialOrderGraphNode) this.head);
		memPartialOrderGraphHead.deserializeCollections(this.sequenceNumberNodeMap);
		// put head in "previous" of every item in next-collection of head
		this.head.getNext().forEach(n -> {
			if (n.getArtifact() != null) {
				((MemPartialOrderGraphNode) n).addPrevious(this.head);
			}
		});

		MemPartialOrderGraphNode memPartialOrderGraphTail = ((MemPartialOrderGraphNode) this.tail);
		memPartialOrderGraphTail.deserializeCollections(this.sequenceNumberNodeMap);
		// put tail in "next" of every item in previous-collection of tail
		this.tail.getPrevious().forEach(n -> {
			if (n.getArtifact() != null) {
				((MemPartialOrderGraphNode) n).addNext(this.tail);
			}
		});


		List<Node.Op> nodes = this.collectNodes();
		if (nodes.size() > 2){
			System.out.print("");
		}
	}

	private void wireHeadAndTail(){
		((MemPartialOrderGraphNode) this.head).addNext(this.tail);
		((MemPartialOrderGraphNode) this.tail).addPrevious(this.head);
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MemPartialOrderGraph memPartialOrderGraph = (MemPartialOrderGraph) o;
		List<Node.Op> thisNodes = this.collectNodes();
		List<Node.Op> otherNodes = memPartialOrderGraph.collectNodes();
		return PartialOrderGraph.nodeCollectionsAreCompletelyEqual(thisNodes, otherNodes);
	}
}
