package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class PythonReader implements ArtifactReader<Path, Set<Node.Op>> {

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(1, new String[]{"**.py", "**.ipynb", "**.json"});
    }
    private static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    protected final EntityFactory entityFactory;
    @Inject
    public PythonReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return PythonPlugin.class.getName();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path[] input) { return this.read(Paths.get("."), input); }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();

        PythonParser.Reader parser = PythonParserFactory.getParser();

        try {
            if (parser == null) {
                throw new IOException("no parser found");
            }
            parser.init();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            throw new RuntimeException("could not initialize parser", e);
        }

        for (Path path : input) {
            // Absolute path for the parser required
            Path pythonFile = base.resolve(path);
            // as ECCO is still in development, retrieving relative or absolute path changed
            // once, check is to be save for potential future changes
            if (pythonFile.equals(path)) {
                // for the Plug-in artifact nodes, relative path is required
                path = base.relativize(path);
            }

            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
            nodes.add(pluginNode);

            Node.Op head = parser.parse(pythonFile, entityFactory);

            if (head == null) {
                LOGGER.log(Level.SEVERE, "parser returned no node, file {0}", pythonFile);
            } else {
                pluginNode.addChild(head);
            }

            listeners.forEach(l -> l.fileReadEvent(pythonFile, this));
        }

        parser.shutdown();
        return nodes;
    }

    private final Collection<ReadListener> listeners = new ArrayList<>();

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }
}
