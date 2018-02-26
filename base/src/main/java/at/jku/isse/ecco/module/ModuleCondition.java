package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
public interface ModuleCondition {

	public enum TYPE {
		AND, OR
	}

	public TYPE getType();

	public Map<Module, Collection<ModuleRevision>> getModules();

	public default void addModule(Module module) {
		Collection<ModuleRevision> moduleRevisions = this.getModules().computeIfAbsent(module, k -> new ArrayList<>());
	}

	public default void addModuleRevision(ModuleRevision moduleRevision) {
		Collection<ModuleRevision> moduleRevisions = this.getModules().computeIfAbsent(moduleRevision.getModule(), k -> new ArrayList<>());
		moduleRevisions.add(moduleRevision);
	}


	public boolean holds(Configuration configuration);

	public boolean implies(ModuleCondition other);


	public default String getModuleConditionString() {
		return this.getModules().keySet().stream().map(Module::toString).collect(Collectors.joining(this.getType().toString()));
	}

	public default String getModuleRevisionConditionString() {
		return this.getModules().values().stream().map(moduleRevisions -> moduleRevisions.stream().map(ModuleRevision::toString).collect(Collectors.joining(","))).collect(Collectors.joining(this.getType().toString()));
	}

	public default String getSimpleModuleConditionString() {
		Map<Module, Collection<ModuleRevision>> modules = this.getModules();
		int minOrder = modules.isEmpty() ? 0 : modules.keySet().stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
		return modules.keySet().stream().filter(module -> module.getOrder() <= minOrder).map(Module::toString).collect(Collectors.joining(this.getType().toString()));
	}

	public default String getSimpleModuleRevisionConditionString() {
		Map<Module, Collection<ModuleRevision>> modules = this.getModules();
		int minOrder = modules.isEmpty() ? 0 : modules.keySet().stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
		return modules.entrySet().stream().filter(entry -> entry.getKey().getOrder() <= minOrder).map(entry -> entry.getValue().stream().map(ModuleRevision::toString).collect(Collectors.joining(","))).collect(Collectors.joining(this.getType().toString()));
	}

	@Override
	public String toString();

}
