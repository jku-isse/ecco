package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.rust.antlr.RustLexer;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Reader {

    public void read(Path base, Path[] input) {
        for (Path path : input) {
            Path absolutePath = base.resolve(path);
            this.parseFile(absolutePath, path);
        }
    }

    private void parseFile(Path absolutePath, Path relPath) {
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);
            Visitor visitor = new Visitor(lines);
            RustParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.crate();
            visitor.visit(tree);
            visitor.getFeatures().forEach(System.out::println);
        } catch (IOException e) {
            throw new EccoException(e);
        }
    }

    private RustParser createParser(Path absolutePath) {
        try {
            CharStream charstream = CharStreams.fromFileName(String.valueOf(absolutePath));
            RustLexer lexer = new RustLexer(charstream);
            // in order to suppress log output like in Creader
            lexer.removeErrorListeners();
            TokenStream tokenStream = new CommonTokenStream(lexer);
            return new RustParser(tokenStream);
        } catch (IOException e) {
            throw new EccoException("Failed to read file: " + absolutePath, e);
        }
    }

    /**
     * @param input Paths to input files relative to current working directory
     * @return A set of Node operands representing Rust source code
     * @see #read(Path, Path[])
     */
    public void read(Path[] input) {
        this.read(Paths.get("."), input);
    }


    public static void main(String[] args) {
        Reader reader = new Reader();
        Path[] input = {Paths.get("adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor/main.rs")};
        reader.read(input);
    }
}
