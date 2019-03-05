package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.dao.NeoEntityFactory;
import org.eclipse.collections.impl.factory.Maps;
import org.neo4j.ogm.annotation.*;

import java.util.*;

/**
 * Neo4J implementation of {@link Repository}.
 */
@NodeEntity
public final class NeoRepository extends NeoEntity implements Repository, Repository.Op {

    @Relationship("hasFeaturesRp")
    private Map<String, Feature> features;

    @Relationship("hasAssociationRp")
    private Collection<Association.Op> associations;

    @Relationship("hasModuleRp")
    private List<Map<Module, Module>> modules;

    @Property
    private int maxOrder;

    public NeoRepository() {
        this.features = new HashMap<>();
        //this.features = Maps.mutable.empty();
        this.associations = new ArrayList<>();
        this.modules = new ArrayList<>();
        this.setMaxOrder(2);
    }

    @Override
    public Collection<Feature> getFeatures() {
        return Collections.unmodifiableCollection(this.features.values());
    }

    @Override
    public Collection<Association.Op> getAssociations() {
        return Collections.unmodifiableCollection(this.associations);
    }

    @Override
    public Collection<? extends Module> getModules(int order) {
        return Collections.unmodifiableCollection(this.modules.get(order).values());
    }


    @Override
    public Feature getFeature(String id) {
        return this.features.get(id);
    }

    @Override
    public Feature addFeature(String id, String name) {
        if (this.features.containsKey(id))
            return null;
        NeoFeature feature = new NeoFeature(id, name);
        this.features.put(feature.getId(), feature);
        return feature;
    }


    @Override
    public void addAssociation(Association.Op association) {
        this.associations.add(association);
    }

    @Override
    public void removeAssociation(Association.Op association) {
        this.associations.remove(association);
    }


    @Override
    public int getMaxOrder() {
        return this.maxOrder;
    }

    @Override
    public void setMaxOrder(int maxOrder) {
        this.maxOrder = maxOrder;
        for (int order = this.modules.size(); order <= this.maxOrder; order++) {
            this.modules.add(new HashMap<>());
            //this.modules.add(Maps.mutable.empty());
        }
    }


    @Override
    public EntityFactory getEntityFactory() {
        return new NeoEntityFactory();
    }


    @Override
    public Module getModule(Feature[] pos, Feature[] neg) {
        NeoModule queryModule = new NeoModule(pos, neg);
        return this.modules.get(queryModule.getOrder()).get(queryModule);
    }

    @Override
    public Module addModule(Feature[] pos, Feature[] neg) {
        NeoModule module = new NeoModule(pos, neg);
        if (this.modules.get(module.getOrder()).containsKey(module))
            return null;
        this.modules.get(module.getOrder()).put(module, module);
        return module;
    }

}
