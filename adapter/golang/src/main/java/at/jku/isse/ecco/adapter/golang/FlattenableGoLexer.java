package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.golang.antlr.GoLexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

import java.util.LinkedList;
import java.util.List;

/**
 * Extends the generated GoLexer with a method to create a flat list of all tokens
 */
public class FlattenableGoLexer extends GoLexer {
    public FlattenableGoLexer(CharStream input) {
        super(input);
    }

    /**
     * Runs through all tokens to generate a list for easier processing.
     * Resets the lexer afterwards.
     * @see #reset()
     * @return List of all tokens in the input stream
     */
    public List<Token> flat() {
        List<Token> tokens = new LinkedList<>();
        Token golangToken = nextToken();

        while (golangToken.getType() != Token.EOF) {
            tokens.add(golangToken);
            golangToken = nextToken();
        }

        tokens.add(golangToken);
        // Reset lexer so it can be reused afterwards
        reset();

        return tokens;
    }

}
