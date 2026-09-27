package at.jku.isse.ecco.service;

import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * enable/disableFeatureTraceBoosting() set the main tree building strategy on the repository
 * without a write transaction, so the setting only survived if some later, unrelated write happened
 * to persist it. They now write it like every other repository change.
 */
public class FeatureTraceBoostingPersistenceTest {

    @Test
    @Timeout(30)
    public void theBoostingSettingIsPersisted() throws Exception {
        Path repoDir = Files.createTempDirectory("feature-trace-boosting").resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.enableFeatureTraceBoosting();
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertTrue(strategyOf(service) instanceof BoostedAssociationMerger, "enabled boosting must survive a reopen");
            service.disableFeatureTraceBoosting();
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertFalse(strategyOf(service) instanceof BoostedAssociationMerger, "disabled boosting must survive a reopen");
        }
    }

    private static Object strategyOf(EccoService service) {
        return ((Repository.Op) service.getRepository()).getMainTreeBuildingStrategy();
    }
}
