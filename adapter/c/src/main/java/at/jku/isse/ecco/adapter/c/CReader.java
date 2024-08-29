package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.c.translator.CEccoVisitor;
import at.jku.isse.ecco.adapter.c.parser.generated.CLexer;
import at.jku.isse.ecco.adapter.c.parser.generated.CParser;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
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
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            VevosFileConditionContainer fileConditionContainer = vevosConditionHandler.getFileSpecificPresenceConditions(path);
            Node.Op pluginNode = addPluginNode(nodes, path);
            Path absolutePath = base.resolve(path);
            Node.Op node = this.parseFile(absolutePath, fileConditionContainer, path);
            pluginNode.addChild(node);
            nodes.add(pluginNode);
        }
        return nodes;
    }

    private Node.Op addPluginNode(Set<Node.Op> nodes, Path path){
        Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
        Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
        nodes.add(pluginNode);
        return pluginNode;
    }

    private Node.Op parseFile(Path absolutePath, VevosFileConditionContainer fileConditionContainer, Path relPath){
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);
            CEccoVisitor translator = new CEccoVisitor(lines, this.entityFactory, fileConditionContainer, relPath);
            CParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.translationUnit();
            return translator.translate(tree);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CParser createParser(Path absolutePath){
        try {
            CharStream contentStream = CharStreams.fromFileName(String.valueOf(absolutePath));
            CLexer lexer = new CLexer(contentStream);
            // in order to suppress log output
            lexer.removeErrorListeners();
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            return new CParser(tokens);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
