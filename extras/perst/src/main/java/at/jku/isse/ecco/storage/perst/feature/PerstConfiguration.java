package at.jku.isse.ecco.storage.perst.feature;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.garret.perst.Persistent;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Configuration}.
 */
public class PerstConfiguration extends Persistent implements Configuration {

	private final FeatureRevision[] featureRevisions;


	public PerstConfiguration(FeatureRevision[] featureRevisions) {
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
		PerstConfiguration that = (PerstConfiguration) o;
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
