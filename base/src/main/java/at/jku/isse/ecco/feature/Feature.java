package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

import java.util.Collection;

/**
 * Contains id, name and description of a feature as well as a collection of all revisions of this feature.
 */
public interface Feature extends Persistable {
	Collection<? extends FeatureRevision> getRevisions();

	FeatureRevision addRevision(String id);

	FeatureRevision getRevision(String id);

	FeatureRevision getOrphanedRevision(String id);

	FeatureRevision getLatestRevision();

	Feature feature(String name);


	/**
	 * Returns the id of the feature.
	 *
	 * @return The id.
	 */
	String getId();

	/**
	 * Returns the name of the feature.
	 *
	 * @return The name.
	 */
	String getName();

	/**
	 * Sets the name of the feature.
	 *
	 * @param name of the feature
	 */
	void setName(String name);

	/**
	 * Returns the description.
	 *
	 * @return The description.
	 */
	String getDescription();

	/**
	 * Sets the description.
	 *
	 * @param description of the association
	 */
	void setDescription(String description);


	@Override
	int hashCode();

	@Override
	boolean equals(Object object);

	default String getFeatureString() {
		return this.getName();
	}

	/**
	 * Should call {@link #getFeatureString}.
	 *
	 * @return The feature string representing this feature.
	 */
	@Override
	String toString();

}
