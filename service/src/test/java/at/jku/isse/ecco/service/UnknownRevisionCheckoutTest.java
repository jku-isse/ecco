package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A configuration's unknown revision id ("A.1" - ids are hashes) or feature name is read as a new
 * one: right for a commit, which creates it (FR-F2b), but a checkout of it wrote nothing of that
 * feature, without an error (gap #81). A checkout names committed revisions only and is refused
 * otherwise, before anything is written.
 */
public class UnknownRevisionCheckoutTest {

    @Test
    @Timeout(60)
    public void aCheckoutOfARevisionOrFeatureNeverCommittedIsRefused(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);
            Files.writeString(work.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            String revision = service.getRepository().getFeatures().iterator().next().getLatestRevision().toString();

            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            EccoException unknownRevision = assertThrows(EccoException.class, () -> service.checkout("A.1"));
            assertTrue(unknownRevision.getMessage().contains("A has no revision 1"), unknownRevision.getMessage());
            EccoException unknownFeature = assertThrows(EccoException.class, () -> service.checkout("A, Z"));
            assertTrue(unknownFeature.getMessage().contains("feature Z was never committed"), unknownFeature.getMessage());
            assertEquals(Map.of(), DirectoryFingerprint.of(out).entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty() && !e.getKey().equals("/")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                    "nothing written");

            // a name alone is its latest revision, a displayed id its revision
            service.checkout("A");
            assertEquals("a\n", Files.readString(out.resolve("a.txt")));
            Path byId = Files.createDirectories(tmp.resolve("by-id"));
            service.setBaseDir(byId);
            service.checkout(revision);
            assertEquals("a\n", Files.readString(byId.resolve("a.txt")));

            // a commit still creates the revision it names
            service.setBaseDir(work);
            Files.writeString(work.resolve("a.txt"), "a2\n");
            service.commit("a2", "A.1");
            assertEquals(2, service.getRepository().getFeatures().iterator().next().getRevisions().size());
        }
    }

    @Test
    @Timeout(60)
    public void anAmbiguousRevisionPrefixIsReportedAsAmbiguous(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);
            Files.writeString(work.resolve("a.txt"), "a\n");
            service.commit("a1", "A.abcdef01");
            Files.writeString(work.resolve("a.txt"), "a2\n");
            service.commit("a2", "A.abcdef02");

            service.setBaseDir(Files.createDirectories(tmp.resolve("out")));
            // two revisions start with the 7 displayed characters: not "A has no revision abcdef0"
            EccoException ambiguous = assertThrows(EccoException.class, () -> service.checkout("A.abcdef0"));
            assertTrue(ambiguous.getMessage().contains("A.abcdef0 is ambiguous: 2 revisions start with it"), ambiguous.getMessage());
            assertFalse(ambiguous.getMessage().contains("has no revision"), ambiguous.getMessage());
        }
    }
}
