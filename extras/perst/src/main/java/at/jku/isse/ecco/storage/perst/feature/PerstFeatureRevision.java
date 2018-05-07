package at.jku.isse.ecco.storage.perst.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link FeatureRevision}.
 */
public class PerstFeatureRevision extends Persistent implements FeatureRevision {

	private Feature feature;
	private String id;
	private String description;


	public PerstFeatureRevision(Feature feature, String id) {
		checkNotNull(feature);
		checkNotNull(id);
		this.feature = feature;
		this.id = id;
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PerstFeatureRevision)) return false;

		PerstFeatureRevision that = (PerstFeatureRevision) o;

		if (!getFeature().equals(that.getFeature())) return false;
		return getId().equals(that.getId());

	}

	@Override
	public int hashCode() {
		int result = getFeature().hashCode();
		result = 31 * result + getId().hashCode();
		return result;
	}

	@Override
	public String toString() {
		return this.getFeatureRevisionString();
	}

}
