package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.c.translator.CEccoVisitor;
import at.jku.isse.ecco.adapter.c.parser.generated.CLexer;
import at.jku.isse.ecco.adapter.c.parser.generated.CParser;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.dispatch.TextFileFormat;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.featuretrace.parser.VevosConditionHandler;
import at.jku.isse.ecco.featuretrace.parser.VevosFileConditionContainer;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class CReader implements ArtifactReader<Path, Set<Node.Op>> {

    protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());

    private final EntityFactory entityFactory;

    private Collection<ReadListener> listeners = new ArrayList<>();

    private static Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.c", "**.h"});
    }

    @Inject
    public CReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() { return CPlugin.class.getName(); }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() { return Collections.unmodifiableMap(prioritizedPatterns); }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        VevosConditionHandler vevosConditionHandler = new VevosConditionHandler(base);
        String configuration = this.getConfigurationString(base);
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            VevosFileConditionContainer fileConditionContainer = vevosConditionHandler.getFileSpecificPresenceConditions(path);
            Path absolutePath = base.resolve(path);
            TextFileFormat.Decoded decoded;
            try {
                // charset/line separator/final newline are recorded so checkout reproduces the file byte
                // for byte - see TextFileFormat
                decoded = TextFileFormat.read(absolutePath);
            } catch (IOException e) {
                throw new EccoException("Could not read file: " + absolutePath, e);
            }
            Node.Op pluginNode = addPluginNode(nodes, path, decoded);
            this.parseFile(pluginNode, decoded, fileConditionContainer, path, configuration);
            nodes.add(pluginNode);
        }
        return nodes;
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

    private Node.Op addPluginNode(Set<Node.Op> nodes, Path path, TextFileFormat.Decoded decoded){
        PluginArtifactData pluginArtifactData = new PluginArtifactData(this.getPluginId(), path);
        decoded.recordOn(pluginArtifactData);
        Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(pluginArtifactData);
        Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
        nodes.add(pluginNode);
        return pluginNode;
    }

    private void parseFile(Node.Op pluginNode,
                           TextFileFormat.Decoded decoded,
                           VevosFileConditionContainer fileConditionContainer,
                           Path relPath,
                           String configuration){
        String[] lines = decoded.lines().toArray(new String[0]);
        CEccoVisitor translator = new CEccoVisitor(pluginNode, lines, this.entityFactory, fileConditionContainer, relPath, configuration);
        // the parser sees the same lines (joined with \n, so its line numbers match them whatever the
        // file's line separator or charset)
        CParser parser = this.createParser(String.join("\n", lines));
        // in order to suppress log output
        parser.removeErrorListeners();
        ParseTree tree = parser.translationUnit();
        translator.translate(tree);
    }

    private CParser createParser(String content){
        CharStream contentStream = CharStreams.fromString(content);
        CLexer lexer = new CLexer(contentStream);
        // in order to suppress log output
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new CParser(tokens);
    }

    @Override
    public Set<Node.Op> read(Path[] input) { return this.read(Paths.get("."), input); }

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }
}
