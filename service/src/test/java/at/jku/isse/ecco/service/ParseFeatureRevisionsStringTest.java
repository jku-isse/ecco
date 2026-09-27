package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * parseFeatureRevisionsString() (fork/pull/push --exclude) must accept the revision ids as they are
 * displayed - FeatureRevision#getFeatureRevisionString() truncates them to 7 characters, which
 * parseConfigurationString() already resolved as a unique prefix but this parser rejected - and the
 * [feature id] form, which it looked up with the brackets still attached and so never found.
 */
public class ParseFeatureRevisionsStringTest {

    @Test
    @Timeout(60)
    public void acceptsDisplayedShortRevisionIdsAndFeatureIds() throws Exception {
        Path workDir = Files.createTempDirectory("parse-feature-revisions");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A");

            Feature a = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow();
            FeatureRevision revision = a.getLatestRevision();
            String shortId = revision.getId().substring(0, 7);

            assertEquals(List.of(revision), List.copyOf(service.parseFeatureRevisionsString("A." + revision.getId())));
            assertEquals(List.of(revision), List.copyOf(service.parseFeatureRevisionsString("A." + shortId)));
            assertEquals(List.of(revision), List.copyOf(service.parseFeatureRevisionsString("[" + a.getId() + "]." + shortId)));

            assertThrows(EccoException.class, () -> service.parseFeatureRevisionsString("A.zzzzzzz"));
            assertThrows(EccoException.class, () -> service.parseFeatureRevisionsString("[nosuchfeature]." + shortId));
        }
    }
}
