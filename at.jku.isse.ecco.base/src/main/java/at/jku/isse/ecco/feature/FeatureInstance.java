package at.jku.isse.ecco.feature;

/**
 * NOTE: this class does not need to be persistable.
 */
public interface FeatureInstance { // extends Persistable {

	public Feature getFeature();

	public FeatureVersion getFeatureVersion();

	public boolean getSign();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);

}
