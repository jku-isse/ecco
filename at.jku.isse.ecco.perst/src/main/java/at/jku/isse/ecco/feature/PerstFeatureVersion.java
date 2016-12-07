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
	private String id;
	private String description;

	protected PerstFeatureVersion() {
		this.feature = null;
	}

	public PerstFeatureVersion(Feature feature, String id) {
		checkNotNull(feature);

		this.feature = feature;
		this.id = id;

//		this.feature.addVersion(this);
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public String getId() {
		return this.id;
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
		int result = getFeature().hashCode();
		result = 31 * result + getId().hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PerstFeatureVersion)) return false;

		PerstFeatureVersion that = (PerstFeatureVersion) o;

		if (!getFeature().equals(that.getFeature())) return false;
		return getId().equals(that.getId());

	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.getId();
	}

}
