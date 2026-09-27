package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;


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

	/**
	 * The condition as a LogicNG-parsable formula: the disjunction of its module revisions, each
	 * the conjunction of its positive feature revisions and the negation of every revision of its
	 * negative features (see {@link ModuleRevision#getConditionString()}).
	 * <p>
	 * Module revisions made redundant by another one with a subset of its literals are left out
	 * ({@code X | X & Y = X}, an identity - the formula is equivalent to the disjunction of all of
	 * them). computeCondition() yields a whole lattice of such supersets: with 40 commits, one
	 * association had 32,684 module revisions that absorb to about 20, and writing all of them made
	 * a 4.2 million character string - stored on every node as its retroactive condition, rewritten
	 * with every commit (169 MB of association files) and parsed at every checkout. Terms and
	 * literals are sorted, so an unchanged condition gives an identical string.
	 */
	default String toLogicString(){
		List<ModuleRevision> moduleRevisions = new ArrayList<>();
		for (Collection<ModuleRevision> revisions : this.getModules().values())
			moduleRevisions.addAll(revisions);
		// smallest first: a term can only be absorbed by one with at most as many literals, which
		// then is already among the kept ones - so each term is compared with the kept terms only
		moduleRevisions.sort(Comparator.comparingInt(r -> r.getPos().length + r.getNeg().length));

		List<ModuleRevision> kept = new ArrayList<>();
		for (ModuleRevision candidate : moduleRevisions) {
			boolean absorbed = false;
			for (ModuleRevision smaller : kept) {
				if (containsAll(candidate.getPos(), smaller.getPos()) && containsAll(candidate.getNeg(), smaller.getNeg())) {
					absorbed = true;
					break;
				}
			}
			if (!absorbed)
				kept.add(candidate);
		}

		if (kept.isEmpty())
			return "$false";
		SortedSet<String> terms = new TreeSet<>();
		for (ModuleRevision moduleRevision : kept) {
			SortedSet<String> literals = new TreeSet<>();
			for (FeatureRevision featureRevision : moduleRevision.getPos())
				literals.add(featureRevision.getLogicLiteralRepresentation());
			for (Feature feature : moduleRevision.getNeg())
				for (FeatureRevision featureRevision : feature.getRevisions())
					literals.add("~" + featureRevision.getLogicLiteralRepresentation());
			if (literals.isEmpty())
				return "$true"; // an empty module revision always holds
			terms.add(literals.size() == 1 ? literals.first() : "(" + String.join(" & ", literals) + ")");
		}
		return String.join(" | ", terms);
	}

	private static boolean containsAll(Object[] larger, Object[] smaller) {
		for (Object element : smaller) {
			boolean found = false;
			for (Object other : larger) {
				if (element.equals(other)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
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
