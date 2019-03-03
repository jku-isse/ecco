package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoFeatureRevision extends NeoEntity implements FeatureRevision {

    @Relationship("HAS")
	private Feature feature;

    @Property("id")
	private String id;

    @Property("description")
	private String description;

    public NeoFeatureRevision() {}

	public NeoFeatureRevision(Feature feature, String id) {
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
		if (!(o instanceof NeoFeatureRevision)) return false;

		NeoFeatureRevision that = (NeoFeatureRevision) o;

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
