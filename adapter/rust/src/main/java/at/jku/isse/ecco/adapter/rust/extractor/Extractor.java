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
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

public class Extractor {
    private final Set<String> features;
    private final Path basePath;

    public Extractor(Set<String> features, Path basePath) {
        this.features = features;
        this.basePath = basePath;
    }

    public void extract(Path[] input) {
        for (Path path : input) {
            Path absolutePath = this.basePath.resolve(path);
            this.parseFile(absolutePath, path);
        }
    }

//    public void createConfigFile(Set<String> features, Path path) {
//        try (BufferedWriter writer = new BufferedWriter(
//                new OutputStreamWriter(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
//            for (String feature : features) {
//                String config = feature.split("=")[1];
//                writer.write(config + ".1,");
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private void parseFile(Path absolutePath, Path relPath) {
        try {
            List<String> lineList = Files.readAllLines(absolutePath);
            String[] lines = lineList.toArray(new String[0]);
            //Path configPath = Paths.get("adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor/.config");
            //this.createConfigFile(this.features, configPath);
            Visitor visitor = new Visitor(lines, features);
            RustParser parser = this.createParser(absolutePath);
            // in order to suppress log output
            parser.removeErrorListeners();
            ParseTree tree = parser.crate();
            visitor.visit(tree);
            List<String> nonNullLines = visitor.getNonNullCodeLines();
            Path outputPath = Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor/output").resolve(relPath);
            this.writeToFile(outputPath, nonNullLines);
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

    public void writeToFile(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path.toAbsolutePath(), lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Path> CollectFiles(Path sourceDir) throws IOException {
        List<Path> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile).forEach(fileList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileList;
    }

    public void extractFromDirectory(Path sourceDir) {
        try {
            List<Path> files = CollectFiles(sourceDir);
            for (Path file : files) {
                Path relativePath = sourceDir.relativize(file);
                Path absolutePath = this.basePath.resolve(file);
                this.parseFile(absolutePath, relativePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Set<String> features = Set.of("std", "feature=std");
        Extractor extractor = new Extractor(features, Paths.get("."));
        Path input = Paths.get("/home/zaber/Documents/serde/serde_core");
        extractor.extractFromDirectory(input);
//        Path input = Paths.get("adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor/main.rs");
//        extractor.extract(new Path[]{input});
    }

}
