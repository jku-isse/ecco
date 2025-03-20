import at.jku.isse.ecco.experiment.Experiment;
import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.result.Result;
import at.jku.isse.ecco.experiment.result.persister.ResultDatabasePersister;
import at.jku.isse.ecco.experiment.result.persister.ResultInMemoryPersister;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.DatabaseResultUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ExperimentTest {

    @BeforeEach
    public void cleanUpDatabase() throws IOException, ResourceException {
        if (DatabaseResultUtils.databaseExists()) {
            Path databasePath = ResourceUtils.getResourceFolderPath("database/results.db");
            Files.delete(databasePath);
        }
    }

    @Test
    public void experimentRunsWithoutException() throws ResourceException {
        String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
    }

    @Test
    public void configurationCreatesCorrectNumberOfResults() throws ResourceException {
        ResultInMemoryPersister persister = new ResultInMemoryPersister();
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);

        assertEquals(10, persister.getResults().size());
    }

    @Test
    public void openVpnHasPerfectScoreFor100PercentFeatureTracesTest() throws ResourceException {
        String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/openvpn_experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("test_variant_openvpn");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
        assertTrue(DatabaseResultUtils.checkF1OfSingleResult(1.0));
    }

    @Test
    public void erroneousConjunctionCreatesFixedResults() throws ResourceException {
        ResultInMemoryPersister persister = new ResultInMemoryPersister();
        Experiment experiment = new Experiment(true, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/erroneous_conjunction_experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);

        Collection<Result> results = persister.getResults();

        results.forEach(result -> {
            assertEquals(1.0, result.getPrecision());
            assertEquals(0.5, result.getRecall());
            assertEquals((2.0/3.0), result.getF1());
        });
    }
}
