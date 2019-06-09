package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.sg.JpaSequenceGraph;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.tree.JpaNode;
import at.jku.isse.ecco.tree.Node;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaArtifact<DataType extends ArtifactData> implements Artifact<DataType>, Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;


	@Embedded
	//@Transient
	private Object data = null;

	//@Embedded
	//@Column(columnDefinition = "blob")
	//@Lob
	//private byte[] buffer = null;

	@Override
	public DataType getData() {
//		if (this.data == null) {
//			Kryo kryo = new Kryo();
//
//			// read
//			Input input = new Input(this.buffer);
//			Object object = kryo.readClassAndObject(input);
//
//			this.data = object;
//		}

		return (DataType) this.data;
	}

	@Override
	public boolean useReferencesInEquals() {
		return false;
	}

	@Override
	public void setUseReferencesInEquals(boolean useReferenesInEquals) {

	}

	public void setData(DataType data) {
		this.data = data;


//		Kryo kryo = new Kryo();
//
//		// write
//		Output output = new Output(100, 2048);
//		kryo.writeClassAndObject(output, data);
//		this.buffer = output.getBuffer();
	}


	private boolean atomic;

	private boolean ordered;

	@OneToOne(targetEntity = JpaSequenceGraph.class)
	private SequenceGraph sequenceGraph;

	private int sequenceNumber;


	// constructors
	public JpaArtifact() {
		this(null);
	}

	public JpaArtifact(DataType data) {
		this(data, false);
	}

	public JpaArtifact(DataType data, boolean ordered) {
		//this.data = data;
		this.ordered = ordered;
		this.sequenceNumber = Artifact.UNASSIGNED_SEQUENCE_NUMBER;

		this.setData(data);
	}


	@Override
	public int hashCode() {
		int result = getData().hashCode();
		result = 31 * result + (ordered ? 1 : 0);
		if (this.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER)
			result = 31 * result + sequenceNumber;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JpaArtifact<?> that = (JpaArtifact<?>) o;

		if (ordered != that.ordered) return false;
		if (this.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER && that.sequenceNumber != Artifact.UNASSIGNED_SEQUENCE_NUMBER && this.sequenceNumber != that.sequenceNumber)
			return false;
		return getData().equals(that.getData());
	}

	@Override
	public String toString() {
		return this.getData().toString();
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
		return new JpaSequenceGraph();
	}


	// FIELDS #####################################################

	private final ArrayList<ArtifactReference> uses = new ArrayList<>();
	private final ArrayList<ArtifactReference> usedBy = new ArrayList<>();

	private HashMap<String, Object> properties = new HashMap<>();
	@ManyToOne(targetEntity = JpaNode.class)
	private Node containingNode;

	// METHODS #####################################################

	@Override
	public Node getContainingNode() {
		return containingNode;
	}

	@Override
	public void setContainingNode(final Node node) {
		containingNode = node;
	}

	// uses and usedBy

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
