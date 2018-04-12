package at.jku.isse.ecco.storage.mem.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.mem.sg.MemSequenceGraph;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of the {@link Artifact}.
 */
public class MemArtifact<DataType extends ArtifactData> implements Artifact<DataType>, Artifact.Op<DataType> {

	public static final long serialVersionUID = 1L;


	private DataType data;

	private boolean atomic;

	private boolean ordered;

	private SequenceGraph.Op sequenceGraph;

	private int sequenceNumber;

	private boolean useReferencesInEquals;


	public MemArtifact(DataType data) {
		this(data, false);
	}

	public MemArtifact(DataType data, boolean ordered) {
		checkNotNull(data);
		this.data = data;
		this.ordered = ordered;
		this.sequenceNumber = SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER;
		this.useReferencesInEquals = false;
	}


	@Override
	public int hashCode() {
		int result = this.data.hashCode();
		result = 31 * result + (this.ordered ? 1 : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MemArtifact<?> that = (MemArtifact<?>) o;

		if (this.isOrdered() != that.isOrdered()) return false;
		if (this.getSequenceNumber() != SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER && that.getSequenceNumber() != SequenceGraph.UNASSIGNED_SEQUENCE_NUMBER && this.getSequenceNumber() != that.getSequenceNumber())
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
		return this.data.toString();
	}


	@Override
	public DataType getData() {
		return this.data;
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
		return new MemSequenceGraph();
	}


	// CONTAINING NODE

	private Node.Op containingNode;

	@Override
	public Node.Op getContainingNode() {
		return this.containingNode;
	}

	@Override
	public void setContainingNode(final Node.Op node) {
		this.containingNode = node;
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

		ArtifactReference.Op artifactReference = new MemArtifactReference();
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
