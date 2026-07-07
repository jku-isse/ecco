package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

/**
 * Represents a revision of a feature.
 */
public interface FeatureRevision extends Persistable {

	/**
	 * Returns the feature belonging to this version.
	 *
	 * @return The feature belonging to this version.
	 */
	Feature getFeature();

	String getId();

	String getDescription();

	void setDescription(String description);

	String getLogicLiteralRepresentation();

	@Override
	int hashCode();

	@Override
	boolean equals(Object object);

	default String getFeatureRevisionString() {
		return this.getFeature().toString() + "." + this.getId().substring(0, Math.min(this.getId().length(), 7));
	}

	/**
	 * Should call {@link #getFeatureRevisionString}.
	 *
	 * @return The feature revision string representing this feature revision.
	 */
	@Override
	String toString();

}
