package at.jku.isse.ecco.service;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerRetroactiveBasedEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fork has its origin's maximum order and checks out as it does - the same evaluation and main tree
 * building strategies (FR-K1b) - whether it copies everything or leaves revisions out, locally or
 * from a sync server (gaps #98, #99): a fork leaving revisions out took the new repository's
 * defaults, and no fork took the strategies.
 */
public class ForkKeepsRepositorySettingsTest {

    /** an origin with maximum order 3, retroactive evaluation and plain (unboosted) main trees, and A and B committed */
    private static Path origin(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
        }
        try (EccoService service = open(dir)) {
            Repository.Op repository = (Repository.Op) service.getRepository();
            repository.setMaxOrder(3);
            repository.setEvaluationStrategy(new SerRetroactiveBasedEvaluation());
            service.store();
            service.disableFeatureTraceBoosting();
        }
        commitInto(dir, steps("A=a0", "A, B=a0|b0"));
        return dir;
    }

    private static String settings(Path repository) {
        try (EccoService service = open(repository)) {
            Repository.Op repo = (Repository.Op) service.getRepository();
            return repo.getMaxOrder() + " " + repo.getEvaluationStrategy().getClass().getSimpleName() + " " + repo.getMainTreeBuildingStrategy().getClass().getSimpleName();
        }
    }

    private static String revisionsOfB(Path repository) {
        try (EccoService service = open(repository)) {
            return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("B"))
                    .flatMap(f -> f.getRevisions().stream()).map(r -> "B." + r.getId()).collect(Collectors.joining(", "));
        }
    }

    @Test
    @Timeout(120)
    public void everyForkHasItsOriginsSettings(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp.resolve("origin"));
        String expected = settings(origin);
        assertEquals("3 SerRetroactiveBasedEvaluation SerAssociationMerger", expected, "the origin as set up");
        String withoutB = revisionsOfB(origin);

        Path all = fork(origin, tmp.resolve("all"));
        Path excluding = Files.createDirectories(tmp.resolve("excluding"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(excluding.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"), withoutB);
        }
        Path remoteAll = forkOverTheNetwork(origin, tmp.resolve("remote-all"));
        Path remoteExcluding = Files.createDirectories(tmp.resolve("remote-excluding"));
        withServer(origin, port -> {
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(remoteExcluding.resolve(".ecco"));
                service.fork("localhost", port, withoutB);
            }
        });

        for (Path fork : List.of(all, excluding, remoteAll, remoteExcluding))
            assertEquals(expected, settings(fork), fork.getFileName() + "");
    }
}
