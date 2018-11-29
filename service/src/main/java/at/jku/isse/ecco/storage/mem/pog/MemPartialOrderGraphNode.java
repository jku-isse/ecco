package at.jku.isse.ecco.storage.mem.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.ArrayList;
import java.util.Collection;

public class MemPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	private Collection<PartialOrderGraph.Node.Op> parents;
	private Collection<PartialOrderGraph.Node.Op> children;

	private Artifact.Op<?> artifact;

	public MemPartialOrderGraphNode() {
		this(null);
	}

	public MemPartialOrderGraphNode(Artifact.Op<?> artifact) {
		this.artifact = artifact;
		this.parents = new ArrayList<>();
		this.children = new ArrayList<>();
	}

	@Override
	public Collection<Op> getPrevious() {
		return this.parents;
	}

	@Override
	public Collection<Op> getNext() {
		return this.children;
	}

	@Override
	public Artifact.Op<?> getArtifact() {
		return this.artifact;
	}

	@Override
	public void setArtifact(Artifact.Op<?> artifact) {
		this.artifact = artifact;
	}

	@Override
	public Op addChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.children.add(child);
		((MemPartialOrderGraphNode) child).parents.add(this);
		return child;
	}

	@Override
	public void removeChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.children.remove(child);
		((MemPartialOrderGraphNode) child).parents.remove(this);
	}

	@Override
	public String toString() {
		return this.getArtifact() == null ? "NULL" : this.getArtifact().toString() + " [" + this.getArtifact().getSequenceNumber() + "]";
	}

}
