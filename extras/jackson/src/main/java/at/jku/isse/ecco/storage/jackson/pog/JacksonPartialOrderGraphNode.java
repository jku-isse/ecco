package at.jku.isse.ecco.storage.jackson.pog;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class JacksonPartialOrderGraphNode implements PartialOrderGraph.Node, PartialOrderGraph.Node.Op {

	public static final long serialVersionUID = 1L;

	@JsonBackReference
	private Collection<JacksonPartialOrderGraphNode> previous;
	@JsonManagedReference
	private Collection<JacksonPartialOrderGraphNode> next;

	private Artifact.Op<?> artifact;

	public JacksonPartialOrderGraphNode(Artifact.Op<?> artifact) {
//		Objects.requireNonNull(artifact);
		this.artifact = artifact;
		this.previous = new ArrayList<>();
		this.next = new ArrayList<>();
	}

	@Override
	public Collection<? extends Op> getPrevious() {
		return this.previous;
	}

	@Override
	public Collection<? extends Op> getNext() {
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
		this.next.add((JacksonPartialOrderGraphNode) child);
		((JacksonPartialOrderGraphNode) child).previous.add(this);
		return child;
	}

	@Override
	public void removeChild(Op child) {
		if (child.getClass() != this.getClass())
			throw new EccoException("Incompatible storage types.");
		this.next.remove(child);
		((JacksonPartialOrderGraphNode) child).previous.remove(this);
	}

	@Override
	public String toString() {
		return this.getArtifact() == null ? "NULL" : this.getArtifact().toString() + " [" + this.getArtifact().getSequenceNumber() + "]";
	}

}
