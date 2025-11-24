package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
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
import at.jku.isse.ecco.util.Location;
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

    private String gitCommitHash;
    private int gitCommitIndex;


    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.c", "**.h", "**.gch"});
    }

    @Inject
    public CReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
        this.gitCommitIndex = -1;
        this.gitCommitHash = null;
    }


    /*public void setGitCommitDetails(Path path) {
        if (!Files.exists(path)) {
            this.gitCommitHash = null;
            this.gitCommitIndex = -1;

            LOGGER.info(this.gitCommitHash);
        }

        try (Stream<String> stream = Files.lines(path)) {
            List<String> fileLines = stream.toList();
            this.gitCommitIndex = Integer.parseInt(fileLines.get(0).split(";")[0]);
            this.gitCommitHash = fileLines.get(0).split(";")[1];

            LOGGER.info(this.gitCommitHash);
        } catch (IOException e) {
            throw new RuntimeException(e);

        }
    }*/

    @Override
    public void SetGitCommitDetails(String contentOfFile) {
        this.gitCommitIndex = Integer.parseInt(contentOfFile.split(";")[0]);
        this.gitCommitHash = contentOfFile.split(";")[1];
    }

    @Override
    public String getPluginId() { return CPlugin.class.getName(); }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() { return Collections.unmodifiableMap(prioritizedPatterns); }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) throws IOException {
        // Initial checks and logging
        if (base == null) {
            throw new EccoException(getPluginId() + ": Reader base path is null!");
        }

        VevosConditionHandler vevosConditionHandler = new VevosConditionHandler(base);
        String configuration = this.getConfigurationString(base);
        Set<Node.Op> nodes = new HashSet<>();

        /*try {
            setGitCommitDetails(base.resolve("gitCommitHash.gch"));
            //Files.deleteIfExists(path.resolve("gitCommitHash.gch"));
        } catch(Exception e) {
            LOGGER.info(e.getMessage());
        }*/

        for (Path path : input) {
            VevosFileConditionContainer fileConditionContainer = vevosConditionHandler.getFileSpecificPresenceConditions(path);
            Node.Op pluginNode = addPluginNode(nodes, path);
            Path absolutePath = base.resolve(path);


            this.parseFile(pluginNode, absolutePath, fileConditionContainer, path, configuration);
            //No need to add location here, it is done in CEccoTranslator at line 76

            nodes.add(pluginNode);
        }

        return nodes;
    }

    private String getConfigurationString(Path base) {
        Path configurationPath = base.resolve(".config");
        if (!Files.exists(configurationPath)){
            return null;
        }

        try (Stream<String> stream = Files.lines(configurationPath)) {
            List<String> fileLines = stream.toList();
            return fileLines.get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Node.Op addPluginNode(Set<Node.Op> nodes, Path path){
        Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
        Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
        nodes.add(pluginNode);
        return pluginNode;
    }

    private void parseFile(Node.Op pluginNode,
                           Path absolutePath,
                           VevosFileConditionContainer fileConditionContainer,
                           Path relPath,
                           String configuration){
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);
            CEccoVisitor translator = new CEccoVisitor(pluginNode, lines, this.entityFactory, fileConditionContainer, relPath, configuration);

            LOGGER.info(this.gitCommitHash + ";" + this.gitCommitIndex);

            translator.setGitCommitHash(this.gitCommitHash);
            translator.setGitCommitIndex(this.gitCommitIndex);

            CParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.translationUnit();
            translator.translate(tree);
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
    public Set<Node.Op> read(Path[] input) throws IOException { return this.read(Paths.get("."), input); }

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }
}
