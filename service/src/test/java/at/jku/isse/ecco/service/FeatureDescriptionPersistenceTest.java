package at.jku.isse.ecco.service;

import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The GUI's feature "Save" button only set the description on the in-memory Feature ("TODO:
 * implement saving/updating features"), so it was lost on reopen unless some unrelated write later
 * happened to persist it. setFeatureDescription() writes it.
 */
public class FeatureDescriptionPersistenceTest {

    @Test
    @Timeout(60)
    public void aFeatureDescriptionIsPersisted() throws Exception {
        Path workDir = Files.createTempDirectory("feature-description");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("a.txt"), "a\n");
        String featureId;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            service.commit("a", "A");
            Feature a = feature(service);
            featureId = a.getId();

            a.setDescription("only in memory"); // what the Save button used to do
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertNotEquals("only in memory", feature(service).getDescription(), "precondition: in-memory changes are not persisted");

            service.setFeatureDescription(featureId, "persisted description");
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertEquals("persisted description", feature(service).getDescription());
        }
    }

    private static Feature feature(EccoService service) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow();
    }
}
