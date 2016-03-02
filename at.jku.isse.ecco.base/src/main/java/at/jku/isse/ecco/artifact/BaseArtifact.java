package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.sequenceGraph.BaseSequenceGraph;
import at.jku.isse.ecco.sequenceGraph.SequenceGraph;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of the {@link Artifact}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseArtifact<DataType extends ArtifactData> implements Artifact<DataType> {

	// fields

	private DataType data;

	private boolean atomic;

	private boolean ordered;

	private SequenceGraph sequenceGraph;

	private int sequenceNumber;


	// constructors

	public BaseArtifact() {
		this(null);
	}

	public BaseArtifact(DataType data) {
		this(data, false);
	}

	public BaseArtifact(DataType data, boolean ordered) {
		this.data = data;
		this.ordered = ordered;
		this.sequenceNumber = Artifact.UNASSIGNED_SEQUENCE_NUMBER;
	}


	@Override
	public int hashCode() {
		int result = data.hashCode();
		result = 31 * result + (ordered ? 1 : 0);
		if (this.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER)
			result = 31 * result + sequenceNumber;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BaseArtifact<?> that = (BaseArtifact<?>) o;

		if (ordered != that.ordered) return false;
		if (this.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER && that.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER && this.sequenceNumber != that.sequenceNumber)
			return false;
		return data.equals(that.data);
	}

	@Override
	public String toString() {
		return this.data.toString();
	}


	@Override
	public DataType getData() {
		return this.data;
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
		return new BaseSequenceGraph();
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

	private Map<String, Object> properties = new HashMap<>();

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
