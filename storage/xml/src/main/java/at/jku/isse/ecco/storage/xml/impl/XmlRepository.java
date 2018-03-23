package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlCommit;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlPluginEntityFactory;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlRemote;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.io.xml.CompactWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class XmlRepository implements Repository.Op {

    private Map<String, Feature> features;
    private Collection<Association.Op> associations = new ArrayList<>();
    private List<Map<Module, Module>> modules = new ArrayList<>();
    private int maxOrder;
    private Map<Integer, XmlCommit> commitIndex;
    private Map<String, XmlRemote> remoteIndex;
    private Set<String> ignorePatterns;
    private Map<String, String> pluginMap;


    private static final XmlPluginEntityFactory artifactFactory = new XmlPluginEntityFactory();

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
        ignorePatterns = newSet();
        pluginMap = newMap();
        features = newMap();

    }

    public void rollbackTo(XmlRepository other) {
        features = other.features;
        associations = other.associations;
        modules = other.modules;
        maxOrder = other.maxOrder;
        commitIndex = other.commitIndex;
        remoteIndex = other.remoteIndex;
        ignorePatterns = other.ignorePatterns;
        pluginMap = other.pluginMap;
    }

    public Map<Integer, XmlCommit> getCommitIndex() {
        return commitIndex;
    }


    public Map<String, XmlRemote> getRemoteIndex() {
        return remoteIndex;
    }

    public Set<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public Map<String, String> getPluginMap() {
        return pluginMap;
    }

    public static XmlRepository loadFromDisk(Path storedRepo) throws IOException {
        if (!Files.exists(storedRepo))
            throw new FileNotFoundException("No repository can be found at '" + storedRepo + '\'');

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(storedRepo));
             BufferedReader repoStream = new BufferedReader(new InputStreamReader(zis))) {
            boolean found = false;
            ZipEntry cur = null;
            while (!found && (cur = zis.getNextEntry()) != null)
                found = ZIP_NAME.equals(cur.getName());
            if (cur == null)
                throw new UnsupportedOperationException("Unable to find the database in the ZIP file");

            final XmlRepository loaded = (XmlRepository) getSerializer().fromXML(repoStream);
            return loaded;
        }
    }

    private static final String ZIP_NAME = "ecco.xml";

    public void storeRepo(Path storageFile) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(storageFile, StandardOpenOption.CREATE_NEW));
             BufferedWriter repoStorage = new BufferedWriter(new OutputStreamWriter(zipOut, StandardCharsets.UTF_8))) {
            zipOut.putNextEntry(new ZipEntry(ZIP_NAME));
            getSerializer().marshal(this, new CompactWriter(repoStorage));
        }
    }


    private static XStream getSerializer() {
        XStream xStream = new XStream(new PureJavaReflectionProvider());
        XStream.setupDefaultSecurity(xStream);
        xStream.allowTypesByWildcard(new String[]{
                "**"
        });
        return xStream;
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
