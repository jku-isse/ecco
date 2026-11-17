package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.golang.antlr.GoParser;
import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.adapter.ReadListener;
import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.adapter.view.SourceRenderer;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GoReader implements ArtifactReader<Path, Set<Node.Op>>, SourceRenderer {

    /**
     * The tokens in the order of their rows and columns, as the writer joins them (the whitespace
     * tokens come last among the file's children), each token node with its text.
     */
    @Override
    public RenderedSource render(Node fileNode) {
        List<Node> tokens = new ArrayList<>();
        collectTokens(fileNode, tokens);
        tokens.sort(Comparator.comparingInt((Node n) -> ((TokenArtifactData) n.getArtifact().getData()).getRow())
                .thenComparingInt(n -> ((TokenArtifactData) n.getArtifact().getData()).getColumn()));
        StringBuilder text = new StringBuilder();
        List<RenderedSource.Span> spans = new ArrayList<>();
        for (Node token : tokens) {
            int start = text.length();
            text.append(((TokenArtifactData) token.getArtifact().getData()).getToken());
            spans.add(new RenderedSource.Span(token, start, text.length()));
        }
        return new RenderedSource(text.toString(), spans);
    }

    private static void collectTokens(Node node, List<Node> tokens) {
        for (Node child : node.getChildren()) {
            if (child.getArtifact() != null && child.getArtifact().getData() instanceof TokenArtifactData)
                tokens.add(child);
            else
                collectTokens(child, tokens);
        }
    }

    private static final Map<Integer, String[]> prioritizedPatterns =
            Collections.singletonMap(1, new String[]{"**.go"});

    private final EntityFactory entityFactory;
    private final List<ReadListener> listeners = new LinkedList<>();

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
     *
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
     * @param base  Path that contains the input files
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
                parseGoFile(pluginArtifactNode, resolvedPath);
            } catch (IOException e) {
                throw new EccoException(e);
            }

            listeners.forEach(listener -> listener.fileReadEvent(resolvedPath, this));
        }

        return resultNodes;
    }

    private void parseGoFile(Node.Op pluginArtifactNode, Path resolvedPath) throws IOException {
        FlattenableGoLexer lexer = new FlattenableGoLexer(CharStreams.fromPath(resolvedPath, StandardCharsets.UTF_8));
        // Tokens are removed from this list as they are added to the tree
        // Since the parser does not preserve whitespace tokens, these will be left in this list
        // These whitespace tokens are then added to the pluginNode after processing the AST
        List<Token> tokenList = lexer.flat();
        GoParser parser = new GoParser(new CommonTokenStream(lexer));
        GoParser.SourceFileContext sourceFileTree = parser.sourceFile();


        parseContext(pluginArtifactNode, sourceFileTree, tokenList);

        while (!tokenList.isEmpty()) {
            Token whitespaceToken = tokenList.remove(0);
            pluginArtifactNode.addChild(createTokenNode(whitespaceToken));
        }
    }

    private void parseContext(Node.Op rootNode, ParseTree tree, List<Token> tokenList) {
        for (int i = 0; i < tree.getChildCount(); i++) {
            ParseTree childTree = tree.getChild(i);

            if (childTree instanceof RuleContext) {
                parseContext(rootNode, childTree, tokenList);
            } else if (childTree instanceof TerminalNode) {
                TerminalNode terminalNode = (TerminalNode) childTree;
                Token terminalSymbol = terminalNode.getSymbol();

                // EOF token should also be removed
                tokenList.removeIf(token -> token.getLine() == terminalSymbol.getLine() &&
                        token.getCharPositionInLine() == terminalSymbol.getCharPositionInLine());

                if (terminalSymbol.getType() == Token.EOF) {
                    // Ignore EOF symbol in resulting tree
                    continue;
                }

                Node.Op tokenNode = createTokenNode(terminalSymbol);
                rootNode.addChild(tokenNode);
            }
        }
    }

    private Node.Op createTokenNode(Token golangToken) {
        Artifact.Op<TokenArtifactData> tokenArtifactData =
                this.entityFactory.createArtifact(new TokenArtifactData(golangToken.getText(), golangToken.getLine(), golangToken.getCharPositionInLine()));
        return this.entityFactory.createNode(tokenArtifactData);
    }

    private Node.Op createFileNode(Path path) {
        Artifact.Op<PluginArtifactData> pluginArtifactData =
                this.entityFactory.createArtifact(new PluginArtifactData(getPluginId(), path));
        return this.entityFactory.createNode(pluginArtifactData);
    }

    /**
     * @param input Paths to input files relative to current working directory
     * @return A set of Node operands representing Golang source code
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
        listeners.add(listener);
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
        listeners.remove(listener);
    }
}
