package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaPresenceCondition implements PresenceCondition, Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	// each of these is a list of "modules", i.e. a "module expression", i.e. an expression composed of modules
	@OneToMany(targetEntity = JpaModule.class, cascade = CascadeType.ALL)
	protected Set<Module> minModules = new HashSet<Module>();
	@OneToMany(targetEntity = JpaModule.class, cascade = CascadeType.ALL)
	protected Set<Module> maxModules = new HashSet<Module>();
	@OneToMany(targetEntity = JpaModule.class, cascade = CascadeType.ALL)
	protected Set<Module> allModules = new HashSet<Module>();
	@OneToMany(targetEntity = JpaModule.class, cascade = CascadeType.ALL)
	protected Set<Module> notModules = new HashSet<Module>();

//	/**
//	 * The following describe sets of features that have never appeared without each other and whose modules have not explicitly been computed.
//	 * Features that are separated are removed from these lists, their modules computed and added to the regular modules.
//	 */
//	private Set<Set<Feature>> minFeatureExpressions;
//	private Set<Set<Feature>> maxFeatureExpressions;
//	private Set<Set<Feature>> allFeatureExpressions;
//	private Set<Set<Feature>> notFeatureExpressions;

	public JpaPresenceCondition() {
		super();
	}

	public JpaPresenceCondition(Configuration configuration, int maxOrder) {
		super();

		// TODO: in the configuration there must not be different versions of the same feature! this is only allowed for checkout!

		// first compute the ordinary powerset modules
		Set<Module> modules = this.powerSet(configuration.getFeatureInstances(), 4);

		// add different feature versions to modules
		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
			for (FeatureVersion featureVersion : featureInstance.getFeature().getVersions()) { // for every version of every feature
				if (!featureVersion.equals(featureInstance.getFeatureVersion())) {
					this.addVersionToModules(featureVersion, modules);
				}
			}
		}

		// initialize the module sets
		this.minModules.addAll(modules);
		this.maxModules.addAll(modules);
		this.allModules.addAll(modules);
	}

	private void addVersionToModules(FeatureVersion featureVersion, Set<Module> modules) {
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
				Module newModule = this.createModule();

				for (ModuleFeature moduleFeature : module) {
					if (moduleFeature.equals(featureVersion.getFeature())) {
						ModuleFeature newModuleFeature = this.createModuleFeature(moduleFeature); // make a copy of the module feature
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
			moduleFeatures.add(this.createModuleFeature(featureInstance.getFeature(), Arrays.asList(featureInstance.getFeatureVersion()), featureInstance.getSign()));
		}

		// add empty set
		Set<Module> moduleSet = new HashSet<>();
		moduleSet.add(this.createModule()); // add empty module to power set

		for (final ModuleFeature moduleFeature : moduleFeatures) {
			final Set<Module> toAdd = new HashSet<>();

			for (final Module module : moduleSet) {
				if (module.getOrder() < maxOrder) {
					final Module newModule = this.createModule();
					newModule.addAll(module);
					newModule.add(moduleFeature);
					toAdd.add(newModule);
				}
			}

			moduleSet.addAll(toAdd);
		}

		moduleSet.remove(this.createModule()); // remove the empty module again

		return moduleSet;
	}


	protected Module createModule() {
		return new JpaModule();
	}

	protected ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
	}

	protected ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return this.createModuleFeature(feature, new ArrayList<>(), sign);
	}

	protected ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return new JpaModuleFeature(feature, featureVersions, sign);
	}

	protected PresenceCondition createPresenceCondition() {
		return new JpaPresenceCondition();
	}


	//@Override
	public void addFeatureVersion(FeatureVersion newFeatureVersion) {
		for (Set<Module> modules : Arrays.asList(this.minModules, this.maxModules, this.notModules, this.allModules)) {
			this.addVersionToModules(newFeatureVersion, modules);
		}
	}


	//@Override
	public boolean holds(Configuration configuration) {
		// A presence condition holds in a configuration when at least one of the modules in minModules or maxModules holds. A module holds if all its feature instances are contained in a configuration.
		for (Module module : minModules) {
			if (module.holds(configuration)) {
				return true;
			}
		}
		if (minModules.isEmpty()) {
			for (Module module : maxModules) {
				if (module.holds(configuration))
					return true;
			}
		}
		return false;
	}

	//@Override
	public boolean isEmpty() {
		if (this.minModules.isEmpty() && this.maxModules.isEmpty() && this.allModules.isEmpty() && this.notModules.isEmpty())
			return true;
		else
			return false;
	}

	//@Override
	public PresenceCondition slice(PresenceCondition other) {
		if (!(other instanceof PresenceCondition))
			return null;

		JpaPresenceCondition otherBase = (JpaPresenceCondition) other;
		JpaPresenceCondition intersection = (JpaPresenceCondition) this.createPresenceCondition();

		// TODO: for now we ignore the feature expressions. add this later.

		/**
		 * TODO: at some point i must make sure that all feature instances refer to the ones in the repository!
		 * when an association is committed that contains a new feature/feature version it must be added to the repository's list!
		 * existing feature instances need to be replaced by the ones stored in the repository.
		 *
		 * OR: do this in the client already? as client get the feature instances from the server when creating a configuration!
		 * either already when computing the configuration from the configuration string or later with a method
		 * "Configuration getServerConfiguration(Configuration clientConfiguration)" or "Configuration getServerConfiguration(String configurationString)".
		 * but this will not work if the client info is stored in a separate db!
		 *
		 * no, the server should take care of this...
		 *
		 * provide two commit options:
		 * "commit(Configuration configuration, Set<Node> nodes)" for when the client is simple and does not provide traceability, and
		 * "commit(Collection<Association> associations)" when the client is more advanced and provides traceability.
		 * Also consider a hybrid option: "commit(Configuration configuration, Set<Node> nodes, Collection<Association> associations)" or just use one and then the other.
		 */

		// intersection
		intersection.allModules.addAll(this.allModules);
		intersection.allModules.addAll(otherBase.allModules);

		intersection.minModules.addAll(this.minModules);
		intersection.minModules.retainAll(otherBase.minModules);

		intersection.notModules.addAll(this.notModules);
		//intersection.notModules.addAll(otherBase.notModules); // NOTE: DO NOT EXECUTE THIS!

		intersection.maxModules.addAll(intersection.allModules);
		intersection.maxModules.removeAll(intersection.notModules);

		// this
		this.minModules.removeAll(intersection.minModules);

		this.notModules.addAll(otherBase.allModules);

		this.maxModules.addAll(this.allModules);
		this.maxModules.removeAll(this.notModules);

		// other
		otherBase.minModules.removeAll(intersection.minModules);

		otherBase.notModules.addAll(this.allModules);

		otherBase.maxModules.addAll(otherBase.allModules);
		otherBase.maxModules.removeAll(otherBase.notModules);

		return intersection;
	}

	@Override
	public String toString() {
		Set<Module> modules = null;
		String separator = " AND ";
		if (!this.minModules.isEmpty()) { // use min modules
			modules = this.minModules;
			separator = " AND ";
		} else { // use max modules
			modules = this.maxModules;
			separator = " OR ";
		}

		String result = modules.stream().sorted((m1, m2) -> (m1.getOrder() - m2.getOrder())).map((Module module) -> {
			return module.toString();
		}).collect(Collectors.joining(separator));

		return result;
	}

}
