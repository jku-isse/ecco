package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

import java.util.List;

/**
 * Contains name and description of a feature.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface Feature extends Persistable {

	public List<FeatureVersion> getVersions();

//	public void addVersion(FeatureVersion version);
//
//	public FeatureVersion getId(FeatureVersion version);

	public FeatureVersion addVersion(int version);

	public FeatureVersion getVersion(int version);


	public FeatureVersion getLatestVersion();

	public FeatureVersion createNewVersion();


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
