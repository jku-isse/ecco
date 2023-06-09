package at.jku.isse.ecco.adapter.golang.antlr;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class NodeWalker {
    public void walk(String path, ParseTreeListener parseTreeListener) throws IOException {
        try (InputStream goInputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (goInputStream == null) {
                throw new IOException(String.format("could not read input file: '%s'", path));
            }

            GoLexer lexer = new GoLexer(CharStreams.fromStream(goInputStream, StandardCharsets.UTF_8));

            TokenStream tokenStream = new CommonTokenStream(lexer);
            GoParser goParser = new GoParser(tokenStream);
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(parseTreeListener, goParser.sourceFile());
        }
    }
}
