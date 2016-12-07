package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

import java.util.List;

/**
 * Contains id, name and description of a feature.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public interface Feature extends Persistable {

	public List<? extends FeatureVersion> getVersions();

	public FeatureVersion addVersion(String id);

	public FeatureVersion getVersion(String id);


	public FeatureVersion getLatestVersion();

	public FeatureVersion createNewVersion();


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

}
