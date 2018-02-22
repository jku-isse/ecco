package at.jku.isse.ecco.storage.perst.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link FeatureInstance}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstFeatureInstance extends Persistent implements FeatureInstance {

	private Feature feature;
	private FeatureRevision featureVersion;
	private boolean sign;

	protected PerstFeatureInstance() {
		this.feature = null;
		this.featureVersion = null;
		this.sign = false;
	}

	public PerstFeatureInstance(Feature feature, FeatureRevision featureVersion, boolean sign) {
		checkNotNull(feature);
		checkNotNull(featureVersion);

		this.feature = feature;
		this.featureVersion = featureVersion;
		this.sign = sign;
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public FeatureRevision getFeatureVersion() {
		return this.featureVersion;
	}

	@Override
	public boolean getSign() {
		return this.sign;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (feature != null ? feature.hashCode() : 0);
		result = 31 * result + (featureVersion != null ? featureVersion.hashCode() : 0);
		result = 31 * result + (sign ? 1 : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof FeatureInstance)) return false;

		final FeatureInstance that = (FeatureInstance) o;

		if (sign != that.getSign()) return false;
		if (feature != null ? !feature.equals(that.getFeature()) : that.getFeature() != null) return false;
		return !(featureVersion != null ? !featureVersion.equals(that.getFeatureVersion()) : that.getFeatureVersion() != null);
	}

	@Override
	public String toString() {
		return (this.sign ? "+" : "-") + this.feature.getName() + "." + this.featureVersion.getId();
	}

}
