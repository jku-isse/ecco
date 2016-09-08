package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;

import java.util.Set;

/**
 * A data access object that handles {@link Feature} entities.
 *
 * @author Hannes Thaller
 * @version 1.0
 * @see Feature Feature
 */
public interface FeatureDao extends EntityDao<Feature> {

	/**
	 * Loads all features from the storage.
	 *
	 * @return All stored features.
	 */
	Set<Feature> loadAllFeatures() throws EccoException;

}
