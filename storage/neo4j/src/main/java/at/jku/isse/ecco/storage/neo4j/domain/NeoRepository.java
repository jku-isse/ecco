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
import org.neo4j.ogm.session.Session;

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

    //@Relationship("hasModuleRp")
    @Transient
    private List<Map<Module, Module>> modules;

    @Relationship(type = "hasModules0Rp", direction = Relationship.INCOMING)
    //@Transient
    private List<Module> modules0;

    @Relationship(type = "hasModules1Rp", direction = Relationship.INCOMING)
    //@Transient
    private List<Module> modules1;

    @Relationship(type = "hasModules2Rp", direction = Relationship.INCOMING)
    //@Transient
    private List<Module> modules2;

    @Transient
    NeoTransactionStrategy transactionStrategy;

    @Property
    private int maxOrder;

    public NeoRepository(NeoTransactionStrategy transactionStrategy) {
        this();
        this.transactionStrategy = transactionStrategy;
    }

    public NeoRepository() {
        this.features = new ArrayList<>();
        this.associations = new HashSet<>();
        this.modules = new ArrayList<>();
        this.modules0 = new ArrayList<>();
        this.modules1 = new ArrayList<>();
        this.modules2 = new ArrayList<>();
        this.setMaxOrder(2);
    }

    public void setTransactionStrategy(NeoTransactionStrategy transactionStrategy) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public Collection<Feature> getFeatures() {
        Session neoSession = transactionStrategy.getNeoSession();
        System.out.println(neoSession);
        System.out.println(transactionStrategy);
        // redundant? neoSession.loadAll(NeoFeature.class, DEPTH).stream().collect(Collectors.toList());

          //does not load revisions - why?
//        Collection<NeoFeature> localFeatures = neoSession.loadAll(this.features, 5);
//        this.features = new ArrayList(localFeatures);

        // we have to overwrite the features because they are not replaced when loaded - why?
        for (int i = 0; i < this.features.size(); i++) {
            NeoFeature actFeature = this.features.get(i);

            // if feature was loaded from db
            if (actFeature.getNeoId() != null) {
                NeoFeature loadedFeature = neoSession.load(NeoFeature.class, actFeature.getNeoId(), 3);
                //this.features.set(i, loadedFeature);
            }
        }

        // same result as above
//        ArrayList<NeoFeature> localFeatures = new ArrayList<>();
//        for (NeoFeature feature: this.features) {
//            NeoFeature feat = neoSession.load(NeoFeature.class, feature.getNeoId(), 2);
//            localFeatures.add(feat);
//        }
//        this.features = localFeatures;

        return Collections.unmodifiableCollection(this.features);
    }

    @Override
    public Collection<NeoAssociation.Op> getAssociations() {
        Session neoSession = transactionStrategy.getNeoSession();
        Collection<NeoAssociation> loadedAss = neoSession.loadAll(NeoAssociation.class).stream().collect(Collectors.toList());
        loadedAss.forEach(a -> {
            NeoAssociation assoc = neoSession.load(NeoAssociation.class, a.getNeoId(), 3);
            System.out.println();
        });
        this.associations.addAll(loadedAss);


        /** set has no fixed order */
        /** loading of artifactTreeRoot */
//        for(NeoAssociation.Op actAssoc : this.associations) {
//            NeoAssociation.Op loadedAssoc = neoSession.load(NeoRootNode.class, actAssoc.getRootNode(), 2);
//            System.out.println();
//        }

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

    public NeoTransactionStrategy getTransactionStrategy() {
        return transactionStrategy;
    }

}
