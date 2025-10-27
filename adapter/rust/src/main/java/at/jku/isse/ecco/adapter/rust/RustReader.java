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
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class RustReader implements ArtifactReader<Path, Set<Node.Op>> {

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.rs"});
    }

    private final EntityFactory entityFactory;
    private Collection<ReadListener> listeners = new ArrayList<>();

    private RustParser rustParser;

    @Inject
    public RustReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }

    public static class ParserBundle {
        RustLexer lexer;
        RustParser parser;
        CommonTokenStream tokens;

        void ensureInitialized(CharStream cs) {
            if (lexer == null) {
                lexer = new RustLexer(cs);
                lexer.removeErrorListeners();
                tokens = new CommonTokenStream(lexer);
                parser = new RustParser(tokens);
                parser.removeErrorListeners();
            } else {
                // Reuse existing instances but replace their input sources and reset state
                lexer.setInputStream(cs);
                tokens.setTokenSource(lexer); // BufferedTokenStream / CommonTokenStream
                tokens.seek(0);
                parser.setTokenStream(tokens);
                parser.reset();              // reset parser state
            }
        }
    }

    private static final ThreadLocal<ParserBundle> PARSER_BUNDLE =
            ThreadLocal.withInitial(ParserBundle::new);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();


    @Override
    public String getPluginId() {
        return RustPlugin.class.getName();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = Collections.synchronizedSet(new HashSet<>());
        List<Future<?>> futures = new ArrayList<>();
        for (Path path : input) {
            futures.add(executor.submit(() -> {
                Node.Op pluginNode = addPluginNode(nodes, path);
                Path absolutePath = base.resolve(path);
                CharStream cs;
                try {
                    cs = CharStreams.fromPath(absolutePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                ParserBundle bundle = PARSER_BUNDLE.get();
                bundle.ensureInitialized(cs);
                ParseTree tree = bundle.parser.crate();

                String fileText = cs.toString();
                String[] lines = fileText.split("\n", -1);
                String configuration = getConfigurationString(base);
                RustEccoVisitor translator = new RustEccoVisitor(pluginNode, lines, this.entityFactory, path, configuration);
                translator.translate(tree);
            }));
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

    private RustParser createParser(Path absolutePath) {
        try {
            CharStream charstream = CharStreams.fromFileName(String.valueOf(absolutePath));
            RustLexer lexer = new RustLexer(charstream);
            // in order to suppress log output
            lexer.removeErrorListeners();
            TokenStream tokenStream = new CommonTokenStream(lexer);
            return new RustParser(tokenStream);
        } catch (IOException e) {
            throw new EccoException("Failed to read file: " + absolutePath, e);
        }
    }

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

    private String getConfigurationString(Path base) {
        Path configurationPath = base.resolve(".config");
        if (!Files.exists(configurationPath)){
            return "";
        }

        try (Stream<String> stream = Files.lines(configurationPath)) {
            List<String> fileLines = stream.toList();
            return fileLines.get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
