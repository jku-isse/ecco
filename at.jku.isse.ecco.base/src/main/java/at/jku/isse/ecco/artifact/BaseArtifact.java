package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.tree.Node;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BaseArtifact<DataType extends ArtifactData> implements Artifact<DataType> {

	private DataType data;

	public BaseArtifact(DataType data) {
		this.data = data;
	}

	@Override
	public DataType getData() {
		return this.data;
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BaseArtifact that = (BaseArtifact) o;

		return data.equals(that.data);

	}

	@Override
	public String toString() {
		return this.data.toString();
	}

	// FIELDS #####################################################

	private final List<ArtifactReference> uses = new ArrayList<>();
	private final List<ArtifactReference> usedBy = new ArrayList<>();

	private Map<String, Object> properties = new HashMap<>();
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
