package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.module.Module;

import java.util.Set;

public interface Configuration {

	//public final String CONFIGURATION_STRING_REGULAR_EXPRESSION = "(\\+|\\-)?[a-zA-Z0-9]+('?|(\\.((\\+|\\-)?[0-9])+)?)(\\s*,\\s*(\\+|\\-)?[a-zA-Z0-9]+('?|(\\.((\\+|\\-)?[0-9])+)?))*";
	public final String CONFIGURATION_STRING_REGULAR_EXPRESSION = "((\\+|\\-)?[a-zA-Z0-9_-]+('?|(\\.((\\+|\\-)?[0-9])+)?)(\\s*,\\s*(\\+|\\-)?[a-zA-Z0-9_-]+('?|(\\.((\\+|\\-)?[0-9])+)?))*)?";


	@Override
	public String toString();


	public Set<FeatureInstance> getFeatureInstances();

	public void addFeatureInstance(FeatureInstance featureInstance);

	public void removeFeatureInstance(FeatureInstance featureInstance);

	public Set<Module> computeModules(int maxOrder);

}
