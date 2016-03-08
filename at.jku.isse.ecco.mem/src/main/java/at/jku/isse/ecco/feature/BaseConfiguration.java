package at.jku.isse.ecco.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Memory implementation of {@link Configuration}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
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
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + featureInstances.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Configuration)) return false;

		final Configuration that = (Configuration) o;

		return this.featureInstances.equals(that.getFeatureInstances());

	}

	@Override
	public String toString() {
		return this.featureInstances.stream().map(fi -> fi.toString()).collect(Collectors.joining(", "));
	}

}
