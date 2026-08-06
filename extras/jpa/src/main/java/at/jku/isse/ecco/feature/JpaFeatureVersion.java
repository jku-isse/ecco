package at.jku.isse.ecco.feature;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.io.Serializable;

import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaFeatureVersion implements FeatureVersion, Serializable {

	@Id
	@ManyToOne(targetEntity = JpaFeature.class)
	private Feature feature;
	@Id
	private int version;

	public JpaFeatureVersion() {
		this.feature = null;
//		this.version = FeatureVersion.ANY;
	}

	public JpaFeatureVersion(Feature feature, int version) {
		checkNotNull(feature);

		this.feature = feature;
		this.version = version;

		this.feature.addVersion(this);
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public int getId() {
		return this.version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JpaFeatureVersion that = (JpaFeatureVersion) o;

		if (version != that.version) return false;
		return feature.equals(that.feature);

	}

	@Override
	public int hashCode() {
		int result = feature.hashCode();
		result = 31 * result + version;
		return result;
	}

	@Override
	public String toString() {
		return this.feature.getName() + "." + this.version;
	}

}
