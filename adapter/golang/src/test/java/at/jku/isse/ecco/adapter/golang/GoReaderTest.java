package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GoReaderTest {
    @Test
    public void pluginIdIsEqualToGoPluginId() {
        assertEquals(new GoPlugin().getPluginId(), new GoReader(new MemEntityFactory()).getPluginId());
    }

    @Test
    public void prioritizedPatternsIncludeGolangFiles() {
        Map<Integer, String[]> patterns = new GoReader(new MemEntityFactory()).getPrioritizedPatterns();

        assertTrue(patterns.containsKey(Integer.MAX_VALUE));
        assertEquals("*.go", patterns.get(Integer.MAX_VALUE)[0]);
    }

    @Test
    public void prioritizedPatternsIsImmutable() {
        Map<Integer, String[]> patterns = new GoReader(new MemEntityFactory()).getPrioritizedPatterns();

        assertThrows(UnsupportedOperationException.class, () -> patterns.put(0, new String[]{}));
    }

    @Test
    public void readsSimpleGolangExample() throws URISyntaxException, IOException {
        URL simpleGoResource = getClass().getClassLoader().getResource("simple.go");

        assertNotNull(simpleGoResource);

        Path resourcePath = Path.of(simpleGoResource.toURI());

        Set<Node.Op> resultSet = new GoReader(new MemEntityFactory()).read(Path.of("."), new Path[]{resourcePath});

        assertNotNull(resultSet);

        Node.Op[] resultArray = resultSet.toArray(new Node.Op[]{});

        assertTrue(resultArray.length > 0);
        assertNodeIsPluginArtifact(resourcePath, resultArray[0]);
        assertArtifactTokensEqualCodeTokens(simpleGoResource, resultArray[0]);
    }

    private static void assertArtifactTokensEqualCodeTokens(URL golangCodeResource, Node.Op pluginNode) throws IOException, URISyntaxException {
        FlattenableGoLexer lexer = new FlattenableGoLexer(CharStreams.fromPath(Path.of(golangCodeResource.toURI()), StandardCharsets.UTF_8));
        List<Token> tokenList = lexer.flat();

        pluginNode.traverse((Node.NodeVisitor) node -> {
            Artifact<?> artifact = node.getArtifact();

            if (artifact.getData() instanceof TokenArtifactData) {
                TokenArtifactData tokenData = (TokenArtifactData) artifact.getData();
                tokenList.removeIf(token -> token.getLine() == tokenData.getRow() && token.getCharPositionInLine() == tokenData.getColumn());
            }
        });

        // pluginNode should contain all tokens of the input including whitespace but excluding EOF token
        assertEquals(1, tokenList.size());
        assertEquals(Token.EOF, tokenList.get(0).getType());
    }

    private static void assertNodeIsPluginArtifact(Path resourcePath, Node.Op node) {
        Artifact.Op<?> artifact = node.getArtifact();

        assertInstanceOf(PluginArtifactData.class, artifact.getData());

        PluginArtifactData data = (PluginArtifactData) artifact.getData();

        assertEquals(new GoPlugin().getPluginId(), data.getPluginId());
        assertEquals(resourcePath, data.getPath());
    }
}
