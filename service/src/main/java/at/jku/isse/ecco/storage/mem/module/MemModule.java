package at.jku.isse.ecco.storage.mem.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Memory implementation of {@link Feature}.
 */
public class MemModule implements Module {

	private Feature[] pos;
	private Feature[] neg;

	public MemModule(Feature[] pos, Feature[] neg) {
		this.pos = pos;
		this.neg = neg;
	}

	@Override
	public boolean holds(Configuration configuration) {
		/**
		 * A module holds in a configuration when all the module's features are contained in the configuration.
		 */

		Set<FeatureInstance> featureInstances = configuration.getFeatureInstances();
		for (ModuleFeature mf : this) {

			boolean atLeastOneVersionMatched = false;
			for (FeatureRevision fv : mf) {
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

		MemModule that = (MemModule) o;

		return this.moduleFeatures.equals(that.moduleFeatures);
	}

	@Override
	public String toString() {
		String result = this.stream().map((ModuleFeature mf) -> {
			return mf.toString();
		}).collect(Collectors.joining(", "));

		return "d^" + this.getOrder() + "(" + result + ")";
	}

}
