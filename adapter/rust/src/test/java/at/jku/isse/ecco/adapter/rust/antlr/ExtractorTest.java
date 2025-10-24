package at.jku.isse.ecco.adapter.rust.antlr;

import at.jku.isse.ecco.adapter.rust.extractor.Extractor;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static at.jku.isse.ecco.adapter.rust.extractor.Extractor.getFeaturesFromFile;

class ExtractorTest {
    @Test
    void testExtractor() {
        Path input = Paths.get("src/test/resources/extractor/serde");
        Path featureDir = Paths.get("src/test/resources/extractor/test");
        try (Stream<Path> files = Files.list(featureDir)) {
            files.filter(Files::isRegularFile).forEach(featureFile -> {
                String featureFileName = featureFile.getFileName().toString();
                String lastFolder = input.getFileName().toString();
                Path output = Paths.get("src/test/resources/extractor/output")
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

    @Test
    void commitVariant() throws IOException {
        Path testDir = Paths.get("src/test/resources/extractor/commit_test").toAbsolutePath();
        Files.createDirectories(testDir);
        // Initialize ECCO service
        EccoService service = null;
        try {
            service = new EccoService(testDir);
            service.init();
        } catch (Exception e) {
            System.out.println("Exception during ECCO setup: " + e.getMessage());
        }
        Path outputBase = Paths.get("src/test/resources/extractor/output");
        List<String> files = Files.list(outputBase).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .toList();
        // files = List.of(files.getFirst()); // Only commit one variant for testing
        for (String folder : files) {
            Assertions.assertNotNull(service);
            service.setBaseDir(outputBase.resolve(folder));
            service.commit();
        }
    }

}
