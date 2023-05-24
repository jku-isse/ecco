package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.golang.antlr.GoLexer;
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
        assertArtifactTokensEqualCodeTokens(simpleGoResource, resultArray);
    }

    private static void assertArtifactTokensEqualCodeTokens(URL golangCodeResource, Node.Op[] resultArray) throws IOException, URISyntaxException {
        GoLexer lexer = new GoLexer(CharStreams.fromPath(Path.of(golangCodeResource.toURI()), StandardCharsets.UTF_8));
        Token lexerToken = lexer.nextToken();

        // First (i=0) result node is plugin data and must be skipped
        for(int i = 1; lexerToken.getType() != Token.EOF; i++) {
            // If resultArray[i] causes an IndexOutOfBounds exception, not all tokens made it into the resultArray
            assertTrue(resultArray.length >= i+1);
            assertInstanceOf(TokenArtifactData.class, resultArray[i].getArtifact().getData());

            TokenArtifactData tokenArtifactData = (TokenArtifactData) resultArray[i].getArtifact().getData();

            assertEquals(lexerToken.getText(), tokenArtifactData.getToken());
            assertEquals(lexerToken.getLine(), tokenArtifactData.getRow());
            assertEquals(lexerToken.getCharPositionInLine(), tokenArtifactData.getColumn());

            lexerToken = lexer.nextToken();
        }
    }

    private static void assertNodeIsPluginArtifact(Path resourcePath, Node.Op node) {
        Artifact.Op<PluginArtifactData> pluginArtifact;

        try {
            pluginArtifact = (Artifact.Op<PluginArtifactData>) node.getArtifact();
            PluginArtifactData data = pluginArtifact.getData();

            assertEquals(new GoPlugin().getPluginId(), data.getPluginId());
            assertEquals(resourcePath, data.getPath());
        }catch (ClassCastException e) {
            fail(e);
        }
    }
}
