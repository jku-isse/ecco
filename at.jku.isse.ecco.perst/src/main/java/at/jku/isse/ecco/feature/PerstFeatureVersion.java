package at.jku.isse.ecco.feature;

import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link FeatureVersion}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstFeatureVersion extends Persistent implements FeatureVersion {

	private Feature feature;
	private int version;

	protected PerstFeatureVersion() {
		this.feature = null;
		this.version = FeatureVersion.ANY;
	}

	public PerstFeatureVersion(Feature feature, int version) {
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

		if (version != that.getVersion()) return false;
		return feature.equals(that.getFeature());

	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.version;
	}

}
