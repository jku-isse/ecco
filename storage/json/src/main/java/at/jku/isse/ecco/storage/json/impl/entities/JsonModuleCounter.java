package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class JsonModuleCounter implements ModuleCounter, Serializable {
    private JsonModule module;
    private int count;
    private Collection<JsonModuleRevisionCounter> children;


    public JsonModuleCounter(JsonModule module) {
        checkNotNull(module);
        this.module = module;
        this.count = 0;
        this.children = new ArrayList<>();
    }


    @Override
    public JsonModuleRevisionCounter addChild(ModuleRevision child) {
        if (!(child instanceof JsonModuleRevision))
            throw new EccoException("Only JsonModuleRevision can be added as a child to JsonModuleRevision!");
        JsonModuleRevision jsonChild = (JsonModuleRevision) child;
        for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
            if (moduleRevisionCounter.getObject() == jsonChild)
                return null;
        }
        JsonModuleRevisionCounter moduleRevisionCounter = new JsonModuleRevisionCounter(jsonChild);
        this.children.add(moduleRevisionCounter);
        return moduleRevisionCounter;
    }

    @Override
    public ModuleRevisionCounter getChild(ModuleRevision child) {
        for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
            if (moduleRevisionCounter.getObject() == child)
                return moduleRevisionCounter;
        }
        return null;
    }

    @Override
    public Collection<ModuleRevisionCounter> getChildren() {
        return Collections.unmodifiableCollection(this.children);
    }


    @Override
    public Module getObject() {
        return this.module;
    }

    @Override
    public int getCount() {
        return this.count;
    }

    @Override
    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public void incCount() {
        this.count++;
    }

    @Override
    public void incCount(int count) {
        this.count += count;
    }


    @Override
    public String toString() {
        return this.getModuleCounterString();
    }
}
