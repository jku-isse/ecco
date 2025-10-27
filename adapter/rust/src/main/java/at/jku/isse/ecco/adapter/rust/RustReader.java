package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.rust.antlr.RustLexer;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.translator.RustEccoVisitor;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class RustReader implements ArtifactReader<Path, Set<Node.Op>> {
    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.rs"});
    }

    private final EntityFactory entityFactory;
    private final Collection<ReadListener> listeners = new ArrayList<>();

    @Inject
    public RustReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return RustPlugin.class.getName();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    /**
     * Reads Rust source files from the specified base path and input paths,
     * and returns a set of Node representing the parsed source code.
     *
     * @param base  Base directory path
     * @param input Array of input file paths relative to the base path
     * @return A set of Nodes representing Rust source code
     */
    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = Collections.synchronizedSet(new HashSet<>());
        List<Future<?>> futures = new ArrayList<>();
        String configuration = getConfigurationString(base);

        // process each file in parallel
        for (Path path : input) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                futures.add(executor.submit(() -> {
                    Path absolutePath = base.resolve(path);
                    CharStream cs;
                    try {
                        cs = CharStreams.fromPath(absolutePath);
                    } catch (IOException e) {
                        throw new EccoException("Failed to read file: " + absolutePath, e);
                    }
                    ParseTree tree = createParseTree(cs);

                    String fileText = cs.toString();
                    String[] lines = fileText.split("\n", -1);
                    Node.Op pluginNode = addPluginNode(nodes, path);
                    RustEccoVisitor translator = new RustEccoVisitor(pluginNode, lines, this.entityFactory, path, configuration);
                    translator.translate(tree);
                }));
            }
        }
        // wait for all tasks to finish
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EccoException("Parsing interrupted", e);
            } catch (ExecutionException e) {
                throw new EccoException("Failed to parse file", e);
            }
        }

        return nodes;
    }

    /**
     * Read configuration string from .config file in base directory
     *
     * @param base Base directory path
     * @return Configuration string, or empty string if no .config file exists
     */
    private String getConfigurationString(Path base) {
        Path configurationPath = base.resolve(".config");
        if (!Files.exists(configurationPath)) {
            return "";
        }
        try (Stream<String> stream = Files.lines(configurationPath)) {
            return stream.findFirst().orElse("");
        } catch (IOException e) {
            throw new EccoException("Failed to read configuration file: " + configurationPath, e);
        }
    }

    /**
     * Create a parse tree from a CharStream
     *
     * @param cs CharStream of Rust source code
     * @return ParseTree representing the Rust source code
     */
    private ParseTree createParseTree(CharStream cs) {
        RustLexer lexer = new RustLexer(cs);
        lexer.removeErrorListeners();
        RustParser parser = new RustParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        return parser.crate();
    }

    /**
     * Adds a plugin node for the given path to the set of nodes.
     *
     * @param nodes Set of Node operands to add the plugin node to
     * @param path  Path of the file being processed
     * @return The created plugin Node operand
     */
    private Node.Op addPluginNode(Set<Node.Op> nodes, Path path) {
        Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
        Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
        nodes.add(pluginNode);
        return pluginNode;
    }

    /**
     * @param input Paths to input files relative to current working directory
     * @return A set of Node operands representing Rust source code
     * @see #read(Path, Path[])
     */
    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }


    /**
     * Adds a {@link ReadListener ReadListener} that
     * is notified everytime a file has been read.
     *
     * @param listener Instance of ReadListener to be notified
     * @see ReadListener
     */
    @Override
    public void addListener(ReadListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        this.listeners.add(listener);
    }

    /**
     * Removes a {@link ReadListener ReadListener} that
     * is notified everytime a file has been read.
     *
     * @param listener Instance of ReadListener to remove
     * @see #addListener(ReadListener)
     * @see ReadListener
     */
    @Override
    public void removeListener(ReadListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        this.listeners.remove(listener);
    }
}
