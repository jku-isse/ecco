package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class JpaModule implements Module, Serializable {

	@Id
	@OneToMany(targetEntity = JpaModuleFeature.class, cascade = CascadeType.ALL)
	protected Set<ModuleFeature> moduleFeatures = new HashSet<ModuleFeature>();

	public JpaModule() {
		this.moduleFeatures = new HashSet<ModuleFeature>();
	}

	public JpaModule(JpaModule module) {
		this.moduleFeatures = new HashSet<ModuleFeature>(module.moduleFeatures);
	}

	@Override
	public boolean holds(Configuration configuration) {
		/**
		 * A module holds in a configuration when all the module's features are contained in the configuration.
		 */

		Set<FeatureInstance> featureInstances = configuration.getFeatureInstances();
		for (ModuleFeature mf : this) {

			boolean atLeastOneVersionMatched = false;
			for (FeatureVersion fv : mf) {
				for (FeatureInstance fi : featureInstances) {
					if (fi.getFeatureVersion().equals(fv) && fi.getSign() == mf.getSign()) {
						atLeastOneVersionMatched = true;
						break;
					}
				}
				if (atLeastOneVersionMatched)
					break;
			}

			if (!atLeastOneVersionMatched)
				return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return this.moduleFeatures.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JpaModule that = (JpaModule) o;

		return this.moduleFeatures.equals(that.moduleFeatures);
	}

	@Override
	public String toString() {
		String result = this.stream().map((ModuleFeature mf) -> {
			return mf.toString();
		}).collect(Collectors.joining(", "));

		return "d^" + this.getOrder() + "(" + result + ")";
	}


	// # SET ####################################################

	@Override
	public int size() {
		return this.moduleFeatures.size();
	}

	@Override
	public boolean isEmpty() {
		return this.moduleFeatures.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return this.moduleFeatures.contains(o);
	}

	@Override
	public Iterator<ModuleFeature> iterator() {
		return this.moduleFeatures.iterator();
	}

	@Override
	public Object[] toArray() {
		return this.moduleFeatures.toArray();
	}

	@Override
	public <T> T[] toArray(T[] ts) {
		return this.moduleFeatures.<T>toArray(ts);
	}

	@Override
	public boolean add(ModuleFeature moduleFeature) {
		return this.moduleFeatures.add(moduleFeature);
	}

	@Override
	public boolean remove(Object o) {
		return this.moduleFeatures.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> collection) {
		return this.moduleFeatures.containsAll(collection);
	}

	@Override
	public boolean addAll(Collection<? extends ModuleFeature> collection) {
		return this.moduleFeatures.addAll(collection);
	}

	@Override
	public boolean retainAll(Collection<?> collection) {
		return this.moduleFeatures.retainAll(collection);
	}

	@Override
	public boolean removeAll(Collection<?> collection) {
		return this.moduleFeatures.removeAll(collection);
	}

	@Override
	public void clear() {
		this.moduleFeatures.clear();
	}

}
