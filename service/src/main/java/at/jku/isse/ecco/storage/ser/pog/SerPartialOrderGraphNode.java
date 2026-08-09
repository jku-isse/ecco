package at.jku.isse.ecco.storage.ser.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;

import java.util.*;

public class SerPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	private transient Collection<PartialOrderGraph.Node.Op> previous = new ArrayList<>();
	private transient Collection<PartialOrderGraph.Node.Op> next = new ArrayList<>();

	// only used for iterative serialization in order not to overflow stack
	private Collection<Integer> previousSequenceNumbers = new ArrayList<>();
	// only used for iterative serialization in order not to overflow stack
	private Collection<Integer> nextSequenceNumbers = new ArrayList<>();

	// transient for the same reason as SerArtifact.containingNode: PartialOrderGraphs get merged
	// across nodes that can belong to different associations (Trees.slice(), Trees.java:115), so a
	// POG node's artifact can live in a foreign association's tree - a direct reference would pull
	// in that foreign object graph via a side channel on reload. artifactId is the serialized
	// surrogate; the transient field is populated by SerTransactionStrategy's post-load resolution
	// pass against the same global artifact-id index used for SerArtifact.containingNode and
	// SerArtifactReference.source/target.
	private transient Artifact.Op<?> artifact;
	private String artifactId;

	// this node's own position within its own graph - see PartialOrderGraph.Node.getSequenceNumber()
	// for why this can't just be artifact.getSequenceNumber() anymore. Serialized directly (not
	// transient): it's now the authoritative value, so prepareSerialization()/deserializeCollections()
	// below key off it directly instead of round-tripping through the artifact.
	private int sequenceNumber = PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER;

	@Override
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	@Override
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public SerPartialOrderGraphNode(Artifact.Op<?> artifact) {
//		Objects.requireNonNull(artifact);
		this.artifact = artifact;
		this.artifactId = (artifact instanceof SerArtifact<?> serArtifact) ? serArtifact.getStorageId() : null;
	}

	public String getArtifactId() {
		return this.artifactId;
	}

	/** Used only by SerTransactionStrategy's post-load resolution pass - sets the live reference without touching artifactId (already correct, just loaded from the stream). */
	public void resolveArtifact(Artifact.Op<?> artifact) {
		this.artifact = artifact;
	}

	public void init(){
		if (this.next == null){ this.next = new ArrayList<>(); }
		if (this.previous == null){ this.previous = new ArrayList<>(); }
	}

	public void prepareSerialization(){
		this.nextSequenceNumbers = new ArrayList<>();
		this.previousSequenceNumbers = new ArrayList<>();
		// fill integer collections, that will be serialized - keyed by each neighbor's own
		// node-owned sequence number now, not its artifact's (see the field javadoc above)
		this.previous.forEach(n -> {
			if (n.getArtifact() != null){
				// head and tail will be put into deserialized node in separate step
				this.previousSequenceNumbers.add(n.getSequenceNumber());
			}
		});

		this.next.forEach(n -> {
			if (n.getArtifact() != null){
				// head and tail will be put into deserialized node in separate step
				this.nextSequenceNumbers.add(n.getSequenceNumber());
			}
		});
	}

	public void deserializeCollections(Map<Integer, SerPartialOrderGraphNode> sequenceNumberNodeMap){
		if (this.next == null) { this.next = new ArrayList<>(); }
		if (this.previous == null) { this.previous = new ArrayList<>(); }
		this.nextSequenceNumbers.forEach(i -> this.next.add(sequenceNumberNodeMap.get(i)));
		this.previousSequenceNumbers.forEach(i -> this.previous.add(sequenceNumberNodeMap.get(i)));
	}

	public void addPrevious(PartialOrderGraph.Node.Op node){
		this.previous.add(node);
	}

	public void addNext(PartialOrderGraph.Node.Op node){
		this.next.add(node);
	}

	@Override
	public Collection<Op> getPrevious() {
		return this.previous;
	}

	@Override
	public Collection<Op> getNext() {
		return this.next;
	}

	@Override
	public Artifact.Op<?> getArtifact() {
		return this.artifact;
	}

	@Override
	public void setArtifact(Artifact.Op<?> artifact) {
		Objects.requireNonNull(artifact);
		this.artifact = artifact;
		this.artifactId = (artifact instanceof SerArtifact<?> serArtifact) ? serArtifact.getStorageId() : null;
	}

	@Override
	public Op addChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.add(child);
		((SerPartialOrderGraphNode) child).previous.add(this);
		return child;
	}

	@Override
	public void removeChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.remove(child);
		((SerPartialOrderGraphNode) child).previous.remove(this);
	}

	@Override
	public String toString() {
		return this.getArtifact() == null ? "NULL" : this.getArtifact().toString() + " [" + this.getSequenceNumber() + "]";
	}

	@Override
	public int hashCode(){
		return Objects.hash(this.artifact);
	}

	@Override
	public boolean equalsCompletely(Object o){
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SerPartialOrderGraphNode node = (SerPartialOrderGraphNode) o;
		// NOT this.equals(node): equals() on this class is unoverridden (identity-based), so a
		// deep/structural comparison like this one needs to compare artifacts directly instead (see
		// PartialOrderGraph.nodeOccursSameNumberOfTimes()'s comment for why equals() stays that way).
		if (!Objects.equals(this.getArtifact(), node.getArtifact())) {
			return false;
		}
		if (!PartialOrderGraph.nodeCollectionsAreEqual(this.getPrevious(), node.getPrevious())){
			return false;
		}
		if (!PartialOrderGraph.nodeCollectionsAreEqual(this.getNext(), node.getNext())){
			return false;
		}
		return true;
	}

}
