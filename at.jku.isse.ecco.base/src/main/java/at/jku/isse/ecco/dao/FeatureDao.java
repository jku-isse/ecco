package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.Optional;
import java.util.Set;

/**
 * A data access object that handles {@link Feature} entities.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CorePlugin#EXTENSION_POINT_DAL}.
 *
 * @author Hannes Thaller
 * @version 1.0
 * @see Feature Feature
 */
public interface FeatureDao extends GenericDao<Feature> {

	/**
	 * Checks whether the given feature name is persistent.
	 *
	 * @param featureName that should exist in the database
	 * @return True if there exists a feature with the given name, false
	 * otherwise.
	 */
	boolean exists(String featureName);

	/**
	 * Returns an optional that might contain a feature which fits the given properties.
	 *
	 * @param featureName of the feature
	 * @param version     of the feature
	 * @return Either the stored instance that matches the search options or nothing.
	 */
	Optional<FeatureVersion> find(String featureName, int version);

	/**
	 * Loads all features from the storage.
	 *
	 * @return All stored features.
	 */
	Set<Feature> loadAllFeatures();

	/**
	 * Returns all versions of the feature.
	 *
	 * @param featureName of the feature from which all versions should be retrieved
	 * @return All versions of the given features.
	 */
	Optional<Set<FeatureVersion>> loadAllVersions(String featureName);

	/**
	 * Loads all the feature names that are stored in the system.
	 *
	 * @return All the feature names that are stored in the system.
	 */
	Set<String> loadAllFeatureNames();

	/**
	 * Removes all versions of the feature with the given featureName.
	 *
	 * @param featureName name of the feature
	 */
	void removeWithAllVersions(String featureName);

}
