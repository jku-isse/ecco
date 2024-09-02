package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class MemPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	private transient Collection<PartialOrderGraph.Node.Op> previous = new ArrayList<>();
	private transient Collection<PartialOrderGraph.Node.Op> next = new ArrayList<>();

	// only used for iterative serialization in order not to overflow stack
	private Collection<Integer> previousSequenceNumbers = new ArrayList<>();
	// only used for iterative serialization in order not to overflow stack
	private Collection<Integer> nextSequenceNumbers = new ArrayList<>();

	private Artifact.Op<?> artifact;

	public MemPartialOrderGraphNode(Artifact.Op<?> artifact) {
//		Objects.requireNonNull(artifact);
		this.artifact = artifact;
	}

	public void init(){
		if (this.next == null){ this.next = new ArrayList<>(); }
		if (this.previous == null){ this.previous = new ArrayList<>(); }
	}

	public void prepareSerialization(){
		this.nextSequenceNumbers = new ArrayList<>();
		this.previousSequenceNumbers = new ArrayList<>();
		// fill integer collections, that will be serialized
		this.previous.forEach(n -> {
			Artifact<?> artifact = n.getArtifact();
			if (artifact != null){
				// head and tail will be put into deserialized node in separate step
				this.previousSequenceNumbers.add(artifact.getSequenceNumber());
			}
		});

		this.next.forEach(n -> {
			Artifact<?> artifact = n.getArtifact();
			if (artifact != null){
				// head and tail will be put into deserialized node in separate step
				this.nextSequenceNumbers.add(artifact.getSequenceNumber());
			}
		});
	}

	public void deserializeCollections(Map<Integer, MemPartialOrderGraphNode> sequenceNumberNodeMap){
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
	}

	@Override
	public Op addChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.add(child);
		((MemPartialOrderGraphNode) child).previous.add(this);
		return child;
	}

	@Override
	public void removeChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.remove(child);
		((MemPartialOrderGraphNode) child).previous.remove(this);
	}

	@Override
	public String toString() {
		return this.getArtifact() == null ? "NULL" : this.getArtifact().toString() + " [" + this.getArtifact().getSequenceNumber() + "]";
	}

	@Override
	public int hashCode(){
		return Objects.hash(this.artifact);
	}

}
