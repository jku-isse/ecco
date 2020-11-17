package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.dao.Persistable;

/**
 * Public interface for artifact references that represent the dependency of a source artifact on a target artifact.
 */
public interface ArtifactReference extends Persistable {

	/**
	 * An artifact reference's hash code is based on the hash codes of the source and target artifacts and its type string.
	 *
	 * @return The hash code of this artifact reference.
	 */
	@Override
	public int hashCode();

	/**
	 * Two artifact references are equal if their types, source artifacts, and target artifacts are respectively equal.
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
	 * Returns the source artifact of the reference that depends on the target.
	 *
	 * @return The source artifact of the reference.
	 */
	public Artifact<?> getSource();

	/**
	 * Returns the target artifact that is referenced.
	 *
	 * @return The target artifact that is referenced.
	 */
	public Artifact<?> getTarget();

	/**
	 * Returns the type of the reference as a string. The type can be arbitrarily assigned.
	 *
	 * @return The type of the reference.
	 */
	public String getType();


	/**
	 * Private interface for artifact references that is used internally and not passed outside.
	 */
	public interface Op extends ArtifactReference {
		/**
		 * Returns the source artifact as an artifact operand.
		 *
		 * @return The source artifact operand.
		 */
		@Override
		public Artifact.Op<?> getSource();

		/**
		 * Returns the target artifact as an artifact operand.
		 *
		 * @return The target artifact operand.
		 */
		@Override
		public Artifact.Op<?> getTarget();

		/**
		 * Sets the source artifact operand of the reference.
		 *
		 * @param source of the reference
		 */
		public void setSource(Artifact.Op<?> source);

		/**
		 * Sets the target artifact operand of the reference..
		 *
		 * @param target that is referenced
		 */
		public void setTarget(Artifact.Op<?> target);
	}

}
