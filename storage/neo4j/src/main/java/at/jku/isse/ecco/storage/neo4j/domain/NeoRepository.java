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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4J implementation of {@link Repository}.
 */
@NodeEntity
public final class NeoRepository extends NeoEntity implements Repository, Repository.Op {

    @Relationship(type = "hasFeaturesRp")
    private List<NeoFeature> features;

    @Relationship(type = "hasAssociationRp", direction = Relationship.INCOMING)
    private Set<NeoAssociation.Op> associations;

    @Relationship(type = "hasModulesRp", direction = Relationship.INCOMING)
    private List<NeoModule> modules;

    @Property
    private int maxOrder;

    public NeoRepository(NeoTransactionStrategy transactionStrategy) {
        this();
    }

    public NeoRepository() {
        this.features = new ArrayList<>();
        this.associations = new HashSet<>();
        this.modules = new ArrayList<>();
        this.setMaxOrder(2);
    }

    @Override
    public Collection<NeoFeature> getFeatures() {
        return this.features;
    }

    @Override
    public Collection<NeoAssociation.Op> getAssociations() {
        return this.associations;
    }

    @Override
    public Collection<? extends Module> getModules(int order) {
        List<NeoModule> orderModules = this.modules.stream().filter(m -> m.getOrder() == order).collect(Collectors.toList());
        return orderModules;
    }

    public List<NeoModule> getModules() {
        return this.modules;
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
    public void addAssociation(NeoAssociation.Op association) {
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
    }


    @Override
    public EntityFactory getEntityFactory() {
        return new NeoEntityFactory();
    }


    @Override
    public Module getModule(Feature[] pos, Feature[] neg) {
        NeoModule queryModule = new NeoModule(pos, neg, this);
        int index = this.modules.indexOf(queryModule);
        if (index != -1) {
            return this.modules.get(index);
        } else {
            return null;
        }
    }

    @Override
    public Module addModule(Feature[] pos, Feature[] neg) {
        NeoModule module = new NeoModule(pos, neg, this);
        if (this.modules.contains(module))
            return null;
        this.modules.add(module);
        return module;
    }

    public void setModules(List<NeoModule> modules) {
        this.modules = modules;
    }

}
