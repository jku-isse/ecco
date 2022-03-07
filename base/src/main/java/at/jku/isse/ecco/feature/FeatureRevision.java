package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a revision of a feature.
 */
public interface FeatureRevision extends Persistable {

	/**
	 * Returns the feature belonging to this version.
	 *
	 * @return The feature belonging to this version.
	 */
	@JsonIgnore
	public Feature getFeature();


	public String getId();

	public String getDescription();

	public void setDescription(String description);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);


	public default String getFeatureRevisionString() {
		return this.getFeature().toString() + "." + this.getId().substring(0, Math.min(this.getId().length(), 7));
	}

	/**
	 * Should call {@link #getFeatureRevisionString}.
	 *
	 * @return The feature revision string representing this feature revision.
	 */
	@Override
	public String toString();

}
