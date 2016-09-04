package at.jku.isse.ecco.feature;

import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

	private String name = "";
	private String description = "";

	private List<FeatureVersion> versions = new ArrayList<FeatureVersion>();

	private int nextVersion = 0;

	public PerstFeature() {

	}

	public PerstFeature(String name) {
		this.setName(name);
	}

	@Override
	public List<FeatureVersion> getVersions() {
		return this.versions;
	}

	@Override
	public FeatureVersion addVersion(int version) {
		PerstFeatureVersion featureVersion = new PerstFeatureVersion(this, version);
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
			if (featureVersion.getId() == version)
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
		PerstFeatureVersion featureVersion = new PerstFeatureVersion(this, this.nextVersion);
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
//	public FeatureVersion getId(FeatureVersion version) {
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
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (!(o instanceof Feature)) return false;

		final Feature other = (Feature) o;

		return this.name.equals(other.getName());
	}

	@Override
	public String toString() {
		return this.name;
	}

}
