package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.rust.antlr.RustLexer;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Extractor {
    private final Set<String> features;
    private final Path basePath;

    public Extractor(Set<String> features, Path basePath) {
        this.features = features;
        this.basePath = basePath;
    }

//    public void extract(Path[] input) {
//        for (Path path : input) {
//            Path absolutePath = this.basePath.resolve(path);
//            this.parseFile(absolutePath, path);
//        }
//    }

    public void createConfigFile(Set<String> features, Path path) {
        Set<String> uniqueFeatures = features.stream()
                .filter(f -> !f.isBlank())
                .map(f -> f.contains("=") ? f.split("=")[1] : f)
                .map(s -> s = s + ".1, ")
                .collect(Collectors.toSet());
        try {
            Files.write(path, uniqueFeatures, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseFile(Path absolutePath, Path relPath, Path outputDir) {
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);

            Visitor visitor = new Visitor(lines, features);
            RustParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.crate();
            visitor.visit(tree);
            List<String> nonNullLines = visitor.getNonNullCodeLines();

            // put output in output directory
            outputDir = outputDir.resolve(relPath);
            this.writeToFile(outputDir, nonNullLines);
        } catch (IOException e) {
            throw new EccoException(e);
        }
    }

    private RustParser createParser(Path absolutePath) {
        try {
            CharStream charstream = CharStreams.fromFileName(String.valueOf(absolutePath));
            RustLexer lexer = new RustLexer(charstream);
            // in order to suppress log output
            lexer.removeErrorListeners();
            TokenStream tokenStream = new CommonTokenStream(lexer);
            return new RustParser(tokenStream);
        } catch (IOException e) {
            throw new EccoException("Failed to read file: " + absolutePath, e);
        }
    }

    private void writeToFile(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path.toAbsolutePath(), lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> collectFiles(Path sourceDir) throws IOException {
        List<Path> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile).forEach(fileList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileList;
    }

    public void extractFromDirectory(Path sourceDir, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            List<Path> files = collectFiles(sourceDir);
            for (Path file : files) {
                Path relativePath = sourceDir.relativize(file);
                Path absolutePath = this.basePath.resolve(file);
                this.parseFile(absolutePath, relativePath, outputDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
