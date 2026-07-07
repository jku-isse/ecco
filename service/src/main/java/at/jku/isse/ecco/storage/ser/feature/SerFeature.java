package at.jku.isse.ecco.storage.ser.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Feature}.
 */
public class SerFeature implements Feature {

	public static final long serialVersionUID = 1L;


	private String id;
	private String name;
	private String description;
	private Collection<SerFeatureRevision> revisions;
	private SerFeatureRevision latest;


	public SerFeature(String id, String name) {
		checkNotNull(id);
		checkNotNull(name);
		this.id = id;
		this.name = name;
		this.description = "";
		this.revisions = new ArrayList<>();
		this.latest = null;
	}


	@Override
	public Collection<SerFeatureRevision> getRevisions() {
		return Collections.unmodifiableCollection(this.revisions);
	}

	@Override
	public SerFeatureRevision addRevision(String id) {
		SerFeatureRevision featureRevision = new SerFeatureRevision(this, id);
		if (!this.revisions.contains(featureRevision)) {
			this.revisions.add(featureRevision);
			this.latest = featureRevision;
			return featureRevision;
		}
		return null;
	}

	@Override
	public SerFeatureRevision getRevision(String id) {
		for (SerFeatureRevision featureVersion : this.revisions) {
			if (featureVersion.getId().equals(id))
				return featureVersion;
		}
		return null;
	}

	@Override
	public FeatureRevision getOrphanedRevision(String id) {
		SerFeatureRevision featureRevision = this.getRevision(id);
		if (featureRevision == null) {
			featureRevision = new SerFeatureRevision(this, id);
		}
		return featureRevision;
	}

	@Override
	public SerFeatureRevision getLatestRevision() {
		return this.latest;
	}

	@Override
	public Feature feature(String name) {
		this.name = name;
		return this;
	}

	@Override
	public String getId() {
		return this.id;
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
		if (!(obj instanceof SerFeature)) return false;

		final Feature other = (Feature) obj;
		return this.id.equals(other.getId());
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
