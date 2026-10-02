package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persisted minimized conditions were never invalidated: after a later commit changed an
 * association's condition, or added a configuration that removed a constraint the minimization
 * relied on, the GUI kept showing the old minimized condition. Each is now stored with a fingerprint
 * of what it was computed from and only trusted while that still matches.
 */
public class MinimizedConditionValidityTest {

    @Test
    @Timeout(120)
    public void minimizedConditionsStayValidUntilWhatTheyWereComputedFromChanges(@TempDir Path tmp) throws IOException {
        Path wd = Files.createDirectories(tmp.resolve("wd"));
        Path repo = tmp.resolve(".ecco");
        try (EccoService service = open(repo, wd, true)) {
            commit(service, wd, "A, B", "a", "b");
            commit(service, wd, "A", "a");

            // a run: fingerprints when it starts, results persisted afterwards
            Map<String, String> bases = service.minimizationBases();
            service.persistMinimizedConditions(minimizedEverything(service), bases);
            assertEquals(ids(service), service.validMinimizedConditions().keySet());
        }

        try (EccoService service = open(repo, wd, false)) {
            assertEquals(ids(service), service.validMinimizedConditions().keySet(), "still valid after reopening");

            // the same configuration again with the same content: neither the configurations nor
            // (here) the conditions change
            Map<String, String> conditionsBefore = conditions(service);
            commit(service, wd, "A", "a");
            Map<String, String> unchanged = new HashMap<>(conditions(service));
            unchanged.entrySet().removeIf(e -> !e.getValue().equals(conditionsBefore.get(e.getKey())));
            assertFalse(unchanged.isEmpty());
            assertEquals(unchanged.keySet(), service.validMinimizedConditions().keySet());

            // a new, distinct configuration can remove a mined constraint the minimization relied on.
            // The associations of a and b are not touched by it and keep their stored minimized
            // conditions - which the GUI used to show regardless - but they are no longer trusted.
            commit(service, wd, "C", "c");
            assertTrue(service.getRepository().getAssociations().stream().anyMatch(a -> ((Association.Op) a).getMinimizedCondition() != null));
            assertTrue(service.validMinimizedConditions().isEmpty());
        }
    }

    @Test
    @Timeout(120)
    public void resultsOfARunThatACommitOvertookAreNotTrusted(@TempDir Path tmp) throws IOException {
        Path wd = Files.createDirectories(tmp.resolve("wd"));
        try (EccoService service = open(tmp.resolve(".ecco"), wd, true)) {
            commit(service, wd, "A, B", "a", "b");
            Map<String, String> bases = service.minimizationBases();
            Map<String, String> minimized = minimizedEverything(service);
            commit(service, wd, "A", "a"); // lands while the run is still going
            service.persistMinimizedConditions(minimized, bases);
            assertTrue(service.validMinimizedConditions().isEmpty());
        }
    }

    @Test
    @Timeout(120)
    public void acceptingAConstraintOrPersistingWithoutAFingerprintInvalidates(@TempDir Path tmp) throws IOException {
        Path wd = Files.createDirectories(tmp.resolve("wd"));
        try (EccoService service = open(tmp.resolve(".ecco"), wd, true)) {
            commit(service, wd, "A, B", "a", "b");
            commit(service, wd, "A", "a");
            service.persistMinimizedConditions(minimizedEverything(service), service.minimizationBases());
            assertEquals(ids(service), service.validMinimizedConditions().keySet());

            // the shape persisted before fingerprints existed
            Association.Op any = (Association.Op) service.getRepository().getAssociations().iterator().next();
            any.setMinimizedCondition("A");
            assertFalse(service.validMinimizedConditions().containsKey(any.getId()));

            service.acceptConstraint(ConstraintMiner.Kind.REQUIRES, "B", "A");
            assertTrue(service.validMinimizedConditions().isEmpty(), "the feature model changed");
        }
    }

    private static EccoService open(Path repo, Path wd, boolean init) {
        EccoService service = new EccoService();
        service.setRepositoryDir(repo);
        if (init) service.init();
        else service.open();
        service.setBaseDir(wd);
        return service;
    }

    private static void commit(EccoService service, Path wd, String configuration, String... files) throws IOException {
        try (var paths = Files.list(wd)) {
            for (Path p : paths.toList()) Files.delete(p);
        }
        for (String f : files) Files.writeString(wd.resolve(f + ".txt"), f + "\n");
        service.commit(configuration, configuration);
    }

    private static Map<String, String> minimizedEverything(EccoService service) {
        return service.getRepository().getAssociations().stream().collect(Collectors.toMap(Association::getId, a -> "MINIMIZED"));
    }

    private static java.util.Set<String> ids(EccoService service) {
        return service.getRepository().getAssociations().stream().map(Association::getId).collect(Collectors.toSet());
    }

    private static Map<String, String> conditions(EccoService service) {
        return service.getRepository().getAssociations().stream().collect(Collectors.toMap(Association::getId, a -> a.computeCondition().toLogicString()));
    }
}
