package at.jku.isse.ecco.storage.jackson.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link FeatureRevision}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class JacksonFeatureRevision implements FeatureRevision {

	public static final long serialVersionUID = 1L;


	@JsonBackReference
	private Feature feature;
	private String id;
	private String description;


	public JacksonFeatureRevision() {

	}

	public JacksonFeatureRevision(Feature feature, String id) {
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
		if (!(o instanceof JacksonFeatureRevision)) return false;

		JacksonFeatureRevision that = (JacksonFeatureRevision) o;

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
