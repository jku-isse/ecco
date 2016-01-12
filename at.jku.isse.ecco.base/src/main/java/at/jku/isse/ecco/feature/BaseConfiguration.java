package at.jku.isse.ecco.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseConfiguration implements Configuration {

	private final Set<FeatureInstance> featureInstances = new HashSet<>();

	@Override
	public Set<FeatureInstance> getFeatureInstances() {
		return this.featureInstances;
	}

	@Override
	public void addFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.add(featureInstance);
	}

	@Override
	public void removeFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.remove(featureInstance);
	}

	@Override
	public String toString() {
		return this.featureInstances.stream().map(fi -> fi.toString()).collect(Collectors.joining(", "));
	}

}
