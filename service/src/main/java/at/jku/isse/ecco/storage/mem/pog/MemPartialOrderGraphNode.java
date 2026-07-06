package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.*;

public class MemPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	private transient Collection<PartialOrderGraph.Node.Op> previous = new ArrayList<>();
	private transient Collection<PartialOrderGraph.Node.Op> next = new ArrayList<>();

	private Artifact.Op<?> artifact;

	public MemPartialOrderGraphNode(Artifact.Op<?> artifact) {
//		Objects.requireNonNull(artifact);
		this.artifact = artifact;
	}

	public void init(){
		if (this.next == null){ this.next = new ArrayList<>(); }
		if (this.previous == null){ this.previous = new ArrayList<>(); }
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

	@Override
	public boolean equalsCompletely(Object o){
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MemPartialOrderGraphNode node = (MemPartialOrderGraphNode) o;
		if (!this.equals(node)) {
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
