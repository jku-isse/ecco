package at.jku.isse.ecco.feature;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class JpaConfiguration implements Configuration, Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@OneToMany(targetEntity = JpaFeatureInstance.class, fetch = FetchType.EAGER)
	private final Set<FeatureInstance> featureInstances = new HashSet<>();

	@Override
	public Set<FeatureInstance> getFeatureInstances() {
		return this.featureInstances;
	}

	@Override
	public void addFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.add(featureInstance);
	}

	@Override
	public void removeFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.remove(featureInstance);
	}

	@Override
	public String toString() {
		return this.featureInstances.stream().map(fi -> fi.toString()).collect(Collectors.joining(", "));
	}

}
