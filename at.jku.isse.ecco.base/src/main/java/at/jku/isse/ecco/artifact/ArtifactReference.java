package at.jku.isse.ecco.artifact;

/**
 * Captures the connection between two artifacts.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface ArtifactReference {

	/**
	 * Returns the source of the reference.
	 *
	 * @return The source of the reference.
	 */
	Artifact<?> getSource();

	/**
	 * Returns the target that is referenced.
	 *
	 * @return The target that is referenced.s
	 */
	Artifact<?> getTarget();

	/**
	 * The reference type.
	 *
	 * @return The type of the references.
	 */
	String getType();

	/**
	 * Sets the source of the reference.
	 *
	 * @param source of the reference.s
	 */
	void setSource(Artifact<?> source);

	/**
	 * Sets the target that is referenced.s
	 *
	 * @param target that is referenced
	 */
	void setTarget(Artifact<?> target);

}
