package at.jku.isse.ecco.storage.mem.feature;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Feature}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseFeature implements Feature {

	private String id = "";
	private String name = "";
	private String description = "";

	private List<FeatureVersion> versions = new ArrayList<FeatureVersion>();

//	private int nextVersion = 0;

//	public BaseFeature() {
//		this(UUID.randomUUID().toString(), "", "");
//	}
//
//	public BaseFeature(String name) {
//		this(UUID.randomUUID().toString(), name, "");
//	}
//
//	public BaseFeature(String name, String description) {
//		this(UUID.randomUUID().toString(), name, description);
//	}

	public BaseFeature(String id, String name, String description) {
		checkNotNull(id);
		checkNotNull(name);
		checkNotNull(description);

		this.id = id;
		this.name = name;
		this.description = description;
	}

	@Override
	public List<FeatureVersion> getVersions() {
		return this.versions;
	}

	@Override
	public FeatureVersion addVersion(String id) {
		BaseFeatureVersion featureVersion = new BaseFeatureVersion(this, id);
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
		BaseFeatureVersion featureVersion = new BaseFeatureVersion(this, UUID.randomUUID().toString());
//		this.nextVersion++;
		this.versions.add(featureVersion);
		return featureVersion;
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
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof BaseFeature)) return false;

		final Feature other = (Feature) obj;
		return this.id.equals(other.getId());
	}


	@Override
	public String toString() {
		//return this.name + "(" + this.id + ")";
		return this.name;
	}

}
