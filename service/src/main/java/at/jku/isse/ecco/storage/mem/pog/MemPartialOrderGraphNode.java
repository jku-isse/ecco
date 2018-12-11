package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class MemPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	private Collection<PartialOrderGraph.Node.Op> previous;
	private Collection<PartialOrderGraph.Node.Op> next;

	private Artifact.Op<?> artifact;

	public MemPartialOrderGraphNode(Artifact.Op<?> artifact) {
//		Objects.requireNonNull(artifact);
		this.artifact = artifact;
		this.previous = new ArrayList<>();
		this.next = new ArrayList<>();
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

}
