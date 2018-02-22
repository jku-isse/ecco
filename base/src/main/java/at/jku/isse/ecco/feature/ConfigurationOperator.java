package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigurationOperator {

	private EntityFactory entityFactory;

	private ConfigurationOperand configuration;

	public ConfigurationOperator(ConfigurationOperand configuration) {
		this.configuration = configuration;
	}

	public ConfigurationOperator(ConfigurationOperand configuration, EntityFactory entityFactory) {
		this.configuration = configuration;
		this.entityFactory = entityFactory;
	}


	// # OPERATIONS #################################################################


	protected void parseConfigurationString() {
		// TODO: create repo independent configuration from string. this is basically for initialization.
	}


	protected String createConfigurationString() {
		return this.configuration.getFeatureInstances().stream().map((FeatureInstance fi) -> {
			StringBuffer sb = new StringBuffer();
			if (fi.getSign())
				sb.append("+");
			else
				sb.append("-");
			sb.append(fi.getFeatureVersion().getFeature().getName());
			sb.append(".");
			sb.append(fi.getFeatureVersion());
			return sb.toString();
		}).collect(Collectors.joining(", "));
	}

	@Override
	public String toString() {
		//return this.createConfigurationString();
		return this.configuration.getFeatureInstances().stream().map(fi -> fi.toString()).collect(Collectors.joining(", "));
	}


	/**
	 * Computes the modules for the association.
	 *
	 * @param maxOrder The maximum order up to which modules shall be computed.
	 * @return The set of modules.
	 */
	public Set<Module> computeModules(int maxOrder) {
		// first compute the ordinary powerset modules
		Set<Module> modules = this.powerSet(this.configuration.getFeatureInstances(), maxOrder);

		// add different feature versions to modules
		for (FeatureInstance featureInstance : this.configuration.getFeatureInstances()) {
			for (FeatureRevision featureVersion : featureInstance.getFeature().getRevisions()) { // for every version of every feature
				if (!featureVersion.equals(featureInstance.getFeatureVersion())) {
					this.addVersionToModules(featureVersion, modules);
				}
			}
		}

		return modules;
	}

	private void addVersionToModules(FeatureRevision featureVersion, Set<Module> modules) {
		Set<Module> modulesToAdd = new HashSet<Module>();
		for (Module module : modules) { // for every module

			boolean featureContained = false;
			boolean versionContained = false;
			for (ModuleFeature moduleFeature : module) {
				if (moduleFeature.getFeature().equals(featureVersion.getFeature()))
					featureContained = true;
				if (moduleFeature.contains(featureVersion))
					versionContained = true;
			}
			if (featureContained && !versionContained) { // feature must be contained in module but not in the same version as the one we want to add
				Module newModule = this.configuration.createModule();

				for (ModuleFeature moduleFeature : module) {
					if (moduleFeature.getFeature().equals(featureVersion.getFeature())) {
						ModuleFeature newModuleFeature = this.configuration.createModuleFeature(moduleFeature); // make a copy of the module feature
						newModuleFeature.add(featureVersion);
						newModule.add(newModuleFeature);
					} else {
						newModule.add(moduleFeature); // copy module feature from original module
					}
				}

				modulesToAdd.add(newModule);
			}
		}
		modules.addAll(modulesToAdd);
	}

	private Set<Module> powerSet(final Set<FeatureInstance> featureInstances, int maxOrder) {
		checkNotNull(featureInstances);

		Set<ModuleFeature> moduleFeatures = new HashSet<ModuleFeature>();
		for (FeatureInstance featureInstance : featureInstances) {
			moduleFeatures.add(this.configuration.createModuleFeature(featureInstance.getFeature(), Arrays.asList(featureInstance.getFeatureVersion()), featureInstance.getSign()));
		}

		// add empty set
		Set<Module> moduleSet = new HashSet<>();
		moduleSet.add(this.configuration.createModule()); // add empty module to power set

		for (final ModuleFeature moduleFeature : moduleFeatures) {
			final Set<Module> toAdd = new HashSet<>();

			for (final Module module : moduleSet) {
				if (module.getOrder() < maxOrder) {
					final Module newModule = this.configuration.createModule();
					newModule.addAll(module);
					newModule.add(moduleFeature);
					toAdd.add(newModule);
				}
			}

			moduleSet.addAll(toAdd);
		}

		moduleSet.remove(this.configuration.createModule()); // remove the empty module again

		return moduleSet;
	}


	// # INTERFACE #################################################################

	public interface ConfigurationOperand extends Configuration {
		public Module createModule();

		public ModuleFeature createModuleFeature(ModuleFeature moduleFeature);

		public ModuleFeature createModuleFeature(Feature feature, boolean sign);

		public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureRevision> featureVersions, boolean sign);
	}

}
