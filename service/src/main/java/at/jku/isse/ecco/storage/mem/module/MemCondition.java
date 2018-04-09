package at.jku.isse.ecco.storage.mem.module;

import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import org.eclipse.collections.impl.factory.Maps;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class MemCondition implements Condition {

	public static final long serialVersionUID = 1L;


	private TYPE type;
	private Map<Module, Collection<ModuleRevision>> moduleToRevisionsMap;


	public MemCondition() {
		this.type = TYPE.AND;
		this.moduleToRevisionsMap = Maps.mutable.empty();
	}


	@Override
	public TYPE getType() {
		return this.type;
	}

	@Override
	public void setType(TYPE type) {
		this.type = type;
	}

	@Override
	public Map<Module, Collection<ModuleRevision>> getModules() {
		return this.moduleToRevisionsMap;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MemCondition that = (MemCondition) o;
		return type == that.type &&
				Objects.equals(moduleToRevisionsMap, that.moduleToRevisionsMap);
	}

	@Override
	public int hashCode() {

		return Objects.hash(type, moduleToRevisionsMap);
	}

	@Override
	public String toString() {
		return this.getModuleRevisionConditionString();
	}

}
