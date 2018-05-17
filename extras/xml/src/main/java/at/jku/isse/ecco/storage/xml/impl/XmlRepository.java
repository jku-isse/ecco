package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.mem.module.MemModule;

import java.util.*;

public class XmlRepository implements Repository.Op {

    private Map<String, Feature> features;
    private Collection<Association.Op> associations = new ArrayList<>();
    private List<Map<Module, Module>> modules = new ArrayList<>();
    private int maxOrder;
    private Map<String, MemCommit> commitIndex;
    private Map<String, MemRemote> remoteIndex;


    private <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    private <E> Set<E> newSet() {
        return new HashSet<>();
    }

    //Needs to be public
    public XmlRepository() {
        commitIndex = newMap();
        remoteIndex = newMap();
        features = newMap();
    }


    public Map<String, MemCommit> getCommitIndex() {
        return commitIndex;
    }


    public Map<String, MemRemote> getRemoteIndex() {
        return remoteIndex;
    }

    @Override
    public Collection<? extends Feature> getFeatures() {
        return Collections.unmodifiableCollection(features.values());
    }

    @Override
    public Collection<? extends Association.Op> getAssociations() {
        return Collections.unmodifiableCollection(associations);
    }

    @Override
    public Collection<? extends Module> getModules(int order) {
        return Collections.unmodifiableCollection(modules.get(order).values());
    }

    @Override
    public Feature getFeature(String id) {
        return features.get(id);
    }

    @Override
    public Feature addFeature(String id, String name) {
        if (this.features.containsKey(id))
            return null;
        Feature feature = getEntityFactory().createFeature(id, name);
        this.features.put(feature.getId(), feature);
        return feature;
    }

    @Override
    public void addAssociation(Association.Op association) {
        associations.add(association);
    }

    @Override
    public void removeAssociation(Association.Op association) {
        associations.remove(association);
    }

    @Override
    public int getMaxOrder() {
        return maxOrder;
    }

    @Override
    public void setMaxOrder(int maxOrder) {
        this.maxOrder = maxOrder;
        for (int order = this.modules.size(); order <= this.maxOrder; order++)
            this.modules.add(new HashMap<>());
    }

    @Override
    public EntityFactory getEntityFactory() {
        return new MemEntityFactory();
    }

    @Override
    public Module getModule(Feature[] pos, Feature[] neg) {
        Module queryModule = new MemModule(pos, neg);
        return modules.get(queryModule.getOrder()).get(queryModule);
    }

    @Override
    public Module addModule(Feature[] pos, Feature[] neg) {
        Module module = new MemModule(pos, neg);
        if (this.modules.get(module.getOrder()).containsKey(module))
            return null;
        this.modules.get(module.getOrder()).put(module, module);
        return module;
    }
}
