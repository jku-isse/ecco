package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.ArtifactOperator;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JsonArtifact<DataType extends ArtifactData> implements Artifact.Op<DataType> {

    private transient ArtifactOperator operator = new ArtifactOperator(this);


    // fields

    private DataType data;

    private boolean atomic;

    private boolean ordered;

    private SequenceGraph.Op sequenceGraph;

    private int sequenceNumber;

    private boolean useReferencesInEquals;


    // constructors

    public JsonArtifact() {
        this(null);
    }

    public JsonArtifact(DataType data) {
        this(data, false);
    }

    public JsonArtifact(DataType data, boolean ordered) {
        this.data = data;
        this.ordered = ordered;
        this.sequenceNumber = Artifact.UNASSIGNED_SEQUENCE_NUMBER;
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

        JsonArtifact<?> that = (JsonArtifact<?>) o;

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
        return new JsonSequenceGraph();
    }


    @Override
    public void checkConsistency() {
        this.operator.checkConsistency();
    }

    @Override
    public boolean hasReplacingArtifact() {
        return this.operator.hasReplacingArtifact();
    }

    @Override
    public Op getReplacingArtifact() {
        return this.operator.getReplacingArtifact();
    }

    @Override
    public void setReplacingArtifact(Op replacingArtifact) {
        this.operator.setReplacingArtifact(replacingArtifact);
    }

    @Override
    public void updateArtifactReferences() {
        this.operator.updateArtifactReferences();
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
    public boolean uses(Op target) {
        return this.operator.uses(target);
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

        ArtifactReference.Op artifactReference = new JsonArtifactReference();
        artifactReference.setSource(this);
        artifactReference.setTarget(target);
        this.addUses(artifactReference);
        target.addUsedBy(artifactReference);
    }


    // PROPERTIES

    private transient Map<String, Object> properties = new HashMap<>();

    @Override
    public Map<String, Object> getProperties() {
        return this.properties;
    }

    @Override
    public <T> Optional<T> getProperty(final String name) {
        return this.operator.getProperty(name);
    }

    @Override
    public <T> void putProperty(final String name, final T property) {
        this.operator.putProperty(name, property);
    }

    @Override
    public void removeProperty(String name) {
        this.operator.removeProperty(name);
    }
}
