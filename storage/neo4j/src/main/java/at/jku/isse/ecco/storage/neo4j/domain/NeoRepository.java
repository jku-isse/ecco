package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.dao.NeoEntityFactory;
import at.jku.isse.ecco.storage.neo4j.dao.NeoTransactionStrategy;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;

import java.util.*;

/**
 * Neo4J implementation of {@link Repository}.
 */
@NodeEntity
public final class NeoRepository extends NeoEntity implements Repository, Repository.Op {

    @Relationship(type = "hasFeaturesRp", direction = Relationship.INCOMING)
    private List<NeoFeature> features;

    @Relationship(type = "hasAssociationRp", direction = Relationship.INCOMING)
    private List<Association.Op> associations;

    //@Relationship("hasModuleRp")
    @Transient
    private List<Map<Module, Module>> modules;

    @Relationship(type = "hasModules0Rp", direction = Relationship.INCOMING)
    private List<Module> modules0;

    @Relationship(type = "hasModules1Rp", direction = Relationship.INCOMING)
    private List<Module> modules1;

    @Relationship(type = "hasModules2Rp", direction = Relationship.INCOMING)
    private List<Module> modules2;

    @Property
    private int maxOrder;

    public NeoRepository(NeoTransactionStrategy transactionStrategy) {
        this();
    }

    public NeoRepository() {
        this.features = new ArrayList<>();
        this.associations = new ArrayList<>();
        this.modules = new ArrayList<>();
        this.modules0 = new ArrayList<>();
        this.modules1 = new ArrayList<>();
        this.modules2 = new ArrayList<>();
        this.setMaxOrder(2);
    }

    public void setTransactionStrategy(NeoTransactionStrategy transactionStrategy) {
    }

    @Override
    public Collection<Feature> getFeatures() {
//        Session neoSession = transactionStrategy.getNeoSession();
//        features = neoSession.loadAll(NeoFeature.class, DEPTH).stream().collect(Collectors.toMap(NeoFeature::getId, feature -> feature));
        return Collections.unmodifiableCollection(this.features);
    }

    @Override
    public Collection<Association.Op> getAssociations() {
//        Session neoSession = transactionStrategy.getNeoSession();
//        associations = neoSession.loadAll(NeoAssociation.Op.class, DEPTH);
        return Collections.unmodifiableCollection(this.associations);
    }

    @Override
    public Collection<? extends Module> getModules(int order) {
        //return Collections.unmodifiableCollection(this.modules.get(order).values());
        if (order <= 0) {
            return Collections.unmodifiableCollection(modules0);
        } else if (order == 1) {
            return Collections.unmodifiableCollection(modules1);
        } else {
            return Collections.unmodifiableCollection(modules2);
        }
    }


    @Override
    public Feature getFeature(String id) {
        return this.features.stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Feature addFeature(String id, String name) {
        if (this.features.stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null) != null)
            return null;
        NeoFeature feature = new NeoFeature(id, name);
        this.features.add(feature);
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
        }
    }


    @Override
    public EntityFactory getEntityFactory() {
        return new NeoEntityFactory();
    }


    @Override
    public Module getModule(Feature[] pos, Feature[] neg) {
        NeoModule queryModule = new NeoModule(pos, neg);
        //return this.modules.get(queryModule.getOrder()).get(queryModule);
        if (queryModule.getOrder() <= 0) {
            int index = this.modules0.indexOf(queryModule);
            return index != -1 ? modules0.get(index) : null;
        } else if (queryModule.getOrder() == 1) {
            int index = this.modules1.indexOf(queryModule);
            return index != -1 ? modules1.get(index) : null;
        } else {
            int index = this.modules2.indexOf(queryModule);
            return index != -1 ? modules2.get(index) : null;
        }
    }

    @Override
    public Module addModule(Feature[] pos, Feature[] neg) {
        NeoModule module = new NeoModule(pos, neg);
        if (this.modules.get(module.getOrder()).containsKey(module))
            return null;
        this.modules.get(module.getOrder()).put(module, module);

        if (module.getOrder() <= 0) {
            this.modules0.add(module);
        } else if (module.getOrder() == 1) {
            this.modules1.add(module);
        } else {
            this.modules2.add(module);
        }

        return module;
    }

}
