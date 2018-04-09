package at.jku.isse.ecco.storage.mem.feature;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Configuration}.
 */
public class MemConfiguration implements Configuration {

	public static final long serialVersionUID = 1L;


	private final FeatureRevision[] featureRevisions;


	public MemConfiguration(FeatureRevision[] featureRevisions) {
		checkNotNull(featureRevisions);
		this.featureRevisions = featureRevisions;
	}


	@Override
	public FeatureRevision[] getFeatureRevisions() {
		return this.featureRevisions;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MemConfiguration that = (MemConfiguration) o;
		return Arrays.equals(featureRevisions, that.featureRevisions);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(featureRevisions);
	}

	@Override
	public String toString() {
		return this.getConfigurationString();
	}

}
