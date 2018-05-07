package at.jku.isse.ecco.storage.perst.module;

import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PerstCondition implements Condition {

	private TYPE type;
	private Map<Module, Collection<ModuleRevision>> moduleToRevisionsMap;


	public PerstCondition() {
		this.type = TYPE.AND;
		this.moduleToRevisionsMap = new HashMap<>();
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
		PerstCondition that = (PerstCondition) o;
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
