package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

/**
 * A concrete instance of a feature version that is has either a positive or a negative sign.
 */
public interface FeatureInstance extends Persistable {

	public Feature getFeature();

	public FeatureVersion getFeatureVersion();

	public boolean getSign();


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);

}
