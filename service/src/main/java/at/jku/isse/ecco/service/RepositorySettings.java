package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The per-repository settings file ({@link EccoService#SETTINGS_FILE_NAME}) of one
 * {@link EccoService}: what each plugin chose when the repository was created, and so how its files
 * are read. Created with the repository, taken over by a fork, compared before a merge.
 */
final class RepositorySettings {

    private final EccoService owner;

    RepositorySettings(EccoService owner) {
        this.owner = owner;
    }

    private Path file() {
        return this.owner.getRepositoryDir().resolve(EccoService.SETTINGS_FILE_NAME);
    }

    /** Records each plugin's settings for a new repository (see ArtifactPlugin#newRepositorySettings). */
    void writeForNewRepository() throws IOException {
        Properties settings = new Properties();
        for (ArtifactPlugin plugin : this.owner.getArtifactPlugins()) {
            settings.putAll(plugin.newRepositorySettings());
        }
        write(this.file(), settings, "Chosen when this repository was created; changing them changes how files are read.");
    }

    /**
     * Writes [settings] to [file] as a whole or not at all: into a file beside it, moved over it. A
     * failure - a full disk, a crash - while the file itself was being written left it truncated, and
     * the repository read files without the settings they were committed with.
     */
    static void write(Path file, Properties settings, String comment) throws IOException {
        Path written = Files.createTempFile(file.getParent(), EccoService.SETTINGS_FILE_NAME.toString(), ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(written)) {
                settings.store(out, comment);
            }
            try {
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(written);
        }
    }

    /**
     * Records a plugin's settings, as for a new repository, in a repository that has none of them and
     * none of that plugin's files: one created before the plugin had settings, or by a service without
     * the plugin (ecco-rest without LilyPond). Nothing stored can disagree with them, and without them
     * the plugin would read the repository as one from before - LilyPond refusing every commit. Called
     * before a commit reads its files.
     */
    void adopt(Repository repository) throws IOException {
        Path file = this.file();
        Properties settings = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                settings.load(in);
            }
        }
        Set<String> pluginsWithFiles = null;
        boolean adopted = false;
        for (ArtifactPlugin plugin : this.owner.getArtifactPlugins()) {
            Map<String, String> own = plugin.newRepositorySettings();
            if (own.isEmpty() || own.keySet().stream().anyMatch(settings::containsKey))
                continue;
            if (pluginsWithFiles == null)
                pluginsWithFiles = pluginsWithFiles(repository);
            if (pluginsWithFiles.contains(plugin.getPluginId()))
                continue;
            settings.putAll(own);
            adopted = true;
        }
        if (adopted) {
            write(file, settings, "Chosen when this repository was created, or when it got its first file of a plugin; changing them changes how files are read.");
        }
    }

    /** The plugins whose files the repository holds. */
    static Set<String> pluginsWithFiles(Repository repository) {
        Set<String> plugins = new HashSet<>();
        Deque<Node> nodes = new ArrayDeque<>();
        for (Association association : repository.getAssociations())
            nodes.push(association.getRootNode());
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();
            if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data) {
                plugins.add(data.getPluginId());
                continue;
            }
            for (Node child : node.getChildren())
                nodes.push(child);
        }
        return plugins;
    }

    /** The settings in [repositoryDir], empty if it has none. */
    static Map<String, String> read(Path repositoryDir) {
        Path file = repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME);
        Map<String, String> settings = new TreeMap<>();
        if (!Files.exists(file))
            return settings;
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new EccoException("Could not read the repository's settings.", e);
        }
        properties.stringPropertyNames().forEach(key -> settings.put(key, properties.getProperty(key)));
        return settings;
    }

    /** This repository's settings, empty if it has none. */
    Map<String, String> current() {
        return read(this.owner.getRepositoryDir());
    }

    /** The plugins whose files this repository holds - in a read-only transaction of its own. */
    Set<String> pluginsWithFiles() {
        return this.owner.context.read(null, () -> pluginsWithFiles(this.owner.context.repositoryDao.load()));
    }

    /**
     * Checks, before files are merged into a repository, that both read them the same way: for every
     * plugin whose files are merged ([incomingPlugins]), the settings they were read with ([incomingSettings])
     * and the receiving repository's ([receivingSettings]) must agree. Files read differently - with
     * musical tokens and without, say - match nothing of each other, and a later commit would read
     * them all the receiving repository's way. A receiving repository with none of a plugin's settings
     * and none of its files ([receivingPlugins]) takes over the incoming ones, as a fork or a first
     * commit does.
     *
     * @param action "pull from 'origin'", "push to 'origin'" - for the message
     * @return the settings the receiving repository has to take over, empty if none
     * @throws EccoException naming the settings that differ
     */
    Map<String, String> toTakeOver(Set<String> incomingPlugins, Map<String, String> incomingSettings,
                                   Map<String, String> receivingSettings, Set<String> receivingPlugins, String action) {
        Map<String, String> takeOver = new TreeMap<>();
        for (ArtifactPlugin plugin : this.owner.getArtifactPlugins()) {
            Set<String> keys = plugin.newRepositorySettings().keySet();
            if (keys.isEmpty() || !incomingPlugins.contains(plugin.getPluginId()))
                continue;
            Map<String, String> theirs = restrictedTo(incomingSettings, keys);
            Map<String, String> ours = restrictedTo(receivingSettings, keys);
            if (theirs.equals(ours))
                continue;
            if (ours.isEmpty() && !receivingPlugins.contains(plugin.getPluginId())) {
                takeOver.putAll(theirs);
                continue;
            }
            Set<String> differing = new TreeSet<>();
            for (String key : keys)
                if (!Objects.equals(theirs.get(key), ours.get(key)))
                    differing.add(key);
            throw new EccoException("Cannot " + action + ": its " + plugin.getName() + " files were read with "
                    + describe(restrictedTo(theirs, differing)) + ", the receiving repository's with " + describe(restrictedTo(ours, differing))
                    + ". Files read differently cannot be merged.");
        }
        return takeOver;
    }

    private static Map<String, String> restrictedTo(Map<String, String> settings, Set<String> keys) {
        Map<String, String> restricted = new TreeMap<>(settings);
        restricted.keySet().retainAll(keys);
        return restricted;
    }

    private static String describe(Map<String, String> settings) {
        return settings.isEmpty() ? "none of them (a repository from before them)" : settings.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    /** Adds [settings] to this repository's settings file. */
    private void takeOver(Map<String, String> settings) {
        if (settings.isEmpty())
            return;
        Map<String, String> all = new TreeMap<>(this.current());
        all.putAll(settings);
        Properties properties = new Properties();
        properties.putAll(all);
        try {
            write(this.file(), properties, "Chosen when this repository was created, or taken over from the repository its first files of a plugin came from; changing them changes how files are read.");
        } catch (IOException e) {
            throw new EccoException("Could not write the repository's settings.", e);
        }
    }

    /**
     * Ends the transaction of a merge (pull, push), taking over [settings] (see {@link #toTakeOver})
     * first: they were written after it and could fail, a merge stored without the settings its files
     * were read with. Should ending fail, the settings are put back as they were.
     */
    void endTakingOver(Map<String, String> settings) {
        Map<String, String> before = settings.isEmpty() ? null : this.current();
        this.takeOver(settings);
        try {
            this.owner.context.transactionStrategy.end();
        } catch (RuntimeException | Error e) {
            if (before != null) {
                try {
                    this.replace(before);
                } catch (RuntimeException restoring) {
                    e.addSuppressed(restoring);
                }
            }
            throw e;
        }
    }

    /** This repository's settings become [settings]: none at all if it is empty, as from before settings. */
    void replace(Map<String, String> settings) {
        Path file = this.file();
        try {
            if (settings.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            Properties properties = new Properties();
            properties.putAll(settings);
            write(file, properties, "Taken over from the repository this one was forked from; changing them changes how files are read.");
        } catch (IOException e) {
            throw new EccoException("Could not take over the origin's settings.", e);
        }
    }

    /** A fork's settings are its origin's, or none (the behaviour from before settings) if it has none. */
    void inheritFrom(Path originRepositoryDir) {
        try {
            Path origin = originRepositoryDir.resolve(EccoService.SETTINGS_FILE_NAME);
            Path own = this.file();
            if (Files.exists(origin)) {
                Files.copy(origin, own, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(own);
            }
        } catch (IOException e) {
            throw new EccoException("Could not take over the origin's settings.", e);
        }
    }
}
