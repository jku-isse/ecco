package at.jku.isse.ecco.storage.mem.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link FeatureVersion}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseFeatureVersion implements FeatureVersion {

	private Feature feature;
	private String id;
	private String description;

	private FeatureInstance pos;
	private FeatureInstance neg;

	public BaseFeatureVersion(Feature feature, String id) {
		checkNotNull(feature);
		checkNotNull(id);

		this.feature = feature;
		this.id = id;

		this.pos = new BaseFeatureInstance(feature, this, true);
		this.neg = new BaseFeatureInstance(feature, this, false);

//		this.feature.addVersion(this);
	}


	@Override
	public FeatureInstance getPositiveInstance() {
		return this.pos;
	}

	@Override
	public FeatureInstance getNegativeInstance() {
		return this.neg;
	}

	@Override
	public FeatureInstance getInstance(boolean sign) {
		if (sign)
			return this.pos;
		else
			return this.neg;
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
		if (!(o instanceof BaseFeatureVersion)) return false;

		BaseFeatureVersion that = (BaseFeatureVersion) o;

		if (!getFeature().equals(that.getFeature())) return false;
		return getId().equals(that.getId());

	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.getId();
	}

}
