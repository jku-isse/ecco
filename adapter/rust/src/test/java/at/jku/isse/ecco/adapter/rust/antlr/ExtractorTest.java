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
        Path input = Paths.get("src/test/resources/extractor/serde-1.0.228");
        Path featureDir = Paths.get("src/test/resources/extractor/featureLists");
        try (Stream<Path> files = Files.list(featureDir)) {
            files.filter(Files::isRegularFile).forEach(featureFile -> {
                String featureFileName = featureFile.getFileName().toString();
                String lastFolder = input.getFileName().toString();
                Path output = Paths.get("src/test/resources/extractor/output")
                        .resolve(lastFolder + featureFileName.replace(".csvconf", ""));
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
        Files.createDirectories(outputBase);

        List<String> files = Files.list(outputBase).filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();
        for (String folder : files) {
            Assertions.assertNotNull(service);
            service.setBaseDir(outputBase.resolve(folder));
            service.commit();
        }
    }

}
