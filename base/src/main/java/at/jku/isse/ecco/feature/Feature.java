package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

import java.util.Collection;

/**
 * Contains id, name and description of a feature as well as a collection of all revisions of this feature.
 */
public interface Feature extends Persistable {

	public Collection<? extends FeatureRevision> getRevisions();

	public FeatureRevision addRevision(String id);

	public FeatureRevision getRevision(String id);


	public FeatureRevision getLatestRevision();


	/**
	 * Returns the id of the feature.
	 *
	 * @return The id.
	 */
	public String getId();

	/**
	 * Returns the name of the feature.
	 *
	 * @return The name.
	 */
	public String getName();

	/**
	 * Sets the name of the feature.
	 *
	 * @param name of the feature
	 */
	public void setName(String name);

	/**
	 * Returns the description.
	 *
	 * @return The description.
	 */
	public String getDescription();

	/**
	 * Sets the description.
	 *
	 * @param description of the association
	 */
	public void setDescription(String description);


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);


	public default String getFeatureString() {
		return this.getName();
	}

	/**
	 * Should call {@link #getFeatureString}.
	 *
	 * @return The feature string representing this feature.
	 */
	@Override
	public String toString();

}
