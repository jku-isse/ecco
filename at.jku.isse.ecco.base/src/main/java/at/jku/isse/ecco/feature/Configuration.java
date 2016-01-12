package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.feature.FeatureInstance;

import java.util.Set;

public interface Configuration {

	public Set<FeatureInstance> getFeatureInstances();

	public void addFeatureInstance(FeatureInstance featureInstance);

	public void removeFeatureInstance(FeatureInstance featureInstance);

}
