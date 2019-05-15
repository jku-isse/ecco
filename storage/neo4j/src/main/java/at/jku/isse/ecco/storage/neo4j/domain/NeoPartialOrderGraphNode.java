package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class NeoPartialOrderGraphNode extends NeoEntity implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	@Relationship(type = "hasPreviousPOGN")
	private Collection<Op> previous;

	@Relationship("hasPreviousPOGN")
	private Collection<Op> next;

	@Relationship("hasPOGAf")
	private Artifact.Op<?> artifact;

	public NeoPartialOrderGraphNode() {}

	public NeoPartialOrderGraphNode(Artifact.Op<?> artifact) {
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
		((NeoPartialOrderGraphNode) child).previous.add(this);
		return child;
	}

	@Override
	public void removeChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.remove(child);
		((NeoPartialOrderGraphNode) child).previous.remove(this);
	}

	@Override
	public String toString() {
		return this.getArtifact() == null ? "NULL" : this.getArtifact().toString() + " [" + this.getArtifact().getSequenceNumber() + "]";
	}

}
