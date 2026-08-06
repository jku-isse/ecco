package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import org.eclipse.collections.impl.factory.Maps;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@NodeEntity
public class NeoCondition extends NeoEntity implements Condition {

    @Relationship("hasTypeCd")
	private TYPE type;

    //TODO: make this a list
    //@Relationship("hasModuleToRevisionsMapCd")
	@Transient
	private Map<Module, Collection<ModuleRevision>> moduleToRevisionsMap;

	public NeoCondition() {
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
		NeoCondition that = (NeoCondition) o;
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
