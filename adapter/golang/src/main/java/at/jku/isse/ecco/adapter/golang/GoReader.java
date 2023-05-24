package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.golang.antlr.GoLexer;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class GoReader implements ArtifactReader<Path, Set<Node.Op>> {
    private static final Map<Integer, String[]> prioritizedPatterns =
            Collections.singletonMap(1, new String[]{"**.go"});

    private final EntityFactory entityFactory;

    @Inject
    public GoReader(final EntityFactory entityFactory) {
        this.entityFactory = entityFactory;
    }


    @Override
    public String getPluginId() {
        return new GoPlugin().getPluginId();
    }

    /**
     * Returns a map of filename patterns that have a greater significance to the GoReader
     * @return filename patterns to be prioritized
     */
    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return prioritizedPatterns;
    }

    /**
     * Reads every file from the input argument relative to the path passed in the base argument.
     * Returns a set of Node operands that represent Golang source code.
     * Expects input files to be Golang source files
     *
     * @param base Path that contains the input files
     * @param input Paths to input files relative to the base path
     * @return A set of Node operands representing Golang source code
     */
    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        final Set<Node.Op> resultNodes = new LinkedHashSet<>();

        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            Node.Op pluginArtifactNode = createFileNode(path);

            resultNodes.add(pluginArtifactNode);

            try {
                parseGoFile(resultNodes, resolvedPath);
            } catch (IOException e) {
                throw new EccoException(e);
            }
        }

        return resultNodes;
    }

    private void parseGoFile(Set<Node.Op> resultNodes, Path resolvedPath) throws IOException {
        GoLexer lexer = new GoLexer(CharStreams.fromPath(resolvedPath, StandardCharsets.UTF_8));
        Token golangToken = lexer.nextToken();

        while(golangToken.getType() != Token.EOF) {
            Node.Op tokenNode = createTokenNode(golangToken);

            resultNodes.add(tokenNode);

            golangToken = lexer.nextToken();
        }
    }

    private Node.Op createTokenNode(Token golangToken) {
        Artifact.Op<TokenArtifactData> tokenArtifactData =
            this.entityFactory.createArtifact(new TokenArtifactData(golangToken.getText(), golangToken.getLine(), golangToken.getCharPositionInLine()));
        return this.entityFactory.createOrderedNode(tokenArtifactData);
    }

    private Node.Op createFileNode(Path path) {
        Artifact.Op<PluginArtifactData> pluginArtifactData =
                this.entityFactory.createArtifact(new PluginArtifactData(getPluginId(), path));
        return this.entityFactory.createNode(pluginArtifactData);
    }

    /**
     * @see #read(Path, Path[])
     *
     * @param input Paths to input files relative to current working directory
     * @return A set of Node operands representing Golang source code
     */
    @Override
    public Set<Node.Op> read(Path[] input) {
        return null;
    }

    /**
     * Adds a <a href="#{@link}">{@link ReadListener}</a> that
     * is notified everytime a file has been read.
     *
     * @see ReadListener
     * @param listener Instance of ReadListener to be notified
     */
    @Override
    public void addListener(ReadListener listener) {

    }

    /**
     * Removes a <a href="#{@link}">{@link ReadListener}</a> that
     * is notified everytime a file has been read.
     * @see #addListener(ReadListener)
     * @see ReadListener
     * @param listener Instance of ReadListener to remove
     */
    @Override
    public void removeListener(ReadListener listener) {

    }
}
