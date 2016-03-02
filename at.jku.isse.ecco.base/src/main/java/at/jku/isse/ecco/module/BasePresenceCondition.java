package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.operation.PresenceConditionOperator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Memory implementation of {@link PresenceCondition}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BasePresenceCondition implements PresenceCondition, PresenceConditionOperator.PresenceConditionOperand {

	private transient PresenceConditionOperator operator = new PresenceConditionOperator(this);


	// each of these is a list of "modules", i.e. a "module expression", i.e. an expression composed of modules
	protected Set<Module> minModules = new HashSet<Module>();
	protected Set<Module> maxModules = new HashSet<Module>();
	protected Set<Module> allModules = new HashSet<Module>();
	protected Set<Module> notModules = new HashSet<Module>();

//	/**
//	 * The following describe sets of features that have never appeared without each other and whose modules have not explicitly been computed.
//	 * Features that are separated are removed from these lists, their modules computed and added to the regular modules.
//	 */
//	private Set<Set<Feature>> minFeatureExpressions;
//	private Set<Set<Feature>> maxFeatureExpressions;
//	private Set<Set<Feature>> allFeatureExpressions;
//	private Set<Set<Feature>> notFeatureExpressions;


	public BasePresenceCondition() {
		super();
	}

	public BasePresenceCondition(Configuration configuration, int maxOrder) {
		super();
		this.operator.initialize(configuration, maxOrder);
	}


	@Override
	public boolean holds(Configuration configuration) {
		return this.operator.holds(configuration);
	}

	@Override
	public boolean isEmpty() {
		return this.operator.isEmpty();
	}

	@Override
	public PresenceCondition slice(PresenceCondition other) throws EccoException {
		return this.operator.slice(other);
	}

	@Override
	public void addFeatureVersion(FeatureVersion newFeatureVersion) {
		this.operator.addFeatureVersion(newFeatureVersion);
	}


	// operand

	@Override
	public Set<Module> getMinModules() {
		return this.minModules;
	}

	@Override
	public Set<Module> getMaxModules() {
		return this.maxModules;
	}

	@Override
	public Set<Module> getNotModules() {
		return this.notModules;
	}

	@Override
	public Set<Module> getAllModules() {
		return this.allModules;
	}

	@Override
	public Module createModule() {
		return new BaseModule();
	}

	@Override
	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return this.createModuleFeature(feature, new ArrayList<>(), sign);
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return new BaseModuleFeature(feature, featureVersions, sign);
	}

	@Override
	public PresenceConditionOperator.PresenceConditionOperand createPresenceCondition() {
		return new BasePresenceCondition();
	}


	// to string

	@Override
	public String toString() {
		return this.operator.toString();
	}


//
//	public BasePresenceCondition() {
//		super();
//	}
//
//	public BasePresenceCondition(Configuration configuration) {
//		super();
//
//		// TODO: in the configuration there must not be different versions of the same feature! this is only allowed for checkout!
//
//		// first compute the ordinary powerset modules
//		Set<Module> modules = this.powerSet(configuration.getFeatureInstances(), 4);
//
//		// add different feature versions to modules
//		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
//			for (FeatureVersion featureVersion : featureInstance.getFeature().getVersions()) { // for every version of every feature
//				if (!featureVersion.equals(featureInstance.getFeatureVersion())) {
//					this.addVersionToModules(featureVersion, modules);
//				}
//			}
//		}
//
//		// initialize the module sets
//		this.minModules.addAll(modules);
//		this.maxModules.addAll(modules);
//		this.allModules.addAll(modules);
//	}
//
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
//				Module newModule = this.createModule();
//
//				for (ModuleFeature moduleFeature : module) {
//					if (moduleFeature.equals(featureVersion.getFeature())) {
//						ModuleFeature newModuleFeature = this.createModuleFeature(moduleFeature); // make a copy of the module feature
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
//			moduleFeatures.add(this.createModuleFeature(featureInstance.getFeature(), Arrays.asList(featureInstance.getFeatureVersion()), featureInstance.getSign()));
//		}
//
//		// add empty set
//		Set<Module> moduleSet = new HashSet<>();
//		moduleSet.add(this.createModule()); // add empty module to power set
//
//		for (final ModuleFeature moduleFeature : moduleFeatures) {
//			final Set<Module> toAdd = new HashSet<>();
//
//			for (final Module module : moduleSet) {
//				if (module.getOrder() < maxOrder) {
//					final Module newModule = this.createModule();
//					newModule.addAll(module);
//					newModule.add(moduleFeature);
//					toAdd.add(newModule);
//				}
//			}
//
//			moduleSet.addAll(toAdd);
//		}
//
//		moduleSet.remove(this.createModule()); // remove the empty module again
//
//		return moduleSet;
//	}
//
//
//	protected Module createModule() {
//		return new BaseModule();
//	}
//
//	protected ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
//		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
//	}
//
//	protected ModuleFeature createModuleFeature(Feature feature, boolean sign) {
//		return this.createModuleFeature(feature, new ArrayList<>(), sign);
//	}
//
//	protected ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
//		return new BaseModuleFeature(feature, featureVersions, sign);
//	}
//
//	protected PresenceCondition createPresenceCondition() {
//		return new BasePresenceCondition();
//	}
//
//
//	@Override
//	public void addFeatureVersion(FeatureVersion newFeatureVersion) {
//		for (Set<Module> modules : Arrays.asList(this.minModules, this.maxModules, this.notModules, this.allModules)) {
//			this.addVersionToModules(newFeatureVersion, modules);
//		}
//	}
//
//
//	@Override
//	public boolean holds(Configuration configuration) {
//		// A presence condition holds in a configuration when at least one of the modules in minModules or maxModules holds. A module holds if all its feature instances are contained in a configuration.
//		for (Module module : minModules) {
//			if (module.holds(configuration)) {
//				return true;
//			}
//		}
//		if (minModules.isEmpty()) {
//			for (Module module : maxModules) {
//				if (module.holds(configuration))
//					return true;
//			}
//		}
//		return false;
//	}
//
//	@Override
//	public boolean isEmpty() {
//		if (this.minModules.isEmpty() && this.maxModules.isEmpty() && this.allModules.isEmpty() && this.notModules.isEmpty())
//			return true;
//		else
//			return false;
//	}
//
//	@Override
//	public PresenceCondition slice(PresenceCondition other) {
//		if (!(other instanceof PresenceCondition))
//			return null;
//
//		BasePresenceCondition otherBase = (BasePresenceCondition) other;
//		BasePresenceCondition intersection = (BasePresenceCondition) this.createPresenceCondition(); //new BasePresenceCondition();
//
//		// TODO: for now we ignore the feature expressions. add this later.
//
//		/**
//		 * TODO: at some point i must make sure that all feature instances refer to the ones in the repository!
//		 * when an association is committed that contains a new feature/feature version it must be added to the repository's list!
//		 * existing feature instances need to be replaced by the ones stored in the repository.
//		 *
//		 * OR: do this in the client already? as client get the feature instances from the server when creating a configuration!
//		 * either already when computing the configuration from the configuration string or later with a method
//		 * "Configuration getServerConfiguration(Configuration clientConfiguration)" or "Configuration getServerConfiguration(String configurationString)".
//		 * but this will not work if the client info is stored in a separate db!
//		 *
//		 * no, the server should take care of this...
//		 *
//		 * provide two commit options:
//		 * "commit(Configuration configuration, Set<Node> nodes)" for when the client is simple and does not provide traceability, and
//		 * "commit(Collection<Association> associations)" when the client is more advanced and provides traceability.
//		 * Also consider a hybrid option: "commit(Configuration configuration, Set<Node> nodes, Collection<Association> associations)" or just use one and then the other.
//		 */
//
//		// intersection
//		intersection.allModules.addAll(this.allModules);
//		intersection.allModules.addAll(otherBase.allModules);
//
//		intersection.minModules.addAll(this.minModules);
//		intersection.minModules.retainAll(otherBase.minModules);
//
//		intersection.notModules.addAll(this.notModules);
//		//intersection.notModules.addAll(otherBase.notModules); // NOTE: DO NOT EXECUTE THIS!
//
//		intersection.maxModules.addAll(intersection.allModules);
//		intersection.maxModules.removeAll(intersection.notModules);
//
//		// this
//		this.minModules.removeAll(intersection.minModules);
//
//		this.notModules.addAll(otherBase.allModules);
//
//		this.maxModules.addAll(this.allModules);
//		this.maxModules.removeAll(this.notModules);
//
//		// other
//		otherBase.minModules.removeAll(intersection.minModules);
//
//		otherBase.notModules.addAll(this.allModules);
//
//		otherBase.maxModules.addAll(otherBase.allModules);
//		otherBase.maxModules.removeAll(otherBase.notModules);
//
//		return intersection;
//	}
//
//	@Override
//	public String toString() {
//		Set<Module> modules = null;
//		String separator = " AND ";
//		if (!this.minModules.isEmpty()) { // use min modules
//			modules = this.minModules;
//			separator = " AND ";
//		} else { // use max modules
//			modules = this.maxModules;
//			separator = " OR ";
//		}
//
//		String result = modules.stream().sorted((m1, m2) -> (m1.getOrder() - m2.getOrder())).map((Module module) -> {
//			return module.toString();
//		}).collect(Collectors.joining(separator));
//
//		return result;
//	}

}
