package at.jku.isse.ecco.feature;

import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Feature}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstFeature extends Persistent implements Feature {

	private String id = "";
	private String name = "";
	private String description = "";

	private List<PerstFeatureVersion> versions = new ArrayList<>();

//	private int nextVersion = 0;

	public PerstFeature() {

	}

	public PerstFeature(String name) {
		this(UUID.randomUUID().toString(), name, "");
	}

	public PerstFeature(String id, String name, String description) {
		checkNotNull(id);
		checkNotNull(name);
		checkNotNull(description);

		this.id = id;
		this.name = name;
		this.description = description;
	}

	@Override
	public List<PerstFeatureVersion> getVersions() {
		return this.versions;
	}

	@Override
	public FeatureVersion addVersion(String id) {
		PerstFeatureVersion featureVersion = new PerstFeatureVersion(this, id);
		if (!this.versions.contains(featureVersion)) {
			this.versions.add(featureVersion);
//			if (this.nextVersion <= version)
//				this.nextVersion = version + 1;
			return featureVersion;
		}
		return null;
	}

	@Override
	public FeatureVersion getVersion(String id) {
		for (FeatureVersion featureVersion : this.versions) {
			if (featureVersion.getId().equals(id))
				return featureVersion;
		}
		return null;
	}

	@Override
	public FeatureVersion getLatestVersion() {
		if (this.versions.isEmpty())
			return null;
		return this.versions.get(this.versions.size() - 1);
	}

	@Override
	public FeatureVersion createNewVersion() {
		PerstFeatureVersion featureVersion = new PerstFeatureVersion(this, UUID.randomUUID().toString());
//		this.nextVersion++;
		this.versions.add(featureVersion);
		return featureVersion;
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
	public int hashCode() {
		return Objects.hash(this.id);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (!(o instanceof Feature)) return false;

		final Feature other = (Feature) o;

		return this.id.equals(other.getId());
	}

	@Override
	public String getId() {
		return this.name;
	}

	@Override
	public String toString() {
		//return this.name + "(" + this.id + ")";
		return this.name;
	}

}
