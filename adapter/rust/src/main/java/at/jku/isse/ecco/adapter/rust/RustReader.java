package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.rust.antlr.RustLexer;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.rust.data.LineArtifactData;
import at.jku.isse.ecco.adapter.rust.translator.RustEccoVisitor;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RustReader implements ArtifactReader<Path, Set<Node.Op>> {

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.rs"});
    }

    private final EntityFactory entityFactory;
    private Collection<ReadListener> listeners = new ArrayList<>();


    @Inject
    public RustReader(EntityFactory entityFactory) {
        if (entityFactory == null) {
            throw new IllegalArgumentException("EntityFactory must not be null");
        }
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

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            Node.Op pluginNode = addPluginNode(nodes, path);
            Path absolutePath = base.resolve(path);
            this.parseFile(pluginNode, absolutePath, path);
            nodes.add(pluginNode);
        }
        return nodes;
    }

    private void parseFile(Node.Op pluginNode, Path absolutePath, Path relPath) {
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);
            RustEccoVisitor translator = new RustEccoVisitor(pluginNode, lines, this.entityFactory, relPath);
            RustParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.crate();
            translator.translate(tree);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RustParser createParser(Path absolutePath) {
        try {
            CharStream charstream = CharStreams.fromFileName(String.valueOf(absolutePath));
            RustLexer lexer = new RustLexer(charstream);
            // in order to suppress log output like in Creader
            lexer.removeErrorListeners();
            TokenStream tokenStream = new CommonTokenStream(lexer);
            return new RustParser(tokenStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + absolutePath, e);
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


//    public static void main(String[] args) {
//        RustReader reader = new RustReader(new MemEntityFactory());
//        Path[] input = {Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/simple.rs")}; // Example path, adjust as needed
//        Set<Node.Op> nodes = reader.read(input);
//        for (Node.Op child : nodes) {
//            List<Node.Op> pluginNodeChildren = (List<Node.Op>) child.getChildren();
//            for (Node.Op node : pluginNodeChildren) {
//                Artifact<?> artifact = node.getArtifact();
//                if (artifact.getData() instanceof FunctionArtifactData) {
//                    Node functionNode = node;
//                    List<Node> lineNodeChildren = (List<Node>) functionNode.getChildren();
//                    for (Node lineNode : lineNodeChildren){
//                        LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();
//                        System.out.println(lineArtifactData.getLine());
//                    }
//                }
//                if (artifact.getData() instanceof LineArtifactData) {
//                    LineArtifactData lineArtifactData = (LineArtifactData) artifact.getData();
//                    System.out.println(lineArtifactData.getLine());
//                }
//            }
//        }
//    }

}
