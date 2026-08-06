package at.jku.isse.ecco.storage.jackson.artifact;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.jackson.pog.JacksonPartialOrderGraph;
import at.jku.isse.ecco.storage.jackson.tree.JacksonNode;
import at.jku.isse.ecco.tree.Node;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.eclipse.collections.impl.factory.Maps;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of the {@link Artifact}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class JacksonArtifact<DataType extends ArtifactData> implements Artifact<DataType>, Artifact.Op<DataType> {

	public static final long serialVersionUID = 1L;


	private transient DataType data;

	private byte[] buffer = null;

	private boolean atomic;

	private boolean ordered;

	private JacksonPartialOrderGraph sequenceGraph;

	private int sequenceNumber;

	private boolean useReferencesInEquals;

	private transient Artifact.Op replacingArtifact;


	public JacksonArtifact() {

	}

	public JacksonArtifact(DataType data) {
		this(data, false);
	}

	public JacksonArtifact(DataType data, boolean ordered) {
		checkNotNull(data);
		this.setData(data);
		this.ordered = ordered;
		this.sequenceNumber = PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER;
		this.useReferencesInEquals = false;
		this.replacingArtifact = null;
	}


	@Override
	public int hashCode() {
		int result = this.getData().hashCode();
		result = 31 * result + (this.ordered ? 1 : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JacksonArtifact<?> that = (JacksonArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;
		if (this.getSequenceNumber() != PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER && that.getSequenceNumber() != PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER && this.getSequenceNumber() != that.getSequenceNumber())
			return false;

		if (!this.useReferencesInEquals())
			return getData().equals(that.getData());
		else {
			if (!this.getData().equals(that.getData()))
				return false;
			if (this.getUses().size() != that.getUses().size())
				return false;
			for (ArtifactReference ar : this.getUses()) {
				boolean found = false;
				for (ArtifactReference thatAR : that.getUses()) {
					if (ar.getTarget().equals(thatAR.getTarget())) {
						found = true;
						break;
					}
				}
				if (!found)
					return false;
				// this causes an endless recursion:
				//if (!that.getUses().contains(ar))
				//	return false;
			}
			return true;
		}
	}

	@Override
	public boolean useReferencesInEquals() {
		return this.useReferencesInEquals;
	}

	@Override
	public void setUseReferencesInEquals(boolean useReferenesInEquals) {
		this.useReferencesInEquals = useReferenesInEquals;
	}

	@Override
	public boolean equalsIgnoreSequenceNumber(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JacksonArtifact<?> that = (JacksonArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;

		return getData().equals(that.getData());
	}


	@Override
	public String toString() {
		return this.getData().toString();
	}


	@Override
	@SuppressWarnings("unchecked")
	public DataType getData() {
		if (this.data == null) {
			if (this.buffer == null)
				return null;
			else {
				try (ByteArrayInputStream bis = new ByteArrayInputStream(this.buffer)) {
					try (ObjectInput in = new ObjectInputStream(bis)) {
						this.data = (DataType) in.readObject();
					}
				} catch (IOException | ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}

		return this.data;
	}

	private void setData(DataType data) {
		this.data = data;

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			try (ObjectOutput out = new ObjectOutputStream(bos)) {
				out.writeObject(this.data);
				this.buffer = bos.toByteArray();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	@Override
	public boolean isAtomic() {
		return this.atomic;
	}

	@Override
	public void setAtomic(boolean atomic) {
		this.atomic = atomic;
	}

	@Override
	public boolean isOrdered() {
		return this.ordered;
	}

	@Override
	public void setOrdered(boolean ordered) {
		this.ordered = ordered;
	}

	@Override
	public PartialOrderGraph.Op getSequenceGraph() {
		return this.sequenceGraph;
	}

	@Override
	public void setSequenceGraph(PartialOrderGraph.Op sequenceGraph) {
		if (!(sequenceGraph instanceof JacksonPartialOrderGraph))
			throw new EccoException("Only Jackson storage types can be used.");
		this.sequenceGraph = (JacksonPartialOrderGraph) sequenceGraph;
	}

	@Override
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	@Override
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	@Override
	public Op<?> getReplacingArtifact() {
		return this.replacingArtifact;
	}

	@Override
	public void setReplacingArtifact(Op<?> replacingArtifact) {
		this.replacingArtifact = replacingArtifact;
	}

	@Override
	public boolean isSequenced() {
		return this.sequenceGraph != null;
	}


	@Override
	public PartialOrderGraph.Op createSequenceGraph() {
		return new JacksonPartialOrderGraph();
	}


	// CONTAINING NODE

	@JsonBackReference
	private JacksonNode containingNode;

	@Override
	public Node.Op getContainingNode() {
		return this.containingNode;
	}

	@Override
	public void setContainingNode(final Node.Op node) {
		if (!(node instanceof JacksonNode))
			throw new EccoException("Only Jackson storage types can be used.");
		this.containingNode = (JacksonNode) node;
	}


	// REFERENCES

	private final Collection<ArtifactReference.Op> uses = new ArrayList<>();
	private final Collection<ArtifactReference.Op> usedBy = new ArrayList<>();

	@Override
	public Collection<ArtifactReference.Op> getUses() {
		return Collections.unmodifiableCollection(this.uses);
	}

	@Override
	public Collection<ArtifactReference.Op> getUsedBy() {
		return Collections.unmodifiableCollection(this.usedBy);
	}

	@Override
	public void addUses(final ArtifactReference.Op reference) {
		checkNotNull(reference);

		this.uses.add(reference);
	}

	@Override
	public void addUsedBy(final ArtifactReference.Op reference) {
		checkNotNull(reference);

		this.usedBy.add(reference);
	}

	@Override
	public void addUses(Op target) {
		this.addUses(target, "");
	}

	@Override
	public void addUses(Op target, String type) {
		checkNotNull(target);
		checkNotNull(type);

		if (this.uses(target))
			return;

		ArtifactReference.Op artifactReference = new JacksonArtifactReference();
		artifactReference.setSource(this);
		artifactReference.setTarget(target);
		this.addUses(artifactReference);
		target.addUsedBy(artifactReference);
	}


	// PROPERTIES

	private transient Map<String, Object> properties = null;

	@Override
	public Map<String, Object> getProperties() {
		if (this.properties == null)
			this.properties = Maps.mutable.empty();
		return this.properties;
	}

}
