package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.SettingsDao;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlPluginEntityFactory;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlRemote;
import com.google.inject.Inject;

import java.util.*;

import static java.util.Objects.requireNonNull;

public class XmlSettingsDao implements SettingsDao {

    private final XmlPluginTransactionStrategy transactionStrategy;

    @Inject
    public XmlSettingsDao(XmlPluginTransactionStrategy transactionStrategy, final XmlPluginEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public Collection<Remote> loadAllRemotes() {
        return new ArrayList<>(transactionStrategy.getOrLoadRepository().getRemoteIndex().values());
    }

    @Override
    public Remote loadRemote(String name) {
        requireNonNull(name);
        assert !name.isEmpty() : "Expected a non-empty name!";

        return transactionStrategy.getOrLoadRepository().getRemoteIndex().get(name);
    }

    @Override
    public Remote storeRemote(Remote remote) {
        requireNonNull(remote);
        final XmlRemote xmlRemote = (XmlRemote) remote;
        Object returnVal = transactionStrategy.getOrLoadRepository().getRemoteIndex().putIfAbsent(xmlRemote.getName(), xmlRemote);
        assert returnVal == null;
        return xmlRemote;
    }

    @Override
    public void removeRemote(String name) {
        requireNonNull(name);
        Object returnVal = transactionStrategy.getOrLoadRepository().getRemoteIndex().remove(name);
        assert returnVal != null;
    }

    @Override
    public Map<String, String> loadPluginMap() {
        return Collections.unmodifiableMap(transactionStrategy.getOrLoadRepository().getPluginMap());
    }

    @Override
    public void addPluginMapping(String pattern, String pluginId) {
        requireNonNull(pattern);
        assert !pattern.isEmpty();
        Object returnVal = transactionStrategy.getOrLoadRepository().getPluginMap().putIfAbsent(pattern, pluginId);
        assert returnVal == null;
    }

    @Override
    public void removePluginMapping(String pattern) {
        assert pattern != null && !pattern.isEmpty();
        Object returnVal = transactionStrategy.getOrLoadRepository().getPluginMap().remove(pattern);
        assert returnVal != null;
    }

    @Override
    public Set<String> loadIgnorePatterns() {
        return Collections.unmodifiableSet(transactionStrategy.getOrLoadRepository().getIgnorePatterns());
    }

    @Override
    public void addIgnorePattern(String ignorePattern) {
        requireNonNull(ignorePattern);
        assert !ignorePattern.isEmpty();
        final boolean elementAdded = transactionStrategy.getOrLoadRepository().getIgnorePatterns().add(ignorePattern);
        assert elementAdded;
    }

    @Override
    public void removeIgnorePattern(String ignorePattern) {
        requireNonNull(ignorePattern);
        assert !ignorePattern.isEmpty();
        final boolean containedElement = transactionStrategy.getOrLoadRepository().getIgnorePatterns().remove(ignorePattern);
        assert containedElement;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public void init() {

    }
}
