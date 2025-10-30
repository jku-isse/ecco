package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.rust.antlr.RustLexer;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.io.Reader;
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

    private static class ParserBundle {
        RustLexer lexer;
        RustParser parser;
        CommonTokenStream tokens;
    }

    private static final ThreadLocal<ParserBundle> PARSER_BUNDLE = ThreadLocal.withInitial(ParserBundle::new);


    private void parseFile(Path absolutePath, Path relPath, Path outputDir) {
        try (Reader reader = Files.newBufferedReader(absolutePath)) {
            ParserBundle bundle = PARSER_BUNDLE.get();
            CharStream cs = CharStreams.fromReader(reader);

            if (bundle.lexer == null) {
                bundle.lexer = new RustLexer(cs);
                bundle.lexer.removeErrorListeners();
                bundle.tokens = new CommonTokenStream(bundle.lexer);
                bundle.parser = new RustParser(bundle.tokens);
                bundle.parser.removeErrorListeners();
            } else {
                bundle.lexer.setInputStream(cs);
                bundle.tokens.setTokenSource(bundle.lexer);
                bundle.tokens.seek(0);
                bundle.parser.setTokenStream(bundle.tokens);
                bundle.parser.reset();
            }
            String fileText = cs.toString();
            String[] lines = fileText.split("\n", -1);

            ParseTree tree = bundle.parser.crate();

            Visitor visitor = new Visitor(lines, features);
            visitor.visit(tree);

            // Visitor.visit sets all unused lines to null
            String[] nonNullLines = visitor.getNonNullCodeLines();

            // put output in output directory
            outputDir = outputDir.resolve(relPath);
            this.writeToFile(outputDir, nonNullLines);
        } catch (IOException e) {
            throw new EccoException("Failed to read file: " + absolutePath, e);
        }
    }

    private void writeToFile(Path path, String[] lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path.toAbsolutePath(), Arrays.asList(lines), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> collectFiles(Path sourceDir) throws IOException {
        List<Path> fileList;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            fileList = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".rs") || p.toString().endsWith("Cargo.toml")) // only .rs and Cargo.toml files
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("could not collect files from path " + sourceDir + e);
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
    // Features always start with the library name, e.g., serde
    public static Set<String> getFeaturesFromFile(Path sourceDir, Path featureFile) {
        try {
            List<String> featureLines = Files.readAllLines(featureFile);
            Set<String> featuresFromFile = new HashSet<>();
            featureLines.removeFirst(); // remove library name header
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
        // default.1, std.1, serde_derive.1, serde.1, serde_core.1, derive.1
//        Set<String> features = Set.of("serde", "std", "serde_derive", "serde_core", "derive");
//        Extractor extractor = new Extractor(features, Paths.get("."));
//        Path input = Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/test/resources/extractor/test");
//        String lastFolder = input.getFileName().toString();
//        Path output = Paths.get("adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/extractor").resolve(lastFolder + "_output");
//        extractor.extractFromDirectory(input, output);
//        extractor.createConfigFile(features, output.resolve(".config"));
    }
}
