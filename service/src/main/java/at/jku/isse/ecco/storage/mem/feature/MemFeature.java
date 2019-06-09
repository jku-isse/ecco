package at.jku.isse.ecco.storage.mem.feature;

import at.jku.isse.ecco.feature.Feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Feature}.
 */
public class MemFeature implements Feature {

	public static final long serialVersionUID = 1L;


	private String id;
	private String name;
	private String description;
	private Collection<MemFeatureRevision> revisions;
	private MemFeatureRevision latest;


	public MemFeature(String id, String name) {
		checkNotNull(id);
		checkNotNull(name);
		this.id = id;
		this.name = name;
		this.description = "";
		this.revisions = new ArrayList<>();
		this.latest = null;
	}


	@Override
	public Collection<MemFeatureRevision> getRevisions() {
		return Collections.unmodifiableCollection(this.revisions);
	}

	@Override
	public MemFeatureRevision addRevision(String id) {
		MemFeatureRevision featureRevision = new MemFeatureRevision(this, id);
		if (!this.revisions.contains(featureRevision)) {
			this.revisions.add(featureRevision);
			this.latest = featureRevision;
			return featureRevision;
		}
		return null;
	}

	@Override
	public MemFeatureRevision getRevision(String id) {
		for (MemFeatureRevision featureVersion : this.revisions) {
			if (featureVersion.getId().equals(id))
				return featureVersion;
		}
		return null;
	}

	@Override
	public MemFeatureRevision getLatestRevision() {
		return this.latest;
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
		if (!(obj instanceof MemFeature)) return false;

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
