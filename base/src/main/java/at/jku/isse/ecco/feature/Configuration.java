package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.module.Module;

import java.util.Set;

/**
 * A configuration of a variant that consists of a set of feature instances.
 * A configuration defines a set of features. When committing a product, the configuration associated to the product
 * can be provided. The format of the configuration description must comply to the regular expression {@link #CONFIGURATION_STRING_REGULAR_EXPRESSION}.
 * The configuration syntax allows representing features in the following way:
 * <p>
 * <ul>
 *     <li>
 * </ul>
 * <p>
 */
public interface Configuration {//extends Collection<FeatureInstance> {

	public final String CONFIGURATION_STRING_REGULAR_EXPRESSION = "((\\+|\\-)?((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('?|(\\.([a-zA-Z0-9_-])+)?)(\\s*,\\s*(\\+|\\-)?((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('?|(\\.([a-zA-Z0-9_-])+)?))*)?";


	@Override
	public String toString();


	public Set<FeatureInstance> getFeatureInstances();

	public void addFeatureInstance(FeatureInstance featureInstance);

	public void removeFeatureInstance(FeatureInstance featureInstance);

	public Set<Module> computeModules(int maxOrder);

}
