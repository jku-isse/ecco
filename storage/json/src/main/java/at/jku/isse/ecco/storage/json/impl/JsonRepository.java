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
import com.jsoniter.annotation.JsonIgnore;
import com.jsoniter.output.JsonStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JsonRepository implements Repository.Op {

    private static final int BUFFERED_INPUTSTREAM_BUFFER_SIZE = 8192;

    private Map<String, Feature> features;
    private Collection<Association.Op> associations = new ArrayList<>();
    private List<Map<Module, Module>> modules = new ArrayList<>();
    private int maxOrder;
    private Map<Integer, MemCommit> commitIndex;
    private Map<String, MemVariant> variantIndex;
    private Map<String, MemRemote> remoteIndex;
    private Set<String> ignorePatterns;
    private Map<String, String> pluginMap;

    @JsonIgnore
    private transient final JsonPluginEntityFactory artifactFactory = new JsonPluginEntityFactory();

    private <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    private <E> Set<E> newSet() {
        return new HashSet<>();
    }

    //Needs to be public
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

    public static JsonRepository loadFromDisk(Path storedRepo) throws IOException {
        if (!Files.exists(storedRepo))
            throw new FileNotFoundException("No repository can be found at '" + storedRepo + '\'');

        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(storedRepo))) {
            ZipEntry nextEntryName;
            boolean found = false;
            while (!found && (nextEntryName = zipStream.getNextEntry()) != null) {
                found = nextEntryName.getName().equals(ZIP_ENTRY_NAME);
            }

            java.util.Scanner s = new java.util.Scanner(zipStream).useDelimiter("\\A");
            String debug = s.hasNext() ? s.next() : "";

            final JsonRepository loaded = JsonIterator.parse(debug).read(JsonRepository.class);

            System.out.println("Loaded repo '" + loaded + "' from: " + storedRepo);
            return loaded;
        }

    }

    private static String ZIP_ENTRY_NAME = "ecco.db.json";

    public void storeRepo(Path storageFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(storageFile, StandardOpenOption.CREATE_NEW))) {
            zos.putNextEntry(new ZipEntry(ZIP_ENTRY_NAME));
            JsonStream.serialize(this, zos);
            System.out.println("Stored repo '" + this + "' to " + storageFile);
        }
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
    @JsonIgnore
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
        Module module = artifactFactory.createModule(pos, neg);
        if (this.modules.get(module.getOrder()).containsKey(module))
            return null;
        this.modules.get(module.getOrder()).put(module, module);
        return module;
    }
}
