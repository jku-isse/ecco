package at.jku.isse.ecco.storage.json.impl;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.json.impl.entities.JsonPluginEntityFactory;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class JsonRepository implements Repository.Op {

    private Map<String, Feature> features;
    private Collection<Association.Op> associations = new ArrayList<>();
    private List<Map<Module, Module>> modules = new ArrayList<>();
    private int maxOrder;
    private Map<Integer, MemCommit> commitIndex;
    private Map<String, MemVariant> variantIndex;
    private Map<String, MemRemote> remoteIndex;
    private Set<String> ignorePatterns;
    private Map<String, String> pluginMap;
    private transient final JsonPluginEntityFactory artifactFactory = new JsonPluginEntityFactory();

    private <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    private <E> Set<E> newSet() {
        return new HashSet<>();
    }

    public JsonRepository() {
        commitIndex = newMap();
        variantIndex = newMap();
        remoteIndex = newMap();
        ignorePatterns = newSet();
        pluginMap = newMap();
        features = newMap();

    }

    public void rollbackTo(JsonRepository other) {
        features = other.features;
        associations = other.associations;
        modules = other.modules;
        maxOrder = other.maxOrder;
        commitIndex = other.commitIndex;
        variantIndex = other.variantIndex;
        remoteIndex = other.remoteIndex;
        ignorePatterns = other.ignorePatterns;
        pluginMap = other.pluginMap;
    }

    public Map<Integer, MemCommit> getCommitIndex() {
        return commitIndex;
    }

    public Map<String, MemVariant> getVariantIndex() {
        return variantIndex;
    }

    public Map<String, MemRemote> getRemoteIndex() {
        return remoteIndex;
    }

    public Set<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public Map<String, String> getPluginMap() {
        return pluginMap;
    }

    public static JsonRepository fromRepo(Path storedRepo) throws IOException {
        if (!Files.exists(storedRepo))
            return new JsonRepository();

        byte[] data = Files.readAllBytes(storedRepo);

        JsonRepository loaded = JsonIterator.deserialize(data, JsonRepository.class);
        System.out.println("Loaded repo '" + loaded + "' from: " + storedRepo);
        return loaded;
    }

    public void storeRepo(Path storageFile) throws IOException {
        JsonStream.serialize(this, Files.newOutputStream(storageFile, StandardOpenOption.CREATE_NEW));
        System.out.println("Stored repo '" + this + "' to " + storageFile);

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
        return features.putIfAbsent(id, getEntityFactory().createFeature(id, name));
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
    }

    @Override
    public EntityFactory getEntityFactory() {
        return artifactFactory;
    }

    @Override
    public Module getModule(Feature[] pos, Feature[] neg) {
        Module queryModule = artifactFactory.createModule(pos, neg);
        return modules.get(queryModule.getOrder()).get(queryModule);
    }

    @Override
    public Module addModule(Feature[] pos, Feature[] neg) {
        final Module module = artifactFactory.createModule(pos, neg);
        return modules.get(module.getOrder()).putIfAbsent(module, module);
    }
}
