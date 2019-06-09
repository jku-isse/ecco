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

}
