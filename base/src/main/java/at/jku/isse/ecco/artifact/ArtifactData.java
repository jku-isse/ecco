package at.jku.isse.ecco.artifact;

import java.io.Serializable;

/**
 * Interface that data types stored in artifacts must implement.
 * All data objects must be {@link Serializable} and override {@link Object#hashCode()}, {@link Object#equals(Object)}, and {@link Object#toString()}.
 */
public interface ArtifactData extends Serializable {

	/**
	 * Artifact data types must provide a {@link Object#hashCode()} implementation.
	 *
	 * @return The hash code of this artifact data object.
	 */
	@Override
	public int hashCode();

	/**
	 * Artifact data types must provide an {@link Object#equals(Object)} implementation.
	 *
	 * @param obj The object to compare to this artifact data object.
	 * @return True if this artifact data object is equal to the given object, false otherwise.
	 */
	@Override
	public boolean equals(Object obj);

	/**
	 * Artifact data types must provide a {@link Object#toString()} implementation.
	 *
	 * @return The string representation of this artifact data object.
	 */
	@Override
	public String toString();

	/**
	 * Called when this (stored) data's artifact is unified with an equal artifact read by a newer
	 * commit, which is then discarded (see Trees.slice()). Data that carries information outside of
	 * its identity (not part of equals()) can adopt the newer values here, so the repository reflects
	 * the latest commit rather than the first one - e.g. a text file's recorded line separator.
	 * Must not change anything equals()/hashCode() depend on. Does nothing by default.
	 *
	 * @param newer The equal data from the newer commit.
	 */
	default void adoptMetadataFrom(ArtifactData newer) {
	}

	/**
	 * Whether artifacts with this data must be ordered, i.e. keep the order of their children - an
	 * adapter that used to create them unordered makes repositories written before inconsistent with
	 * what it reads now (see Repository.Op#checkOrderedArtifacts). False by default: the reader
	 * decides per node.
	 */
	default boolean requiresOrderedArtifact() {
		return false;
	}

}
