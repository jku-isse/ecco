package at.jku.isse.ecco.feature;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.io.Serializable;

import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaFeatureInstance implements FeatureInstance, Serializable {

	@Id
	@ManyToOne(targetEntity = JpaFeature.class)
	private Feature feature;
	@Id
	@ManyToOne(targetEntity = JpaFeatureVersion.class)
	private FeatureVersion featureVersion;
	@Id
	private boolean sign;

	public JpaFeatureInstance(Feature feature, FeatureVersion featureVersion, boolean sign) {
		checkNotNull(feature);
		checkNotNull(featureVersion);

		this.feature = feature;
		this.featureVersion = featureVersion;
		this.sign = sign;
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public FeatureVersion getFeatureVersion() {
		return this.featureVersion;
	}

	@Override
	public boolean getSign() {
		return this.sign;
	}

	@Override
	public String toString() {
		return (this.sign ? "+" : "-") + this.feature.getName() + "." + this.featureVersion.getId();
	}

}
