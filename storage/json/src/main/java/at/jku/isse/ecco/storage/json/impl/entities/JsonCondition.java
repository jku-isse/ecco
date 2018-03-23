package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JsonCondition implements Condition, Serializable {

    private Condition.TYPE type;
    private Map<Module, Collection<ModuleRevision>> moduleToRevisionsMap;


    public JsonCondition() {
        this.type = Condition.TYPE.AND;
        this.moduleToRevisionsMap = new HashMap<>();
    }


    @Override
    public Condition.TYPE getType() {
        return this.type;
    }

    @Override
    public void setType(Condition.TYPE type) {
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
        JsonCondition that = (JsonCondition) o;
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
