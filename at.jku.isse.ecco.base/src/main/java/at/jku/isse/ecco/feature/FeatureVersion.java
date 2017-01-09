package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

/**
 * Represents a version of a feature.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public interface FeatureVersion extends Persistable {

//	public static final int NEWEST = -1;
//	public static final int ANY = -2;


	public FeatureInstance getPositiveInstance();

	public FeatureInstance getNegativeInstance();

	public FeatureInstance getInstance(boolean sign);


	/**
	 * Returns the feature belonging to this version.
	 *
	 * @return The feature belonging to this version.
	 */
	public Feature getFeature();

	public String getId();

	public String getDescription();

	public void setDescription(String description);


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);

}
