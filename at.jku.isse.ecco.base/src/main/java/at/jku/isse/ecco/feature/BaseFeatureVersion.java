package at.jku.isse.ecco.feature;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseFeatureVersion implements FeatureVersion {

	private Feature feature;
	private int version;

	public BaseFeatureVersion() {
		this.feature = null;
		this.version = FeatureVersion.ANY;
	}

	public BaseFeatureVersion(Feature feature, int version) {
		checkNotNull(feature);

		this.feature = feature;
		this.version = version;

		this.feature.addVersion(this);
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public int getVersion() {
		return this.version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BaseFeatureVersion that = (BaseFeatureVersion) o;

		if (version != that.version) return false;
		return feature.equals(that.feature);

	}

	@Override
	public int hashCode() {
		int result = feature.hashCode();
		result = 31 * result + version;
		return result;
	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.version;
	}

}
