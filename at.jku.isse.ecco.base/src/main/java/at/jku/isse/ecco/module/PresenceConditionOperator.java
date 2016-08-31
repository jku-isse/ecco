package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PresenceConditionOperator {

	private PresenceConditionOperand presenceCondition;

	public PresenceConditionOperator(PresenceConditionOperand presenceCondition) {
		this.presenceCondition = presenceCondition;
	}


	// # OPERATIONS #################################################################


	/**
	 * Adds the given feature instance to every module that does not already contain an instance of the same feature.
	 *
	 * @param featureInstance The feature instance to add.
	 */
	public void addFeatureInstance(FeatureInstance featureInstance) {
		for (Set<Module> modules : new Set[]{this.presenceCondition.getMinModules(), this.presenceCondition.getMaxModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules()}) {
			Set<Module> modulesToAdd = new HashSet<>();
			for (Module module : modules) {
				boolean featureAlreadyContained = false;
				for (ModuleFeature mf : module) {
					if (mf.getFeature().equals(featureInstance.getFeature())) {
						featureAlreadyContained = true;
						break;
					}
				}
				if (!featureAlreadyContained) {
					Module newModule = this.presenceCondition.createModule();
					ModuleFeature newModuleFeature = this.presenceCondition.createModuleFeature(featureInstance.getFeature(), Arrays.asList(featureInstance.getFeatureVersion()), featureInstance.getSign());
					newModule.add(newModuleFeature);
					for (ModuleFeature mf : module) {
						newModule.add(mf); // TODO: copy module features? or make modules and module features immutable?
					}
					modulesToAdd.add(newModule);
				}
			}
			modules.addAll(modulesToAdd);
		}
	}

	public void addFeatureVersion(FeatureVersion featureVersion) {
		for (Set<Module> modules : new Set[]{this.presenceCondition.getMinModules(), this.presenceCondition.getMaxModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules()}) {
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
					Module newModule = this.presenceCondition.createModule();

					for (ModuleFeature moduleFeature : module) {
						if (moduleFeature.getFeature().equals(featureVersion.getFeature())) {
							ModuleFeature newModuleFeature = this.presenceCondition.createModuleFeature(moduleFeature); // make a copy of the module feature
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
	}

	public void initialize(Configuration configuration, int maxOrder) {
//		// first compute the ordinary powerset modules
//		Set<Module> modules = this.powerSet(configuration.getFeatureInstances(), maxOrder);
//
//		// add different feature versions to modules
//		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
//			for (FeatureVersion featureVersion : featureInstance.getFeature().getVersions()) { // for every version of every feature
//				if (!featureVersion.equals(featureInstance.getFeatureVersion())) {
//					this.addVersionToModules(featureVersion, modules);
//				}
//			}
//		}

		Set<Module> modules = configuration.computeModules(maxOrder);

		// initialize the module sets
		this.presenceCondition.getMinModules().clear();
		this.presenceCondition.getMaxModules().clear();
		this.presenceCondition.getAllModules().clear();
		this.presenceCondition.getMaxModules().clear();

		this.presenceCondition.getMinModules().addAll(modules);
		this.presenceCondition.getMaxModules().addAll(modules);
		this.presenceCondition.getAllModules().addAll(modules);
	}


//	private void addVersionToModules(FeatureVersion featureVersion, Set<Module> modules) {
//		Set<Module> modulesToAdd = new HashSet<Module>();
//		for (Module module : modules) { // for every module
//
//			boolean featureContained = false;
//			boolean versionContained = false;
//			for (ModuleFeature moduleFeature : module) {
//				if (moduleFeature.getFeature().equals(featureVersion.getFeature()))
//					featureContained = true;
//				if (moduleFeature.contains(featureVersion))
//					versionContained = true;
//			}
//			if (featureContained && !versionContained) { // feature must be contained in module but not in the same version as the one we want to add
//				Module newModule = this.presenceCondition.createModule();
//
//				for (ModuleFeature moduleFeature : module) {
//					if (moduleFeature.getFeature().equals(featureVersion.getFeature())) {
//						ModuleFeature newModuleFeature = this.presenceCondition.createModuleFeature(moduleFeature); // make a copy of the module feature
//						newModuleFeature.add(featureVersion);
//						newModule.add(newModuleFeature);
//					} else {
//						newModule.add(moduleFeature); // copy module feature from original module
//					}
//				}
//
//				modulesToAdd.add(newModule);
//			}
//		}
//		modules.addAll(modulesToAdd);
//	}
//
//	private Set<Module> powerSet(final Set<FeatureInstance> featureInstances, int maxOrder) {
//		checkNotNull(featureInstances);
//
//		Set<ModuleFeature> moduleFeatures = new HashSet<ModuleFeature>();
//		for (FeatureInstance featureInstance : featureInstances) {
//			moduleFeatures.add(this.presenceCondition.createModuleFeature(featureInstance.getFeature(), Arrays.asList(featureInstance.getFeatureVersion()), featureInstance.getSign()));
//		}
//
//		// add empty set
//		Set<Module> moduleSet = new HashSet<>();
//		moduleSet.add(this.presenceCondition.createModule()); // add empty module to power set
//
//		for (final ModuleFeature moduleFeature : moduleFeatures) {
//			final Set<Module> toAdd = new HashSet<>();
//
//			for (final Module module : moduleSet) {
//				if (module.getOrder() < maxOrder) {
//					final Module newModule = this.presenceCondition.createModule();
//					newModule.addAll(module);
//					newModule.add(moduleFeature);
//					toAdd.add(newModule);
//				}
//			}
//
//			moduleSet.addAll(toAdd);
//		}
//
//		moduleSet.remove(this.presenceCondition.createModule()); // remove the empty module again
//
//		return moduleSet;
//	}
//
//	public void addFeatureVersion(FeatureVersion newFeatureVersion) {
//		for (Set<Module> modules : Arrays.asList(this.presenceCondition.getMinModules(), this.presenceCondition.getMaxModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules())) {
//			this.addVersionToModules(newFeatureVersion, modules);
//		}
//	}


	public boolean holds(Configuration configuration) {
		// a presence condition holds in a configuration when at least one of the modules in minModules or maxModules holds. A module holds if all its feature instances are contained in a configuration.
		for (Module module : this.presenceCondition.getMinModules()) {
			if (module.holds(configuration)) {
				return true;
			}
		}
		if (this.presenceCondition.getMinModules().isEmpty()) {
			for (Module module : this.presenceCondition.getMaxModules()) {
				if (module.holds(configuration))
					return true;
			}
		}
		return false;
	}

	public boolean isEmpty() {
		if (this.presenceCondition.getMinModules().isEmpty() && this.presenceCondition.getMaxModules().isEmpty() && this.presenceCondition.getAllModules().isEmpty() && this.presenceCondition.getNotModules().isEmpty())
			return true;
		else
			return false;
	}

	public PresenceCondition slice(PresenceCondition other) throws EccoException {
		if (!(other instanceof PresenceConditionOperand))
			throw new EccoException("Slice requires two presence condition operands.");

		PresenceConditionOperand left = this.presenceCondition;
		PresenceConditionOperand right = (PresenceConditionOperand) other;
		PresenceConditionOperand intersection = this.presenceCondition.createPresenceCondition();

		// intersection
		intersection.getAllModules().addAll(left.getAllModules());
		intersection.getAllModules().addAll(right.getAllModules());

		intersection.getMinModules().addAll(left.getMinModules());
		intersection.getMinModules().retainAll(right.getMinModules());

		intersection.getNotModules().addAll(left.getNotModules());
		//intersection.notModules.addAll(otherBase.notModules); // NOTE: DO NOT EXECUTE THIS!

		intersection.getMaxModules().addAll(intersection.getAllModules());
		intersection.getMaxModules().removeAll(intersection.getNotModules());

		// left
		left.getMinModules().removeAll(intersection.getMinModules());

		left.getNotModules().addAll(right.getAllModules());

		left.getMaxModules().addAll(left.getAllModules());
		left.getMaxModules().removeAll(left.getNotModules());

		// right
		right.getMinModules().removeAll(intersection.getMinModules());

		right.getNotModules().addAll(left.getAllModules());

		right.getMaxModules().addAll(right.getAllModules());
		right.getMaxModules().removeAll(right.getNotModules());

		return intersection;
	}

	@Override
	public String toString() {
		Set<Module> modules = null;
		String separator = " AND ";
		if (!this.presenceCondition.getMinModules().isEmpty()) { // use min modules
			modules = this.presenceCondition.getMinModules();
			separator = " AND ";
		} else { // use max modules
			modules = this.presenceCondition.getMaxModules();
			separator = " OR ";
		}

		String result = modules.stream().sorted((m1, m2) -> (m1.getOrder() - m2.getOrder())).map((Module module) -> {
			return module.toString();
		}).collect(Collectors.joining(separator));

		return result;
	}


	// # INTERFACE #################################################################

	public interface PresenceConditionOperand extends PresenceCondition {
		public Set<Module> getMinModules();

		public Set<Module> getMaxModules();

		public Set<Module> getNotModules();

		public Set<Module> getAllModules();


		public Module createModule();

		public ModuleFeature createModuleFeature(ModuleFeature moduleFeature);

		public ModuleFeature createModuleFeature(Feature feature, boolean sign);

		public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign);

		public PresenceConditionOperand createPresenceCondition();
	}

}
