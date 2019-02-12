package at.jku.isse.ecco.storage.neo4j.domain.feature;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.neo4j.domain.NeoEntity;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoConfiguration extends NeoEntity implements Configuration {

    @Relationship("HAS")
	private final FeatureRevision[] featureRevisions;


	public NeoConfiguration(FeatureRevision[] featureRevisions) {
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
		NeoConfiguration that = (NeoConfiguration) o;
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
