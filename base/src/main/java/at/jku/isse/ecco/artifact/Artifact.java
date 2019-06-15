package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Public interface for artifacts that stores the actual data and references to other artifacts.
 *
 * @param <DataType> The type of the data object stored in the artifact.
 */
public interface Artifact<DataType extends ArtifactData> extends Persistable {

	/**
	 * Setting this property indicates that the artifact's file representation was not modified since it was written.
	 */
	public static final String PROPERTY_UNMODIFIED = "unmodified";

//	/**
//	 * This property is set by some tree operations (see for example {@link at.jku.isse.ecco.util.Trees#slice(Node.Op, Node.Op)} and then subsequently used by other tree operations (see for example {@link at.jku.isse.ecco.util.Trees#updateArtifactReferences(Node.Op)}.
//	 */
//	public static final String PROPERTY_REPLACING_ARTIFACT = "replacingArtifact";

	/**
	 * Artifacts can be marked by setting this property. Other operations can then use this property (see for example {@link at.jku.isse.ecco.util.Trees#extractMarked(Node.Op)}).
	 */
	public static final String PROPERTY_MARKED_FOR_EXTRACTION = "marked";

	/**
	 * This property is set by {@link at.jku.isse.ecco.util.Trees#map(Node.Op, Node.Op)} and contains another artifact from another tree that maps to this one.
	 */
	public static final String PROPERTY_MAPPED_ARTIFACT = "mapped";


	/**
	 * An artifact's hash code is based on the hash code if the contained data object and on its type (i.e. ordered or unordered).
	 * An artifact must NOT use its sequence number for the computation of its hash code!
	 *
	 * @return The hash code of this artifact.
	 */
	@Override
	public int hashCode();

	/**
	 * Two artifacts are equal when
	 * 1) the contained data objects are equal,
	 * 2) they are of the same type (i.e. both ordered or both unordered), and
	 * 3) when both have a sequence number assigned, these sequence numbers must be equal.
	 *
	 * @param obj The object to compare to this artifact.
	 * @return True if this artifact is equal to the given object, false otherwise.
	 */
	@Override
	public boolean equals(Object obj);

	/**
	 * The string representation of an artifact is determined by the contained data object.
	 *
	 * @return The string representation of the artifact.
	 */
	@Override
	public String toString();


	/**
	 * The data object stored in the artifact. The type and its contents are determined by the artifat plugin that created the artifact.
	 *
	 * @return The data object that was stored in the artifact at the time it was created by an artifact plugin.
	 */
	public DataType getData();


	/**
	 * An artifact that is atomic will not be split up further, even if it has children.
	 * In other words, the children of an atomic artifact (or more accurately, the children of nodes containing an atomic artifact) will not be split up by any tree operation (see for example {@link at.jku.isse.ecco.util.Trees#slice(Node.Op, Node.Op)}).
	 *
	 * @return True if the artifact is atomic, false otherwise.
	 */
	public boolean isAtomic();

	/**
	 * There are two types of artifacts: ordered and unordered.
	 *
	 * <i>Unordered</i> artifacts do not contain a sequence graph (see {@link at.jku.isse.ecco.pog.PartialOrderGraph}) and therefore its children (or more accurately the children of nodes containing the unordered artifact) will not be assigned a sequence number.
	 * As a consequence of this, and because the children of an artifact must be unique, the child artifacts of an unordered artifact must be uniquely identifiable just by their contained data object, as it is the only means of identification aside from their sequence number (see {@link at.jku.isse.ecco.artifact.Artifact#equals(Object)}.
	 * In other words, no two child artifacts can contain equal data objects.
	 *
	 * <i>Ordered</i> artifacts are assigned a sequence graph (see {@link at.jku.isse.ecco.pog.PartialOrderGraph}) the first time they are being processed by any operation (see for example {@link at.jku.isse.ecco.util.Trees#slice(Node.Op, Node.Op)}).
	 * This process is called <i>sequencing</i> of an ordered artifact. During this process the children of the ordered artifact are assigned sequence numbers based on their order of occurrence.
	 * This assigned sequence number is used as an additional means of identifying the child artifacts. This makes it possible to have child artifacts containing equal data objects but different sequence numbers.
	 * This is for example necessary when the child artifacts represent statements in a programming language: statements are not unique, the same statement can appear multiple times in a sequence of statements, and the position of a statement in the sequence matters. This is what the sequence number is used for.
	 *
	 * @return True if the node is ordered, false otherwise.
	 */
	public boolean isOrdered();

	/**
	 * Determines whether the artifact contains a sequence graph.
	 * An unordered node will never be sequenced (i.e. never contain a sequence graph).
	 * An ordered node will initially (after its creation) also not be sequenced. After being processed for the first time by any operation (see for example {@link at.jku.isse.ecco.util.Trees#slice(Node.Op, Node.Op)}) it will be assigned a sequence graph.
	 *
	 * @return True when the artifact has a sequence graph assigned, false otherwise
	 */
	public boolean isSequenced();

	public PartialOrderGraph getSequenceGraph();


	public boolean equalsIgnoreSequenceNumber(Object obj);

	/**
	 * Returns the assigned sequence number in case this artifact is the child of an ordered artifact that has already been sequenced, or {@link at.jku.isse.ecco.pog.PartialOrderGraph#UNASSIGNED_SEQUENCE_NUMBER} otherwise.
	 *
	 * @return The assigned sequence number in case this artifact is the child of an ordered artifact that has already been sequenced, or {@link at.jku.isse.ecco.pog.PartialOrderGraph#UNASSIGNED_SEQUENCE_NUMBER} otherwise.
	 */
	public int getSequenceNumber();


	/**
	 * Returns the one unique node from the artifact tree that contains this artifact.
	 *
	 * @return The containing node.
	 */
	public Node getContainingNode();


	/**
	 * Sets the containing node from the artifact tree.
	 *
	 * @param node that this artifact contains (@Nullable)
	 */
	public void setContainingNode(Node.Op node);


	// REFERENCES

	/**
	 * Returns the references to the artifacts that this artifact uses. A uses reference describes which other artifact this artifact depends on.
	 *
	 * @return References to the used artifacts.
	 */
	public Collection<? extends ArtifactReference> getUses();

	/**
	 * Returns the references to the artifacts that are used by this artifact. A used by reference describes which other artifact uses this artifact..
	 *
	 * @return References to artifacts this artifact is used by.
	 */
	public Collection<? extends ArtifactReference> getUsedBy();


	// PROPERTIES

	public Map<String, Object> getProperties();

	/**
	 * Returns the property with the given name in form of an optional. The optional will only contain a result if the name and the type are correct. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name of the property that should be retrieved
	 * @param <T>  The type of the property.
	 * @return An optional which contains the actual property or nothing.
	 */
	public default <T> Optional<T> getProperty(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");

		Optional<T> result = Optional.empty();
		if (this.getProperties().containsKey(name)) {
			final Object obj = this.getProperties().get(name);
			try {
				@SuppressWarnings("unchecked") final T item = (T) obj;
				result = Optional.of(item);
			} catch (final ClassCastException e) {
				System.err.println("Expected a different type of the property.");
			}
		}

		return result;
	}

	/**
	 * Adds a new property to this artifact. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 * <p>
	 * These properties are volatile, i.e. they are not persisted!
	 *
	 * @param name     The name of the property.
	 * @param property The object to be stored as a property.
	 * @param <T>      The type of the property.
	 */
	public default <T> void putProperty(final String name, final T property) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");
		checkNotNull(property);

		this.getProperties().put(name, property);
	}

	/**
	 * Removes the property with the given name. If the name could not be found in the map it does nothing.
	 *
	 * @param name of the property that should be removed
	 */
	public default void removeProperty(String name) {
		checkNotNull(name);

		this.getProperties().remove(name);
	}


	// OPERATION INTERFACE

	/**
	 * Private artifact operand interface used internally and not passed outside.
	 *
	 * @param <DataType> The type of the data object stored in the artifact.
	 */
	public interface Op<DataType extends ArtifactData> extends Artifact<DataType> {

		/**
		 * Sets whether this artifact is atomic or not (see {@link Artifact#isAtomic()}).
		 *
		 * @param atomic Whether the artifact is atomic (true) or not (false).
		 */
		public void setAtomic(boolean atomic);

		/**
		 * Sets whether this artifact is ordered or not (see {@link Artifact#isOrdered()}).
		 *
		 * @param ordered Whether the artifact is ordered (true) or not (false).
		 */
		public void setOrdered(boolean ordered);

		/**
		 * Sets the sequence graph of this artifact.
		 *
		 * @param sequenceGraph The sequence graph.
		 */
		public void setSequenceGraph(PartialOrderGraph.Op sequenceGraph);


		/**
		 * Sets the sequence number of the artifact. This is used by the sequence graph.
		 *
		 * @param sequenceNumber The sequence number to assign to this artifact.
		 */
		public void setSequenceNumber(int sequenceNumber);


		// TODO: document these! make clear where a check is performed for "already existing" or "null" etc.

		public default void checkConsistency() {
			for (ArtifactReference.Op uses : this.getUses()) {
				if (uses.getSource() != this)
					throw new EccoException("Source of uses artifact reference must be identical to artifact.");
				for (ArtifactReference.Op usedBy : uses.getTarget().getUsedBy()) {
					if (usedBy.getSource() == this) {
						if (uses != usedBy)
							throw new EccoException("Artifact reference instance must be identical in source and target.");
					}
				}
			}
		}

		public default boolean hasReplacingArtifact() {
			return this.getReplacingArtifact() != null;
		}

		public Artifact.Op<?> getReplacingArtifact();

		public void setReplacingArtifact(Artifact.Op<?> replacingArtifact);

		public default void updateArtifactReferences() {
			// update "uses" artifact references
			for (ArtifactReference.Op uses : this.getUses()) {
				if (uses.getSource() != this)
					throw new EccoException("Source of uses artifact reference must be identical to artifact.");

				if (uses.getTarget().hasReplacingArtifact()) {
					Artifact.Op<?> replacingArtifact = uses.getTarget().getReplacingArtifact();
					if (replacingArtifact != null) {
						uses.setTarget(replacingArtifact);
						if (!replacingArtifact.getUsedBy().contains(uses)) {
							replacingArtifact.addUsedBy(uses);
						}
					}
				}
			}

			// update "used by" artifact references
			for (ArtifactReference.Op usedBy : this.getUsedBy()) {
				if (usedBy.getTarget() != this)
					throw new EccoException("Target of usedBy artifact reference must be identical to artifact.");

				if (usedBy.getSource().hasReplacingArtifact()) {
					Artifact.Op<?> replacingArtifact = usedBy.getSource().getReplacingArtifact();
					if (replacingArtifact != null) {
						usedBy.setSource(replacingArtifact);
						if (!replacingArtifact.getUses().contains(usedBy)) {
							replacingArtifact.addUses(usedBy);
						}
					}
				}
			}

			// update sequence graph symbols (which are artifacts)
			if (this.getSequenceGraph() != null) {
				this.getSequenceGraph().updateArtifactReferences();
			}
		}

		public default boolean uses(Artifact.Op<?> target) {
			for (ArtifactReference.Op uses : this.getUses()) {
				if (uses.getTarget() == target) {
					return true;
				}
			}
			return false;
		}

		public void addUses(Artifact.Op artifact);

		public void addUses(Artifact.Op artifact, String type);


		public PartialOrderGraph.Op getSequenceGraph();


		/**
		 * Adds a used by reference to the artifact. A used by association describes from which other artifact this artifact is used in some manner.
		 * This should be interface private. Do not use it outside of classes that implement this interface.
		 *
		 * @param reference to the artifact that uses this artifact
		 */
		public void addUsedBy(ArtifactReference.Op reference);

		/**
		 * Adds a uses reference to the artifact. A uses by association describes which other artifact this artifact uses in some manner.
		 * This should be interface private. Do not use it outside of classes that implement this interface.
		 *
		 * @param reference to the artifact that uses this artifact
		 */
		public void addUses(ArtifactReference.Op reference);

		@Override
		public Collection<ArtifactReference.Op> getUses();

		@Override
		public Collection<ArtifactReference.Op> getUsedBy();

		@Override
		public Node.Op getContainingNode();


		public PartialOrderGraph.Op createSequenceGraph();

		// TODO: possibly remove these:

		public boolean useReferencesInEquals();

		public void setUseReferencesInEquals(boolean useReferenesInEquals);
	}

}
