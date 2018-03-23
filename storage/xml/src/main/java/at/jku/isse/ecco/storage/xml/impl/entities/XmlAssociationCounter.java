package at.jku.isse.ecco.storage.xml.impl.entities;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class XmlAssociationCounter implements AssociationCounter, Serializable {

    private Association association;
    private int count;
    private Collection<XmlModuleCounter> children;


    public XmlAssociationCounter(Association association) {
        checkNotNull(association);
        this.association = association;
        this.count = 0;
        this.children = new ArrayList<>();
    }


    @Override
    public ModuleCounter addChild(Module child) {
        if (!(child instanceof XmlModule))
            throw new EccoException("Only PerstModule can be added as a child to PerstAssociationCounter!");
        XmlModule xmlModule = (XmlModule) child;
        for (ModuleCounter moduleCounter : this.children) {
            if (moduleCounter.getObject() == xmlModule)
                return null;
        }
        XmlModuleCounter moduleCounter = new XmlModuleCounter(xmlModule);
        this.children.add(moduleCounter);
        return moduleCounter;
    }

    @Override
    public ModuleCounter getChild(Module child) {
        for (ModuleCounter moduleCounter : this.children) {
            if (moduleCounter.getObject() == child)
                return moduleCounter;
        }
        return null;
    }

    @Override
    public Collection<ModuleCounter> getChildren() {
        return Collections.unmodifiableCollection(this.children);
    }

    @Override
    public Association getObject() {
        return this.association;
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
        return this.getAssociationCounterString();
    }
}
