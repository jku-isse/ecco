package at.jku.isse.ecco.storage.mem.feature;

import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.storage.mem.module.BaseModule;
import at.jku.isse.ecco.storage.mem.module.BaseModuleFeature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Memory implementation of {@link Configuration}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseConfiguration implements Configuration, ConfigurationOperator.ConfigurationOperand {

	private transient ConfigurationOperator operator = new ConfigurationOperator(this);


	private final Set<FeatureInstance> featureInstances = new HashSet<>();

	@Override
	public Set<FeatureInstance> getFeatureInstances() {
		return this.featureInstances;
	}

	@Override
	public void addFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.add(featureInstance);
	}

	@Override
	public void removeFeatureInstance(FeatureInstance featureInstance) {
		this.featureInstances.remove(featureInstance);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + featureInstances.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Configuration)) return false;

		final Configuration that = (Configuration) o;

		return this.featureInstances.equals(that.getFeatureInstances());
	}

	@Override
	public String toString() {
		return this.operator.toString();
	}


	// operations

	public Module createModule() {
		return new BaseModule();
	}

	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
	}

	public ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return this.createModuleFeature(feature, new ArrayList<>(), sign);
	}

	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return new BaseModuleFeature(feature, featureVersions, sign);
	}

	@Override
	public Set<Module> computeModules(int maxOrder) {
		return this.operator.computeModules(maxOrder);
	}

}
