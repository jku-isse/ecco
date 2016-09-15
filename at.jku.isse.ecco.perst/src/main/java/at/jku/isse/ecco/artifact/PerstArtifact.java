package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.sg.PerstSequenceGraph;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.tree.Node;
import org.garret.perst.Persistent;

import java.io.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of the {@link Artifact}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstArtifact<DataType extends ArtifactData> extends Persistent implements Artifact<DataType> {

	// data

	private transient DataType data = null;

	private byte[] buffer = null;

	@Override
	public DataType getData() {
		if (this.data == null) {
			try (ByteArrayInputStream bis = new ByteArrayInputStream(this.buffer)) {
				try (ObjectInput in = new ObjectInputStream(bis)) {
					this.data = (DataType) in.readObject();
				}
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
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


	// fields

	private boolean atomic;

	private boolean ordered;

	private SequenceGraph sequenceGraph;

	private int sequenceNumber;

	private boolean useReferencesInEquals;


	// constructors

	public PerstArtifact() {
		this(null);
	}

	public PerstArtifact(DataType data) {
		this(data, false);
	}

	public PerstArtifact(DataType data, boolean ordered) {
		this.setData(data);
		this.ordered = ordered;
		this.sequenceNumber = Artifact.UNASSIGNED_SEQUENCE_NUMBER;
	}


	@Override
	public int hashCode() {
		int result = getData().hashCode();
		result = 31 * result + (ordered ? 1 : 0);
//		if (this.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER)
//			result = 31 * result + sequenceNumber;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PerstArtifact<?> that = (PerstArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;
		if (this.getSequenceNumber() != Artifact.UNASSIGNED_SEQUENCE_NUMBER && that.getSequenceNumber() != Artifact.UNASSIGNED_SEQUENCE_NUMBER && this.getSequenceNumber() != that.getSequenceNumber())
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
	public String toString() {
		return this.getData().toString();
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
	public SequenceGraph getSequenceGraph() {
		return this.sequenceGraph;
	}

	@Override
	public void setSequenceGraph(SequenceGraph sequenceGraph) {
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
	public SequenceGraph createSequenceGraph() {
		return new PerstSequenceGraph();
	}


	// containing node

	private Node containingNode;

	@Override
	public Node getContainingNode() {
		return containingNode;
	}

	@Override
	public void setContainingNode(final Node node) {
		containingNode = node;
	}


	// uses and usedBy

	private final List<ArtifactReference> uses = new ArrayList<>();
	private final List<ArtifactReference> usedBy = new ArrayList<>();

	@Override
	public List<ArtifactReference> getUsedBy() {
		return usedBy;
	}

	@Override
	public List<ArtifactReference> getUses() {
		return uses;
	}

	@Override
	public void setUsedBy(final List<ArtifactReference> references) {
		checkNotNull(references);

		usedBy.clear();
		if (!references.isEmpty())
			usedBy.addAll(references);
	}

	@Override
	public void setUses(final List<ArtifactReference> references) {
		checkNotNull(references);

		uses.clear();
		if (!references.isEmpty())
			uses.addAll(references);
	}

	@Override
	public void addUsedBy(final ArtifactReference reference) {
		checkNotNull(reference);

		usedBy.add(reference);
	}

	@Override
	public void addUses(final ArtifactReference reference) {
		checkNotNull(reference);

		uses.add(reference);
	}


	// properties

	private transient Map<String, Object> properties = new HashMap<>();

	@Override
	public <T> Optional<T> getProperty(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");

		Optional<T> result = Optional.empty();
		if (properties.containsKey(name)) {
			final Object obj = properties.get(name);
			try {
				@SuppressWarnings("unchecked")
				final T item = (T) obj;
				result = Optional.of(item);
			} catch (final ClassCastException e) {
				System.err.println("Expected a different type of the property.");
			}
		}

		return result;
	}

	@Override
	public <T> void putProperty(final String name, final T property) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");
		checkNotNull(property);

		properties.put(name, property);
	}

	@Override
	public void removeProperty(String name) {
		checkNotNull(name);

		properties.remove(name);
	}

}
