package at.jku.isse.ecco.feature;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link FeatureVersion}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseFeatureVersion implements FeatureVersion {

	private Feature feature;
	private int version;
	private String description;

	public BaseFeatureVersion(Feature feature, int version) {
		checkNotNull(feature);

		this.feature = feature;
		this.version = version;

//		this.feature.addVersion(this);
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public int getId() {
		return this.version;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public int hashCode() {
		int result = feature.hashCode();
		result = 31 * result + version;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof FeatureVersion)) return false;

		final FeatureVersion that = (FeatureVersion) o;

		if (version != that.getId()) return false;
		return feature.equals(that.getFeature());

	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.version;
	}

}
