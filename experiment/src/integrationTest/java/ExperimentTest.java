import at.jku.isse.ecco.experiment.Experiment;
import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.result.persister.ResultDatabasePersister;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.AnalyzeVariantUtils;
import utils.DatabaseResultUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class ExperimentTest {

    @BeforeEach
    public void cleanUpDatabase() throws IOException {
        if (DatabaseResultUtils.databaseExists()) {
            Path databasePath = ResourceUtils.getResourceFolderPath("database/results.db");
            Files.delete(databasePath);
        }
    }

    @Test
    public void experimentRunsWithoutException() {
        String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
    }

    @Test
    public void testSpl2Test(){
        String databasePath = ResourceUtils.getResourceFolderPathAsString("database");
        ResultPersister persister = new ResultDatabasePersister(databasePath);
        Experiment experiment = new Experiment(false, persister);

        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(configPath, variantBasePath);
        experiment.runExperiment(experimentConfig);
    }

    @Test
    public void openVpnHasPerfectScoreFor100PercentFeatureTracesTest(){
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
    public void compareArtefactNumbersOfVevosAndRepository(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant_openvpn/Variant9");
        int conditionedCodeLines = AnalyzeVariantUtils.getNumberOfConditionedCodeLines(variantFolderPath);
        System.out.println(conditionedCodeLines);
    }
}
