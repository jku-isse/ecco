package at.jku.isse.ecco.storage.ser.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

public class SerPartialOrderGraph implements PartialOrderGraph, PartialOrderGraph.Op {

	public static final long serialVersionUID = 1L;

	private Node.Op head;
	private Node.Op tail;
	private int maxIdentifier = INITIAL_SEQUENCE_NUMBER;

	// used to iteratively serialize in order not to overflow stack
	private Map<Integer, SerPartialOrderGraphNode> sequenceNumberNodeMap;

	public SerPartialOrderGraph() {
//		this.head = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("HEAD")));
//		this.head.getArtifact().setSequenceNumber(HEAD_SEQUENCE_NUMBER);
//		this.tail = new MemPartialOrderGraphNode(new MemArtifact<StringArtifactData>(new StringArtifactData("TAIL")));
//		this.tail.getArtifact().setSequenceNumber(TAIL_SEQUENCE_NUMBER);
		this.head = new SerPartialOrderGraphNode(null);
		this.tail = new SerPartialOrderGraphNode(null);
		this.head.addChild(this.tail);
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		this.sequenceNumberNodeMap = new HashMap<>();
		List<Node.Op> nodes = this.collectNodes();
		if (nodes.size() > 2){
			System.out.print("");
		}
		for(Node.Op node : nodes){
			SerPartialOrderGraphNode serPartialOrderGraphNode = (SerPartialOrderGraphNode) node;
			Artifact<?> artifact = serPartialOrderGraphNode.getArtifact();
			if (artifact != null) {
				// head and tail will be serialized as field and must not be in the map
				Integer sequenceNumber = artifact.getSequenceNumber();
				if (this.sequenceNumberNodeMap.containsKey(sequenceNumber)) {
					throw new RuntimeException("Multiple occurences of the same sequence number!");
				}
				this.sequenceNumberNodeMap.put(sequenceNumber, serPartialOrderGraphNode);
			}
			serPartialOrderGraphNode.prepareSerialization();
		}
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		if (this.head == null) { this.head = new SerPartialOrderGraphNode(null); }
		if (this.tail == null) { this.tail = new SerPartialOrderGraphNode(null); }
		((SerPartialOrderGraphNode)this.head).init();
		((SerPartialOrderGraphNode)this.tail).init();

		// if next of head is empty and previous of tail is empty connect them
		if (this.sequenceNumberNodeMap == null || this.sequenceNumberNodeMap.size() == 0){
			this.wireHeadAndTail();
		}

		// fill collections of nodes using the map
		for(SerPartialOrderGraphNode node : this.sequenceNumberNodeMap.values()){
			node.deserializeCollections(this.sequenceNumberNodeMap);
		}

		SerPartialOrderGraphNode memPartialOrderGraphHead = ((SerPartialOrderGraphNode) this.head);
		memPartialOrderGraphHead.deserializeCollections(this.sequenceNumberNodeMap);
		// put head in "previous" of every item in next-collection of head
		this.head.getNext().forEach(n -> {
			if (n.getArtifact() != null) {
				((SerPartialOrderGraphNode) n).addPrevious(this.head);
			}
		});

		SerPartialOrderGraphNode memPartialOrderGraphTail = ((SerPartialOrderGraphNode) this.tail);
		memPartialOrderGraphTail.deserializeCollections(this.sequenceNumberNodeMap);
		// put tail in "next" of every item in previous-collection of tail
		this.tail.getPrevious().forEach(n -> {
			if (n.getArtifact() != null) {
				((SerPartialOrderGraphNode) n).addNext(this.tail);
			}
		});


		List<Node.Op> nodes = this.collectNodes();
		if (nodes.size() > 2){
			System.out.print("");
		}
	}

	private void wireHeadAndTail(){
		((SerPartialOrderGraphNode) this.head).addNext(this.tail);
		((SerPartialOrderGraphNode) this.tail).addPrevious(this.head);
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
		return new SerPartialOrderGraphNode(artifact);
	}

	@Override
	public Op createPartialOrderGraph() {
		return new SerPartialOrderGraph();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SerPartialOrderGraph serPartialOrderGraph = (SerPartialOrderGraph) o;
		List<Node.Op> thisNodes = this.collectNodes();
		List<Node.Op> otherNodes = serPartialOrderGraph.collectNodes();
		return PartialOrderGraph.nodeCollectionsAreCompletelyEqual(thisNodes, otherNodes);
	}
}
