package at.jku.isse.ecco.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Feature}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseFeature implements Feature {

	private String name = "";
	private String description = "";

	private List<FeatureVersion> versions = new ArrayList<FeatureVersion>();

	private int nextVersion = 0;

	public BaseFeature() {

	}

	public BaseFeature(String name) {
		this.setName(name);
	}

	@Override
	public List<FeatureVersion> getVersions() {
		return this.versions;
	}

	@Override
	public FeatureVersion addVersion(int version) {
		BaseFeatureVersion featureVersion = new BaseFeatureVersion(this, version);
		if (!this.versions.contains(featureVersion)) {
			this.versions.add(featureVersion);
			if (this.nextVersion <= version)
				this.nextVersion = version + 1;
			return featureVersion;
		}
		return null;
	}

	@Override
	public FeatureVersion getVersion(int version) {
		for (FeatureVersion featureVersion : this.versions) {
			if (featureVersion.getVersion() == version)
				return featureVersion;
		}
		return null;
	}

	@Override
	public FeatureVersion getLatestVersion() {
		return this.versions.get(this.versions.size() - 1);
	}

	@Override
	public FeatureVersion createNewVersion() {
		BaseFeatureVersion featureVersion = new BaseFeatureVersion(this, this.nextVersion);
		this.nextVersion++;
		this.versions.add(featureVersion);
		return featureVersion;
	}

//	@Override
//	public void addVersion(FeatureVersion version) {
//		if (!this.versions.contains(version))
//			this.versions.add(version);
//	}
//
//	@Override
//	public FeatureVersion getVersion(FeatureVersion version) {
//		for (FeatureVersion featureVersion : this.versions) {
//			if (featureVersion.equals(version))
//				return featureVersion;
//		}
//		return null;
//	}

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
		return Objects.hash(name);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof BaseFeature)) return false;

		final Feature other = (Feature) obj;
		return name.equals(other.getName());
	}

	@Override
	public String toString() {
		return this.name;
	}

}
