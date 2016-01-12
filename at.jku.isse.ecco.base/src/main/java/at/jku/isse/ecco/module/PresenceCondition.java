package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureVersion;

/**
 * A presence condition that determines whether artifacts are present or not in given configuration.
 */
public interface PresenceCondition {

	/**
	 * @param configuration The configuration against which the presence condition should be checked.
	 * @return True if the presence condition holds for configuration, false otherwise.
	 */
	public boolean holds(Configuration configuration);

	/**
	 * Returns true if the presence condition is empty, i.e. it always holds.
	 */
	public boolean isEmpty();

	/**
	 * Slices this presence condition against another presence condition and returns a new presence condition.
	 */
	public PresenceCondition slice(PresenceCondition other);

	public void addFeatureVersion(FeatureVersion newFeatureVersion);

}
