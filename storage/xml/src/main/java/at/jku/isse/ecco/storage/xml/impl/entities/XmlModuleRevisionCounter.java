package at.jku.isse.ecco.storage.xml.impl.entities;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkNotNull;

public class XmlModuleRevisionCounter implements ModuleRevisionCounter, Serializable {

    private XmlModuleRevision moduleRevision;
    private int count;


    public XmlModuleRevisionCounter(XmlModuleRevision moduleRevision) {
        checkNotNull(moduleRevision);
        this.moduleRevision = moduleRevision;
        this.count = 0;
    }


    @Override
    public XmlModuleRevision getObject() {
        return this.moduleRevision;
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
        return this.getModuleRevisionCounterString();
    }
}
