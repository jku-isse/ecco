package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "[id]" names an existing feature by id - for features whose names are not unique. An id that
 * does not exist silently created a new feature named after the id (a typo in a 36-character id
 * committed a feature called "3f2a9c1e-..."); it is now rejected, as parseFeatureRevisionsString()
 * already did. New features are created by name.
 */
public class UnknownFeatureIdTest {

    @Test
    @Timeout(60)
    public void anUnknownFeatureIdIsRejectedInEveryForm() throws Exception {
        Path workDir = Files.createTempDirectory("unknown-feature-id");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A");

            for (String configuration : new String[]{"[nosuchfeature]", "[nosuchfeature]'", "[nosuchfeature].1234567", "A, [nosuchfeature]"}) {
                EccoException e = assertThrows(EccoException.class, () -> service.parseConfigurationString(configuration), configuration);
                assertTrue(causeMessages(e).contains("Feature id does not exist"), configuration + ": " + causeMessages(e));
            }

            int commits = service.getCommits().size();
            assertThrows(EccoException.class, () -> service.commit("typo", "[nosuchfeature]"));
            assertEquals(commits, service.getCommits().size());
            assertEquals(1, service.getRepository().getFeatures().size());
        }
    }

    @Test
    @Timeout(60)
    public void anExistingFeatureIdStillResolvesInEveryForm() throws Exception {
        Path workDir = Files.createTempDirectory("existing-feature-id");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A");

            Feature a = service.getRepository().getFeatures().iterator().next();
            String id = "[" + a.getId() + "]";
            String revisionId = a.getLatestRevision().getId();

            assertEquals(revisionId, onlyRevisionId(service.parseConfigurationString(id)));
            assertEquals(revisionId, onlyRevisionId(service.parseConfigurationString(id + "." + revisionId.substring(0, 7))));
            assertNotEquals(revisionId, onlyRevisionId(service.parseConfigurationString(id + "'")));
        }
    }

    private static String onlyRevisionId(Configuration configuration) {
        assertEquals(1, configuration.getFeatureRevisions().length, Arrays.toString(configuration.getFeatureRevisions()));
        return configuration.getFeatureRevisions()[0].getId();
    }

    private static String causeMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause())
            sb.append(t.getMessage()).append(" | ");
        return sb.toString();
    }
}
