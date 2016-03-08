package at.jku.isse.ecco.feature;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link FeatureInstance}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseFeatureInstance implements FeatureInstance {

	private Feature feature;
	private FeatureVersion featureVersion;
	private boolean sign;

	public BaseFeatureInstance(Feature feature, FeatureVersion featureVersion, boolean sign) {
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
	public FeatureVersion getFeatureVersion() {
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
		return (this.sign ? "+" : "-") + this.feature.getName() + "." + this.featureVersion.getVersion();
	}

}
