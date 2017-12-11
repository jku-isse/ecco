package at.jku.isse.ecco.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.*;
import org.garret.perst.Persistent;

import java.util.*;

/**
 * Perst implementation of {@link PresenceCondition}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstPresenceCondition extends Persistent implements PresenceCondition, PresenceConditionOperator.PresenceConditionOperand {

	private transient PresenceConditionOperator operator = new PresenceConditionOperator(this);


	protected Set<Module> minModules = new HashSet<Module>();
	protected Set<Module> maxModules = new HashSet<Module>();
	protected Set<Module> allModules = new HashSet<Module>();
	protected Set<Module> notModules = new HashSet<Module>();


	public PerstPresenceCondition() {
		super();
	}

	public PerstPresenceCondition(Configuration configuration, int maxOrder) {
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
	public void merge(PresenceCondition other) {
		this.operator.merge(other);
	}

	@Override
	public void addFeatureInstance(FeatureInstance featureInstance) {
		this.operator.addFeatureInstance(featureInstance);
	}

	@Override
	public void addFeatureInstance(FeatureInstance featureInstance, int maxOrder) {
		this.operator.addFeatureInstance(featureInstance, maxOrder);
	}

	@Override
	public void addFeatureVersion(FeatureVersion newFeatureVersion) {
		this.operator.addFeatureVersion(newFeatureVersion);
	}

	@Override
	public void removeFeatureVersion(FeatureVersion featureVersion) {
		// TODO
	}

	@Override
	public void removeModules(Set<Module> modules) {
		// TODO
	}


	// perst

	public void storeRecursively() {
		this.store();

		// store all children
		for (Set<Module> modules : Arrays.asList(this.minModules, this.maxModules, this.notModules, this.allModules)) {
			for (Module module : modules) {
				if (module instanceof PerstModule)
					((PerstModule) module).store();
			}
		}
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
		return new PerstModule();
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
		return new PerstModuleFeature(feature, featureVersions, sign);
	}

	@Override
	public PresenceConditionOperator.PresenceConditionOperand createPresenceCondition() {
		return new PerstPresenceCondition();
	}


	@Override
	public boolean equals(Object o) {
		return this.operator.equals(o);
	}

	@Override
	public boolean implies(PresenceCondition other) {
		return this.operator.implies(other);
	}

	@Override
	public String getLabel() {
		return this.operator.getLabel();
	}

	@Override
	public String getSimpleLabel() {
		return this.operator.getSimpleLabel();
	}

	@Override
	public void addFeatureInstance(FeatureVersion featureVersion, boolean sign, int maxOrder) {
		this.addFeatureInstance(new PerstFeatureInstance(featureVersion.getFeature(), featureVersion, sign), maxOrder);
	}

	@Override
	public int hashCode() {
		return this.operator.hashCode();
	}


	@Override
	public String toString() {
		return this.operator.getLabel();
	}

}
