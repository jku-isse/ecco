package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import javax.management.relation.Relation;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoFeature extends NeoEntity implements Feature {

    @Property("featureId")
	private String featureId;

    @Property("name")
	private String name;

    @Property("description")
	private String description = "";

    @Relationship("hasRevisionFt")
	public ArrayList<NeoFeatureRevision> revisions = new ArrayList<>();

	public NeoFeature() {}

	public NeoFeature(String id, String name) {
		checkNotNull(id);
		checkNotNull(name);
		this.featureId = id;
		this.name = name;
	}


	@Override
	public ArrayList<NeoFeatureRevision> getRevisions() {
		return this.revisions;
	}

	@Override
	public NeoFeatureRevision addRevision(String id) {
		NeoFeatureRevision featureRevision = new NeoFeatureRevision(this, id);
		if (!this.revisions.contains(featureRevision)) {
			this.revisions.add(featureRevision);
			return featureRevision;
		}
		return null;
	}

	@Override
	public NeoFeatureRevision getRevision(String id) {
		if (this.revisions != null) {
			for (NeoFeatureRevision featureVersion : this.revisions) {
				if (featureVersion.getId().equals(id))
					return featureVersion;
			}
		}
		return null;
	}

	@Override
	public NeoFeatureRevision getLatestRevision() {
		return this.revisions.get(revisions.size() - 1);
	}

	@Override
	public String getId() {
		return this.featureId;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		this.name = name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(final String description) {
		checkNotNull(description);

		this.description = description;
	}


	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NeoFeature)) return false;

		final Feature other = (Feature) obj;
		return this.featureId.equals(other.getId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public String toString() {
		return this.getFeatureString();
	}

}
