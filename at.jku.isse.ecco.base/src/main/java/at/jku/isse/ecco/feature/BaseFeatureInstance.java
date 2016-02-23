package at.jku.isse.ecco.feature;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TODO: consider making this a private class of BaseFeatuerVersion with a private constructor.
 */
public class BaseFeatureInstance implements FeatureInstance {

	private Feature feature;
	private FeatureVersion featureVersion;
	private boolean sign;

	protected BaseFeatureInstance() {
		this.feature = null;
		this.featureVersion = null;
		this.sign = false;
	}

	protected BaseFeatureInstance(Feature feature, FeatureVersion featureVersion, boolean sign) {
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
	public String toString() {
		return (this.sign ? "+" : "-") + this.feature.getName() + "." + this.featureVersion.getVersion();
	}

}
