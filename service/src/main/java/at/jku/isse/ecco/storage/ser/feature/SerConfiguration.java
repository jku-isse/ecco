package at.jku.isse.ecco.storage.ser.feature;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.HashSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The serialized (Ser storage) implementation of {@link Configuration}.
 */
public class SerConfiguration implements Configuration {

	public static final long serialVersionUID = 1L;


	private FeatureRevision[] featureRevisions;

	private String originalConfigString;

	public SerConfiguration(FeatureRevision[] featureRevisions) {
		checkNotNull(featureRevisions);
		this.featureRevisions = featureRevisions;
	}

	@Override
	public FeatureRevision[] getFeatureRevisions() {
		return this.featureRevisions;
	}

	@Override
	public Configuration getConfiguration() {
		return this;
	}

	@Override
	public void setOriginalConfigString(String originalConfigString) {
		this.originalConfigString = originalConfigString;
	}

	@Override
	public String getOriginalConfigString() {
		return this.originalConfigString;
	}

	@Override
	public void setFeatureRevisions(FeatureRevision[] featureRevisions) {
		this.featureRevisions = featureRevisions;
	}


	/**
	 * The same feature revisions, in whatever order: "A, B" is "B, A" (the order came from a hash
	 * set, and the same configuration committed again could become a second variant).
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SerConfiguration that = (SerConfiguration) o;
		return new HashSet<>(Arrays.asList(featureRevisions)).equals(new HashSet<>(Arrays.asList(that.featureRevisions)));
	}

	@Override
	public int hashCode() {
		return new HashSet<>(Arrays.asList(featureRevisions)).hashCode();
	}

	@Override
	public String toString() {
		return this.getConfigurationString();
	}

}
