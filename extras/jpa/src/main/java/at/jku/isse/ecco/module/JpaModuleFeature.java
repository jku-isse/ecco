package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.feature.JpaFeature;
import at.jku.isse.ecco.feature.JpaFeatureVersion;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class JpaModuleFeature implements ModuleFeature, Serializable {

	@Id
	@OneToMany(targetEntity = JpaFeatureVersion.class, cascade = CascadeType.ALL)
	protected Set<FeatureVersion> featureVersions = new HashSet<FeatureVersion>();

	@Id
	@ManyToOne(targetEntity = JpaFeature.class)
	private Feature feature = new JpaFeature();
	@Id
	private boolean sign;

	public JpaModuleFeature() {
		this.feature = null;
		this.sign = true;

		this.featureVersions = new HashSet<FeatureVersion>();
	}

	public JpaModuleFeature(Feature feature, boolean sign) {
		this.feature = feature;
		this.sign = sign;

		this.featureVersions = new HashSet<FeatureVersion>();
	}

	public JpaModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		this.feature = feature;
		this.sign = sign;

		this.featureVersions = new HashSet<FeatureVersion>(featureVersions);
	}

	@Override
	public Feature getFeature() {
		return this.feature;
	}

	@Override
	public boolean getSign() {
		return this.sign;
	}

	@Override
	public int hashCode() {
		int result = featureVersions.hashCode();
		result = 31 * result + (sign ? 1 : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JpaModuleFeature that = (JpaModuleFeature) o;

		if (sign != that.sign) return false;
		return featureVersions.equals(that.featureVersions);
	}

	@Override
	public String toString() {
		String signString = this.sign ? "+" : "-";
		return signString + this.feature.toString() + ".{" + this.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.joining(",")) + "}";
	}


	// # SET ####################################################

	@Override
	public int size() {
		return this.featureVersions.size();
	}

	@Override
	public boolean isEmpty() {
		return this.featureVersions.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return this.featureVersions.contains(o);
	}

	@Override
	public Iterator<FeatureVersion> iterator() {
		return this.featureVersions.iterator();
	}

	@Override
	public Object[] toArray() {
		return this.featureVersions.toArray();
	}

	@Override
	public <T> T[] toArray(T[] ts) {
		return this.featureVersions.<T>toArray(ts);
	}

	@Override
	public boolean add(FeatureVersion moduleFeature) {
		return this.featureVersions.add(moduleFeature);
	}

	@Override
	public boolean remove(Object o) {
		return this.featureVersions.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> collection) {
		return this.featureVersions.containsAll(collection);
	}

	@Override
	public boolean addAll(Collection<? extends FeatureVersion> collection) {
		return this.featureVersions.addAll(collection);
	}

	@Override
	public boolean retainAll(Collection<?> collection) {
		return this.featureVersions.retainAll(collection);
	}

	@Override
	public boolean removeAll(Collection<?> collection) {
		return this.featureVersions.removeAll(collection);
	}

	@Override
	public void clear() {
		this.featureVersions.clear();
	}

}
