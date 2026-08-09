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
			if (serPartialOrderGraphNode.getArtifact() != null) {
				// head and tail will be serialized as field and must not be in the map. Keyed by the
				// node's own sequence number now, not its artifact's - see the field javadoc on
				// SerPartialOrderGraphNode.sequenceNumber for why the artifact's own value can't be
				// trusted to be unique-per-node anymore (it can be legitimately shared).
				Integer sequenceNumber = serPartialOrderGraphNode.getSequenceNumber();
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

		this.normalizeSequenceNumberNodeMap();
		// if next of head is empty and previous of tail is empty connect them
		if (this.sequenceNumberNodeMap.isEmpty()){
			this.wireHeadAndTail();
		}

		// fill collections of nodes using the map
		for(SerPartialOrderGraphNode node : this.sequenceNumberNodeMap.values()){
			node.deserializeCollections(this.sequenceNumberNodeMap);
		}

		SerPartialOrderGraphNode memPartialOrderGraphHead = ((SerPartialOrderGraphNode) this.head);
		memPartialOrderGraphHead.deserializeCollections(this.sequenceNumberNodeMap);
		// put head in "previous" of every item in next-collection of head
		//
		// NOTE: this used to check "n.getArtifact() != null" to distinguish a real node from the
		// tail sentinel (head can be directly linked to tail in an empty graph, via
		// wireHeadAndTail() above, and that edge is already bidirectional there - this loop must
		// not add a redundant/wrong edge for it). That worked back when artifact was an ordinary
		// (eagerly-populated-by-readObject) field, but artifact is now transient and only filled in
		// later by SerTransactionStrategy's post-load resolution pass - at this point in
		// deserialization it is always null, for every node, real or not. So the check always
		// evaluated false, meaning head was silently never added to any real node's "previous"
		// collection after a reload, corrupting checkConsistency()'s reachability count. Use
		// identity against the known sentinel instead - correct regardless of artifact resolution
		// timing.
		this.head.getNext().forEach(n -> {
			if (n != this.tail) {
				((SerPartialOrderGraphNode) n).addPrevious(this.head);
			}
		});

		SerPartialOrderGraphNode memPartialOrderGraphTail = ((SerPartialOrderGraphNode) this.tail);
		memPartialOrderGraphTail.deserializeCollections(this.sequenceNumberNodeMap);
		// put tail in "next" of every item in previous-collection of tail - see the comment above
		this.tail.getPrevious().forEach(n -> {
			if (n != this.head) {
				((SerPartialOrderGraphNode) n).addNext(this.tail);
			}
		});


	}

	/**
	 * sequenceNumberNodeMap is non-transient, so a repository serialized by a version of this class
	 * from before this field existed would deserialize it as null (Java leaves an absent field at
	 * its default) - normalize to an empty map so callers can rely on it never being null.
	 */
	private void normalizeSequenceNumberNodeMap() {
		if (this.sequenceNumberNodeMap == null) {
			this.sequenceNumberNodeMap = new HashMap<>();
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
