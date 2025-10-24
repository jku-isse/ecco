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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Extractor {
    private final Set<String> features;
    private final Path basePath;

    public Extractor(Set<String> features, Path basePath) {
        this.features = features;
        this.basePath = basePath;
    }

    /** Create a config file listing the features with format:
     * serde.1
     * tokio.1
     * @param features
     * @param path
     */
    public void createConfigFile(Set<String> features, Path path) {
        String uniqueFeatures = features.stream()
                .filter(f -> !f.isBlank())
                .map(f -> f.contains("=") ? f.split("=")[1] : f)
                .map(s -> s = s + ".1")
                .distinct()
                .collect(Collectors.joining(", "));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, uniqueFeatures, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

            // Visitor.visit sets all unused lines to null
            List<String> nonNullLines = visitor.getNonNullCodeLines();

            // put output in output directory
            outputDir = outputDir.resolve(relPath);
            this.writeToFile(outputDir, nonNullLines);
        } catch (IOException e) {
            throw new EccoException("Failed to read file: " + absolutePath, e);
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
        List<Path> fileList;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            fileList = walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".rs")).toList();
        } catch (IOException e) {
            throw new RuntimeException("could not collect files" + e);
        }
        return fileList;
    }

    /** Extract source code from sourceDir to outputDir based on the features
     * @param sourceDir
     * @param outputDir
     */
    public void extractFromDirectory(Path sourceDir, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            List<Path> files = collectFiles(sourceDir);
            files.parallelStream().forEach(file -> {
                Path relativePath = sourceDir.relativize(file);
                Path absolutePath = this.basePath.resolve(file);
                this.parseFile(absolutePath, relativePath, outputDir);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Extract using features listed in a feature file
    // Feature files has format csvconf like "serde",True \n
    public static Set<String> getFeaturesFromFile(Path sourceDir, Path featureFile) {
        try {
            List<String> featureLines = Files.readAllLines(featureFile);
            Set<String> featuresFromFile = new HashSet<>();
            for (String line : featureLines) {
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid feature line: " + line);
                }
                if (parts[1].trim().equalsIgnoreCase("True")) {
                    featuresFromFile.add(parts[0].trim().replaceAll("\"" , ""));
                }
            }
            return featuresFromFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
//        Set<String> features = Set.of("std", "feature=std");
//        Extractor extractor = new Extractor(features, Paths.get("."));
//        Path input = Paths.get("/home/zaber/Documents/bachelor/serde/serde_core");
//        String lastFolder = input.getFileName().toString();
//        Path output = Paths.get("adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor").resolve(lastFolder + "_output");
//        extractor.extractFromDirectory(input, output);
//        extractor.createConfigFile(features, output.resolve(".config"));
    }
}
