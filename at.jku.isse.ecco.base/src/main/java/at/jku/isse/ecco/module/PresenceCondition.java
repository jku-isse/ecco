package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.Set;

/**
 * A presence condition that determines whether artifacts are present or not in given configuration.
 */
public interface PresenceCondition {

	public void merge(PresenceCondition other);


	public void addFeatureInstance(FeatureInstance featureInstance);

	public void addFeatureInstance(FeatureInstance featureInstance, int maxOrder);

	public void addFeatureVersion(FeatureVersion featureVersion);


	public void removeFeatureVersion(FeatureVersion featureVersion);


	public void removeModules(Set<Module> modules);


	public Set<Module> getMinModules();

	public Set<Module> getMaxModules();

	public Set<Module> getNotModules();

	public Set<Module> getAllModules();


	/**
	 * Slices this presence condition against another presence condition and returns a new presence condition.
	 */
	public PresenceCondition slice(PresenceCondition other) throws EccoException;

	/**
	 * Returns true if the presence condition is empty, i.e. it always holds.
	 */
	public boolean isEmpty();

	/**
	 * Checks if this presence condition holds in the given configuration.
	 *
	 * @param configuration The configuration against which the presence condition should be checked.
	 * @return True if the presence condition holds for configuration, false otherwise.
	 */
	public boolean holds(Configuration configuration);

	@Override
	public boolean equals(Object other);

}
