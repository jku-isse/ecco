package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public interface ModuleRevision extends Persistable {

	/**
	 * Returns a direct reference to the instance of the array of positive feature revisions in this module revision.
	 * DO NOT MODIFY THIS ARRAY!!!
	 * TREAT THE RETURNED ARRAY AS CONST!!!
	 *
	 * @return The array of positive feature revisions in this module revision.
	 */
	public FeatureRevision[] getPos();

	/**
	 * Returns a direct reference to the instance of the array of negative features in this module revision.
	 * DO NOT MODIFY THIS ARRAY!!!
	 * TREAT THE RETURNED ARRAY AS CONST!!!
	 *
	 * @return The array of negative features in this module revision.
	 */
	public Feature[] getNeg();


	public default void verify(FeatureRevision[] pos, Feature[] neg) {
		checkNotNull(pos);
		checkNotNull(neg);

		for (FeatureRevision fr : pos) {
			if (fr == null)
				throw new EccoException("ERROR: A feature revision in pos is null.");
		}
		for (Feature f : neg) {
			if (f == null)
				throw new EccoException("ERROR: A feature in neg is null.");
		}

		for (int i = 0; i < pos.length; i++) {
			for (int j = i + 1; j < pos.length; j++) {
				if (pos[i].equals(pos[j]))
					throw new EccoException("ERROR: The same feature revision is contained twice in pos.");
			}
			for (int j = i + 1; j < neg.length; j++) {
				if (pos[i].getFeature().equals(neg[j]))
					throw new EccoException("ERROR: A feature that has a revision in pos is also in neg.");
			}
		}
		for (int i = 0; i < neg.length; i++) {
			for (int j = i + 1; j < neg.length; j++) {
				if (neg[i].equals(neg[j]))
					throw new EccoException("ERROR: The same feature is contained twice in neg.");
			}
		}
	}


	public int getCount();

	public void setCount(int count);

	public void incCount();

	public void incCount(int count);


	public Module getModule();


	public default int getOrder() {
		return this.getPos().length + this.getNeg().length - 1;
	}


	/**
	 * Checks if this module revision holds on the given configuration.
	 *
	 * @param configuration The configuration to check against.
	 * @return True if this module is contained (i.e. holds) in the given configuration.
	 */
	public default boolean holds(Configuration configuration) {
		// check if all positive features revisiosn of the module are contained in the configuration
		for (FeatureRevision featureRevision : this.getPos()) {
			boolean found = false;
			for (FeatureRevision confFeatureRevision : configuration.getFeatureRevisions()) {
				if (confFeatureRevision.equals(featureRevision)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		// check if no negative features of the module are contained in the configuration
		for (Feature feature : this.getNeg()) {
			for (FeatureRevision confFeatureRevision : configuration.getFeatureRevisions()) {
				if (confFeatureRevision.getFeature().equals(feature)) {
					return false;
				}
			}
		}
		return true;
	}

	public default boolean implies(ModuleRevision other) {
		// check that all positive features of this are contained in other
		for (FeatureRevision thisFeatureRevision : this.getPos()) {
			boolean found = false;
			for (FeatureRevision otherFeatureRevision : other.getPos()) {
				if (thisFeatureRevision.equals(otherFeatureRevision)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		// check that none of the negative features of this are contained in other
		for (FeatureRevision thisFeatureRevision : this.getPos()) {
			for (Feature otherFeature : other.getNeg()) {
				if (thisFeatureRevision.getFeature().equals(otherFeature)) {
					return false;
				}
			}
		}
		return true;
	}


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);


	public default String getModuleRevisionString() {
		String moduleRevisionString = Arrays.stream(this.getPos()).map(FeatureRevision::toString).collect(Collectors.joining(", "));
		if (this.getNeg().length > 0)
			moduleRevisionString += ", " + Arrays.stream(this.getNeg()).map(feature -> "!" + feature.toString()).collect(Collectors.joining(", "));

		return "d^" + this.getOrder() + "(" + moduleRevisionString + ")";
	}

	@Override
	public String toString();

}
