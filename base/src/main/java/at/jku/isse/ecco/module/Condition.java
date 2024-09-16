package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;

import java.util.*;
import java.util.stream.Collectors;


public interface Condition extends Persistable {

	enum TYPE {
		AND, OR
	}

	TYPE getType();

	void setType(TYPE type);

	Map<Module, Collection<ModuleRevision>> getModules();

	default void addModule(Module module) {
		this.getModules().computeIfAbsent(module, k -> new ArrayList<>());
	}

	default void addModuleRevision(ModuleRevision moduleRevision) {
		Collection<ModuleRevision> moduleRevisions = this.getModules().computeIfAbsent(moduleRevision.getModule(), k -> new ArrayList<>());
		moduleRevisions.add(moduleRevision);
	}

	default boolean contains(Module module) {
		return this.getModules().containsKey(module);
	}

	default boolean contains(ModuleRevision moduleRevision) {
		Collection<ModuleRevision> moduleRevisions = this.getModules().get(moduleRevision.getModule());
		if (moduleRevisions == null)
			return false;
		return moduleRevisions.contains(moduleRevision);
	}


	/**
	 * Checks if the condition holds for a given configuration.
	 * A condition holds in a configuration when at least one of its module revisions holds. A module revision holds if all its feature revisions are contained in a configuration.
	 *
	 * @param configuration The configuration against which the presence condition should be checked.
	 * @return True if the presence condition holds for configuration, false otherwise.
	 */
	default boolean holds(Configuration configuration) {
		for (Map.Entry<Module, Collection<ModuleRevision>> entry : this.getModules().entrySet()) {
			// if the module holds check if also a concrete revision holds
			if (entry.getKey().holds(configuration) && entry.getValue() != null) {
				for (ModuleRevision moduleRevision : entry.getValue()) {
					if (moduleRevision.holds(configuration))
						return true;
				}
			}
		}
		return false;
	}

	default String toLogicString(){
		// Condition is true if one module-revision-formula is true for every module
		FormulaFactory formulaFactory = new FormulaFactory();
		Map<Module, Collection<ModuleRevision>> moduleMap = this.getModules();

		// if a module-revision holds, the respective module holds as well
		// or(or(all module-revision-conditions related to the same module))
		Collection<Formula> moduleFormulas = new LinkedList<>();
		for (Collection<ModuleRevision> moduleRevisions : moduleMap.values()) {
			Collection<Formula> moduleRevisionFormulas = moduleRevisions.stream()
					.map(ModuleRevision::getConditionString)
					.map(s -> this.parseString(formulaFactory, s))
					.collect(Collectors.toList());
			moduleFormulas.add(formulaFactory.or(moduleRevisionFormulas));
		}

		Formula conditionFormula;
		conditionFormula = formulaFactory.or(moduleFormulas);
		return conditionFormula.toString();
	}

	private Formula parseString(FormulaFactory formulaFactory, String string){
		try{
			return formulaFactory.parse(string);
		} catch (ParserException e){
			throw new RuntimeException("Formula-String in module-revision could not be parsed: " + e.getMessage());
		}
	}

	/**
	 * Checks if this condition implies other condition.
	 * This means every module in this must be implied by at least one module in other.
	 *
	 * @param other The other condition.
	 * @return True if this condition implies the other condition, false otherwise.
	 */
	default boolean implies(Condition other) {
		for (Map.Entry<Module, Collection<ModuleRevision>> otherEntry : other.getModules().entrySet()) {
			for (ModuleRevision otherModuleRevision : otherEntry.getValue()) {
				boolean implied = false;
				for (Map.Entry<Module, Collection<ModuleRevision>> thisEntry : this.getModules().entrySet()) {
					if (thisEntry.getKey().implies(otherEntry.getKey())) {
						for (ModuleRevision thisModuleRevision : thisEntry.getValue()) {
							if (thisModuleRevision.implies(otherModuleRevision)) {
								implied = true;
								break;
							}
						}
						if (implied) {
							break;
						}
					}
				}
				if (!implied) {
					return false;
				}
			}
		}
		return true;
	}


	default String getModuleConditionString() {
		return this.getModules().keySet().stream().sorted(Comparator.comparingInt(Module::getOrder)).map(Module::toString).collect(Collectors.joining(" " + this.getType().toString() + " "));
	}

	default String getModuleRevisionConditionString() {
		return this.getModules().entrySet().stream().sorted(Comparator.comparingInt(e -> e.getKey().getOrder())).map(entry -> "[" + entry.getValue().stream().map(ModuleRevision::toString).collect(Collectors.joining("/")) + "]").collect(Collectors.joining(" " + this.getType().toString() + " "));
	}

	default String getSimpleModuleConditionString() {
		Map<Module, Collection<ModuleRevision>> modules = this.getModules();
		int minOrder = modules.isEmpty() ? 0 : modules.keySet().stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
		return modules.keySet().stream().filter(module -> module.getOrder() <= minOrder).map(Module::toString).collect(Collectors.joining(" " + this.getType().toString() + " "));
	}

	default String getSimpleModuleRevisionConditionString() {
		Map<Module, Collection<ModuleRevision>> modules = this.getModules();
		int minOrder = modules.isEmpty() ? 0 : modules.keySet().stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
		return modules.entrySet().stream().filter(entry -> entry.getKey().getOrder() <= minOrder).map(entry -> "[" + entry.getValue().stream().map(ModuleRevision::toString).collect(Collectors.joining(",")) + "]").collect(Collectors.joining(" " + this.getType().toString() + " "));
	}

	@Override
	String toString();
	
	default String getPreprocessorConditionString() {
		Map<Module, Collection<ModuleRevision>> modules = this.getModules();
		return modules.keySet().stream().map(Module::getPreprocessorModuleString).collect(Collectors.joining((this.getType() == TYPE.AND ? " && " : " || ")));
	}

}
