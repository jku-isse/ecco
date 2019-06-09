package at.jku.isse.ecco.storage.perst.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.perst.sg.PerstSequenceGraph;
import at.jku.isse.ecco.tree.Node;
import org.garret.perst.Persistent;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of the {@link Artifact}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstArtifact<DataType extends ArtifactData> extends Persistent implements Artifact<DataType>, Artifact.Op<DataType> {

	// data

	private transient DataType data = null;

	private byte[] buffer = null;

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

	public void setData(DataType data) {
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
	public void store() {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			try (ObjectOutput out = new ObjectOutputStream(bos)) {
				out.writeObject(this.getData());
				this.buffer = bos.toByteArray();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		super.store();
	}


	// fields

	private boolean atomic;

	private boolean ordered;

	private SequenceGraph.Op sequenceGraph;

	private int sequenceNumber;

	private boolean useReferencesInEquals;


	// constructors

	public PerstArtifact() {
		//this(null);
	}

	public PerstArtifact(DataType data) {
		this(data, false);
	}

	public PerstArtifact(DataType data, boolean ordered) {
		this.setData(data);
		this.ordered = ordered;
		this.sequenceNumber = SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER;
	}


	@Override
	public int hashCode() {
		int result = getData().hashCode();
		result = 31 * result + (ordered ? 1 : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PerstArtifact<?> that = (PerstArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;
		if (this.getSequenceNumber() != SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER && that.getSequenceNumber() != SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER && this.getSequenceNumber() != that.getSequenceNumber())
			return false;

		if (!this.useReferencesInEquals()) {
			if (this.getData() == null)
				return that.getData() == null;
			else
				return getData().equals(that.getData());
		} else {
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
	public String toString() {
		if (this.getData() == null)
			return "NULL";
		return this.getData().toString();
	}


	@Override
	public boolean equalsIgnoreSequenceNumber(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PerstArtifact<?> that = (PerstArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;

		return getData().equals(that.getData());
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
	public SequenceGraph.Op getSequenceGraph() {
		return this.sequenceGraph;
	}

	@Override
	public void setSequenceGraph(SequenceGraph.Op sequenceGraph) {
		this.sequenceGraph = sequenceGraph;
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
	public boolean isSequenced() {
		return this.sequenceGraph != null;
	}


	@Override
	public SequenceGraph.Op createSequenceGraph() {
		return new PerstSequenceGraph();
	}


	// containing node

	private Node.Op containingNode;

	@Override
	public Node.Op getContainingNode() {
		return containingNode;
	}

	@Override
	public void setContainingNode(final Node.Op node) {
		containingNode = node;
	}


	// uses and usedBy

	private final List<ArtifactReference.Op> uses = new ArrayList<>();
	private final List<ArtifactReference.Op> usedBy = new ArrayList<>();

	@Override
	public List<ArtifactReference.Op> getUsedBy() {
		return usedBy;
	}

	@Override
	public List<ArtifactReference.Op> getUses() {
		return uses;
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

		ArtifactReference.Op artifactReference = new PerstArtifactReference();
		artifactReference.setSource(this);
		artifactReference.setTarget(target);
		this.addUses(artifactReference);
		target.addUsedBy(artifactReference);
	}


	// properties

	private transient Map<String, Object> properties = new HashMap<>();

	@Override
	public Map<String, Object> getProperties() {
		return this.properties;
	}

}
