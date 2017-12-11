package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.*;
import java.util.stream.Collectors;

public class PresenceConditionOperator {

	private PresenceConditionOperand presenceCondition;

	public PresenceConditionOperator(PresenceConditionOperand presenceCondition) {
		this.presenceCondition = presenceCondition;
	}


	// # OPERATIONS #################################################################


	public void merge(PresenceCondition other) {
		this.presenceCondition.getMinModules().addAll(other.getMinModules());
		this.presenceCondition.getMaxModules().addAll(other.getMaxModules());
		this.presenceCondition.getAllModules().addAll(other.getAllModules());
		this.presenceCondition.getNotModules().addAll(other.getNotModules());
	}


	/**
	 * Removes a feature version from a presence condition. This removes all modules that contain the feature version positively, and removes the feature version from all modules that contain it negatively.
	 */
	public void removeFeatureVersion(FeatureVersion featureVersion) {
		for (Set<Module> modules : new Set[]{this.presenceCondition.getMinModules(), this.presenceCondition.getMaxModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules()}) {
			Iterator<Module> it = modules.iterator();
			while (it.hasNext()) {
				Module module = it.next();

				Iterator<ModuleFeature> it2 = module.iterator();
				while (it2.hasNext()) {
					ModuleFeature mf = it2.next();
					if (mf.contains(featureVersion)) {
						if (mf.getSign()) {
							it.remove(); // remove module from presence condition
						} else {
							it2.remove(); // remove feature from module
						}
					}
				}
			}
		}
	}


	/**
	 * Adds the given feature instance to every module that does not already contain an instance of the same feature.
	 *
	 * @param featureInstance The feature instance to add.
	 */
	public void addFeatureInstance(FeatureInstance featureInstance) {
		this.addFeatureInstance(featureInstance, Integer.MAX_VALUE);
	}

	public void addFeatureInstance(FeatureInstance featureInstance, int maxOrder) {
		for (Set<Module> modules : new Set[]{this.presenceCondition.getMinModules(), this.presenceCondition.getMaxModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules()}) {
			Set<Module> modulesToAdd = new HashSet<>();
			for (Module module : modules) {
				if (module.size() >= maxOrder)
					continue;
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

	/**
	 * Checks if a given condition holds for a given configuration.
	 *
	 * @param configuration
	 * @return
	 */
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
		//if (this.presenceCondition.getMinModules().isEmpty() && this.presenceCondition.getMaxModules().isEmpty() && this.presenceCondition.getAllModules().isEmpty() && this.presenceCondition.getNotModules().isEmpty())
		if (this.presenceCondition.getMinModules().isEmpty() && this.presenceCondition.getMaxModules().isEmpty())
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PresenceConditionOperand)) return false;
		if (!super.equals(o)) return false;

		PresenceConditionOperand that = (PresenceConditionOperand) o;

		if (!this.presenceCondition.getMinModules().equals(that.getMinModules())) return false;
		if (!this.presenceCondition.getMaxModules().equals(that.getMaxModules())) return false;
		if (!this.presenceCondition.getAllModules().equals(that.getAllModules())) return false;
		return this.presenceCondition.getNotModules().equals(that.getNotModules());
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + this.presenceCondition.getMinModules().hashCode();
		result = 31 * result + this.presenceCondition.getMaxModules().hashCode();
		result = 31 * result + this.presenceCondition.getAllModules().hashCode();
		result = 31 * result + this.presenceCondition.getNotModules().hashCode();
		return result;
	}


	public String getSimpleLabel() {
		Set<Module> modules = null;
		String separator;
		if (!this.presenceCondition.getMinModules().isEmpty()) { // use min modules
			modules = this.presenceCondition.getMinModules();
			separator = " AND ";
		} else if (!this.presenceCondition.getMaxModules().isEmpty()) { // use max modules
			modules = this.presenceCondition.getMaxModules();
			separator = " OR ";
		} else {
			return "";
		}

		int minOrder = modules.stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
		String result = modules.stream().filter(m -> m.getOrder() <= minOrder).map((Module module) -> {
			return module.toString();
		}).collect(Collectors.joining(separator));

		return result;
	}


	public String getLabel() {
		Set<Module> modules = null;
		String separator;
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


	/**
	 * Fixes a feature version to a specific value and removes every occurrence of the feature version from the condition by either removing the feature version from modules (that contain <code>fi</code>) or by removing whole modules (that contain !<code>fi</code>).
	 *
	 * @param fi
	 */
	public void fixate(FeatureInstance fi) { // TODO: change from FI to MF here.
		for (Set<Module> modules : new Set[]{this.presenceCondition.getMinModules(), this.presenceCondition.getNotModules(), this.presenceCondition.getAllModules(), this.presenceCondition.getMaxModules()}) {

			Iterator<Module> moduleIterator = this.presenceCondition.getMinModules().iterator();
			while (moduleIterator.hasNext()) {
				Module m = moduleIterator.next();

				Iterator<ModuleFeature> moduleFeatureIterator = m.iterator();
				while (moduleFeatureIterator.hasNext()) {
					ModuleFeature mf = moduleFeatureIterator.next();

					Iterator<FeatureVersion> featureVersionIterator = mf.iterator();
					while (featureVersionIterator.hasNext()) {
						FeatureVersion fv = featureVersionIterator.next();

						if (fi.getFeature().equals(fv.getFeature())) {
							if (mf.getSign() == fi.getSign()) {
								// remove module feature from module.
								moduleFeatureIterator.remove();
								// if module is empty remove it from condition.
								if (m.isEmpty()) {
									moduleIterator.remove();
								}
							} else {
								// remove fv from module feature.
								featureVersionIterator.remove();
								// if module feature is empty remove whole module.
								if (mf.isEmpty()) {
									moduleIterator.remove();
								}
							}
						}
					}
				}

			}

			// workaround to rehash values because after these modifications the values will have new hashes and there may be duplicates
			Set<Module> temp = new HashSet<>();
			temp.addAll(this.presenceCondition.getMinModules());
			this.presenceCondition.getMinModules().clear();
			this.presenceCondition.getMinModules().addAll(temp);
		}

		// TODO: this is for testing. later remove it.

		// special treatment for max modules
		Set<Module> maxModules = new HashSet<>();
		maxModules.addAll(this.presenceCondition.getAllModules());
		maxModules.removeAll(this.presenceCondition.getNotModules());

		if (!this.presenceCondition.getMaxModules().equals(maxModules))
			System.err.println("ERROR: inconsistency in max modules after fixating values!");

//		c.getMaxModules().clear();
//		c.getMaxModules().addAll(maxModules);
	}


	/**
	 * Checks if this condition implies other condition.
	 * This imeans every module in this must be implied by at least one module in other.
	 *
	 * @param other
	 * @return
	 */
	public boolean implies(PresenceCondition other) {
		for (Module m2 : other.getMinModules()) {
			boolean moduleImplied = false;
			for (Module m1 : this.presenceCondition.getMinModules()) {
				if (m1.containsAll(m2)) {
					moduleImplied = true;
					break;
				}
			}
			if (!moduleImplied) {
				return false;
			}

		}

		for (Module m2 : other.getMaxModules()) {
			boolean moduleImplied = false;
			for (Module m1 : this.presenceCondition.getMaxModules()) {
				if (m1.containsAll(m2)) {
					moduleImplied = true;
					break;
				}
			}
			if (!moduleImplied) {
				return false;
			}

		}

		return true;
	}


//	/**
//	 * Checks if a presence condition is satisfiable given a (partial) configuration.
//	 * Every feature version that is not specified by either a positive or a negative instance in the configuration is considered unspecified.
//	 *
//	 * @param configuration
//	 * @return
//	 */
//	public boolean satisfiable(Configuration configuration) {
//
//		return false;
//	}


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
