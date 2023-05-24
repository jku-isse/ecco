package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.golang.antlr.GoLexer;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {
    private static final String SIMPLE_GO_PATH = "simple.go";
    private static final String COMPLEX_GO_PATH = "conways-game-of-life.go";

    @Test
    public void simpleGolangWalker() {
        try {
            new NodeWalker().walk(SIMPLE_GO_PATH, new PrintTreeListener());
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    public void complexGolangWalker() {
        try {
            new NodeWalker().walk(COMPLEX_GO_PATH, new PrintTreeListener());
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    public void reconstructSimpleFromLexer() {
        String originalSource = sourceFromFileReader(SIMPLE_GO_PATH);
        String reconstructedSource = sourceFromTokens(SIMPLE_GO_PATH);

        assertEquals(originalSource, reconstructedSource);
    }

    @Test
    public void reconstructComplexFromLexer() {
        String originalSource = sourceFromFileReader(COMPLEX_GO_PATH);
        String reconstructedSource = sourceFromTokens(COMPLEX_GO_PATH);

        assertEquals(originalSource, reconstructedSource);
    }

    private String sourceFromFileReader(String path) {
        try {
            URL resource = getClass().getClassLoader().getResource(path);

            if (resource == null) {
                throw new IOException(String.format("could not find input file: '%s'", path));
            }

            return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            fail(e);
        }

        fail();
        return "";
    }

    private String sourceFromTokens(String path) {
        StringBuilder reconstructedSource = new StringBuilder();

        try (InputStream goInputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (goInputStream == null) {
                throw new IOException(String.format("could not read input file: '%s'", path));
            }

            GoLexer lexer = new GoLexer(CharStreams.fromStream(goInputStream, StandardCharsets.UTF_8));
            Token token = lexer.nextToken();

            while (token.getType() != Token.EOF) {
                String text = token.getText();

                if (text != null) {
                    reconstructedSource.append(token.getText());
                }

                token = lexer.nextToken();
            }

            return reconstructedSource.toString();
        } catch (IOException e) {
            fail(e);
        }

        fail();
        return "";
    }
}
