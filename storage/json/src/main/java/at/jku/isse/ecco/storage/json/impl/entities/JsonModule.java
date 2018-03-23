package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class JsonModule implements Module, Serializable {

    private Feature[] pos;
    private Feature[] neg;
    private int count;
    private Map<ModuleRevision, ModuleRevision> revisions;


    public JsonModule(Feature[] pos, Feature[] neg) {
        requireNonNull(pos);
        requireNonNull(neg);
        assert (pos.length > 0);
        this.verify(pos, neg);
        this.pos = pos;
        this.neg = neg;
        this.count = 0;
        this.revisions = new HashMap<>();
    }


    @Override
    public Feature[] getPos() {
        return this.pos;
    }

    @Override
    public Feature[] getNeg() {
        return this.neg;
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
    public Collection<ModuleRevision> getRevisions() {
        return Collections.unmodifiableCollection(this.revisions.values());
    }

    @Override
    public ModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
        if (!this.matchesRevision(pos, neg))
            return null;
        ModuleRevision moduleRevision = new JsonModuleRevision(this, pos, neg);
        if (this.revisions.containsKey(moduleRevision))
            return null;
        this.revisions.put(moduleRevision, moduleRevision);
        return moduleRevision;
    }

    @Override
    public ModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
        return this.revisions.get(new JsonModuleRevision(this, pos, neg));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonModule jsonModule = (JsonModule) o;

        if (this.pos.length != jsonModule.pos.length || this.neg.length != jsonModule.neg.length)
            return false;
        for (int i = 0; i < this.pos.length; i++) {
            boolean found = false;
            for (int j = 0; j < jsonModule.pos.length; j++) {
                if (this.pos[i].equals(jsonModule.pos[j])) {
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        for (int i = 0; i < this.neg.length; i++) {
            boolean found = false;
            for (int j = 0; j < jsonModule.neg.length; j++) {
                if (this.neg[i].equals(jsonModule.neg[j])) {
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 0;
        for (Feature feature : this.pos)
            result += feature.hashCode();
        result *= 31;
        for (Feature feature : this.neg)
            result += feature.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return this.getModuleString();
    }

}
