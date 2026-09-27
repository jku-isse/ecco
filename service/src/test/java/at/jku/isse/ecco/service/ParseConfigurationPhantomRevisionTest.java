package at.jku.isse.ecco.service;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * parseConfigurationString() resolved a feature given by NAME to the repository's live Feature
 * object and then called addRevision() on it for "A'" (new revision) or an unknown revision id -
 * inside a READ_ONLY transaction, but on the database that is reused across transactions. Merely
 * parsing such a string (e.g. for a checkout preview) thus added a never-committed revision that the
 * next write transaction persisted: a phantom feature revision. (Features given by [id] were already
 * resolved to a temporary copy.) Parsing must not change the repository.
 */
public class ParseConfigurationPhantomRevisionTest {

    @Test
    @Timeout(60)
    public void parsingAConfigurationDoesNotAddRevisionsToTheRepository() throws Exception {
        Path workDir = Files.createTempDirectory("parse-configuration-phantom");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A");

            Configuration newRevision = service.parseConfigurationString("A'");
            Configuration unknownRevision = service.parseConfigurationString("A.notarevision");
            assertEquals(1, newRevision.getFeatureRevisions().length);
            assertEquals(1, unknownRevision.getFeatureRevisions().length);

            // any later write persists whatever the parse left on the live objects
            Files.delete(content.resolve("a.txt"));
            Files.writeString(content.resolve("b.txt"), "b\n");
            service.commit("b", "B");
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            Feature a = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow();
            assertEquals(1, a.getRevisions().size(), "only the committed revision of A may exist: " + a.getRevisions());
        }
    }
}
