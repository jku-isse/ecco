package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Accepted and rejected parents in the feature model ("A is a subfeature of B"), stored in the
 * repository next to the constraints - not as a constraint kind of their own: a parent's only
 * logical content is "A requires B", which is accepted with it as a REQUIRES; the rest - which of the
 * features A requires is its parent, one parent each, no cycles - is the model's structure. The
 * suggested subfeatures could not be accepted or rejected, and the tree followed whatever was suggested.
 */
public class FeatureParentsTest {

    @Test
    @Timeout(30)
    public void anAcceptedParentIsStoredWithItsRequiresAndSurvivesReopening(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        try (EccoService service = repository(repo)) {
            service.acceptFeatureParents(Map.of("aes", "encryption", "zip64", "BASE"));
            assertEquals(Set.of("REQUIRES|aes|encryption", "REQUIRES|zip64|BASE"), ids(service.getRepository().getConstraints()));
        }
        try (EccoService service = reopen(repo)) {
            assertEquals(Map.of("aes", "encryption", "zip64", "BASE"), service.getRepository().getFeatureParents());
        }
    }

    /** One parent each: another replaces it, with its REQUIRES; moving it back to pending takes both away. */
    @Test
    @Timeout(30)
    public void oneParentEachAndItsRequiresGoesWithIt(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.acceptFeatureParents(Map.of("aes", "encryption"));
            service.acceptFeatureParents(Map.of("aes", "BASE"));
            assertEquals(Map.of("aes", "BASE"), service.getRepository().getFeatureParents());
            assertEquals(Set.of("REQUIRES|aes|BASE"), ids(service.getRepository().getConstraints()));

            service.unacceptFeatureParents(List.of("aes"));
            assertEquals(Map.of(), service.getRepository().getFeatureParents());
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()));
        }
    }

    @Test
    @Timeout(30)
    public void aCycleIsRefusedAndChangesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.acceptFeatureParents(Map.of("gcm", "aes", "aes", "encryption"));
            EccoException refused = assertThrows(EccoException.class, () -> service.acceptFeatureParents(Map.of("zip64", "BASE", "encryption", "gcm")));
            assertTrue(refused.getMessage().contains("encryption"), refused.getMessage());
            assertEquals(Map.of("gcm", "aes", "aes", "encryption"), service.getRepository().getFeatureParents(), "not even zip64");
            assertThrows(EccoException.class, () -> service.acceptFeatureParents(Map.of("aes", "aes")));
        }
    }

    /** Rejecting withdraws an acceptance (with its REQUIRES), and the other way round; both can be moved back to pending. */
    @Test
    @Timeout(30)
    public void acceptingAndRejectingWithdrawEachOther(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.acceptFeatureParents(Map.of("aes", "encryption"));
            service.rejectFeatureParents(List.of(Map.entry("aes", "encryption"), Map.entry("aes", "zip64")));
            assertEquals(Map.of(), service.getRepository().getFeatureParents());
            assertEquals(Map.of("aes", Set.of("encryption", "zip64")), service.getRepository().getRejectedFeatureParents());
            assertEquals(Set.of(), ids(service.getRepository().getConstraints()));

            service.unrejectFeatureParents(List.of(Map.entry("aes", "zip64")));
            assertEquals(Map.of("aes", Set.of("encryption")), service.getRepository().getRejectedFeatureParents());
            service.acceptFeatureParents(Map.of("aes", "encryption"));
            assertEquals(Map.of(), service.getRepository().getRejectedFeatureParents());
            assertEquals(Map.of("aes", "encryption"), service.getRepository().getFeatureParents());
        }
    }

    /** Like the constraints: copied by a fork; a pull fills only what the receiver has not decided, and makes no cycle. */
    @Test
    @Timeout(60)
    public void decisionsTravelWithForkAndPull(@TempDir Path tmp) throws Exception {
        Path origin = tmp.resolve("origin");
        try (EccoService service = repository(origin)) {
            service.acceptFeatureParents(Map.of("aes", "encryption", "gcm", "aes"));
            service.rejectFeatureParents(List.of(Map.entry("zip64", "aes")));
        }

        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"));
            assertEquals(Map.of("aes", "encryption", "gcm", "aes"), service.getRepository().getFeatureParents());
            assertEquals(Map.of("zip64", Set.of("aes")), service.getRepository().getRejectedFeatureParents());
            // the fork decides differently, and more
            service.acceptFeatureParents(Map.of("aes", "BASE", "split", "BASE"));
        }

        try (EccoService service = reopen(origin)) {
            service.addRemote("fork", fork.toString(), at.jku.isse.ecco.core.Remote.Type.LOCAL);
            service.pull("fork");
            assertEquals(Map.of("aes", "encryption", "gcm", "aes", "split", "BASE"), service.getRepository().getFeatureParents(),
                    "the origin keeps its own parent of aes, and takes split's");
        }
    }

    @Test
    @Timeout(30)
    public void mergeMakesNoCycle() {
        Repository.Op source = new at.jku.isse.ecco.storage.ser.repository.SerRepository();
        Repository.Op target = new at.jku.isse.ecco.storage.ser.repository.SerRepository();
        source.setFeatureParent("encryption", "aes");
        source.setFeatureParent("zip64", "BASE");
        source.addRejectedFeatureParent("split", "aes");
        target.setFeatureParent("aes", "encryption");

        target.merge(source);

        assertEquals(Map.of("aes", "encryption", "zip64", "BASE"), target.getFeatureParents(), "encryption under aes would close a cycle");
        assertEquals(Map.of("split", Set.of("aes")), target.getRejectedFeatureParents());
    }

    private static EccoService repository(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        return service;
    }

    private static EccoService reopen(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.setBaseDir(dir);
        service.open();
        return service;
    }

    private static Set<String> ids(Collection<? extends Constraint> constraints) {
        return constraints.stream().map(Constraint::getId).collect(Collectors.toSet());
    }
}
