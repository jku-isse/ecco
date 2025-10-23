package at.jku.isse.ecco.adapter.rust.antlr;

import at.jku.isse.ecco.adapter.rust.extractor.Extractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static at.jku.isse.ecco.adapter.rust.extractor.Extractor.getFeaturesFromFile;

class ExtractorTest {
    @Test
    void testExtractor() {
        Path input = Paths.get("src/test/resources/serde");
        Path featureDir = Paths.get("src/test/resources/extractor/featureLists");
        try (Stream<Path> files = Files.list(featureDir)) {
            files.filter(Files::isRegularFile).forEach(featureFile -> {
                String featureFileName = featureFile.getFileName().toString();
                String lastFolder = input.getFileName().toString();
                Path output = Paths.get("src/test/resources/output")
                        .resolve(lastFolder + "_output_" + featureFileName.replace(".csv", ""));
                Set<String> features = getFeaturesFromFile(input, featureFile);
                Extractor extractor = new Extractor(features, Paths.get("."));
                extractor.extractFromDirectory(input, output);
                extractor.createConfigFile(features, output.resolve(".config"));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
