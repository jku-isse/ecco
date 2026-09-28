package at.jku.isse.ecco.service;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A short revision id like the "2" of "A.2" names a new revision. It used to be resolved as a
 * prefix of the existing revisions' ids (meant for ids displayed truncated to 7 characters), so
 * whenever the random id of A's first revision started with "2" (one time in 16), committing "A.2"
 * went to that revision instead and the new content could not be checked out.
 */
public class ShortRevisionIdTest {

    @Test
    @Timeout(60)
    public void aShortIdThatPrefixesAnExistingRevisionIdIsANewRevision() throws Exception {
        Path workDir = Files.createTempDirectory("short-revision-id");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Path out = Files.createDirectories(workDir.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.commit("one", "A");
            FeatureRevision first = featureA(service).getLatestRevision();
            // the worst case, which used to happen by chance: the new id is a prefix of the old one
            String shortId = first.getId().substring(0, 1);

            Files.writeString(content.resolve("f.txt"), "a\nb\n");
            service.commit("two", "A." + shortId);

            assertEquals(2, featureA(service).getRevisions().size());
            service.setBaseDir(out);
            service.checkout("A." + shortId);
            assertEquals("a\nb\n", Files.readString(out.resolve("f.txt")));
        }
    }

    @Test
    @Timeout(60)
    public void aDisplayedIdStillFindsItsRevision() throws Exception {
        Path workDir = Files.createTempDirectory("displayed-revision-id");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Path out = Files.createDirectories(workDir.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.commit("one", "A");
            FeatureRevision first = featureA(service).getLatestRevision();

            service.setBaseDir(out);
            service.checkout(first.getFeatureRevisionString());
            assertEquals("a\n", Files.readString(out.resolve("f.txt")));
            assertEquals(1, featureA(service).getRevisions().size());
        }
    }

    private static Feature featureA(EccoService service) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow();
    }
}
