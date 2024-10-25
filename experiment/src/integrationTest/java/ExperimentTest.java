import at.jku.isse.ecco.experiment.Experiment;
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
        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2/C_SPL/Sample1/SingleCommit");
        Experiment experiment = new Experiment(false, variantBasePath);
        experiment.runExperiment(configPath);
    }

    @Test
    public void testSpl2Test(){
        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/test_config2.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2/C_SPL/Sample1/SingleCommit");
        Experiment experiment = new Experiment(false, variantBasePath);
        experiment.runExperiment(configPath);
    }

    @Test
    public void openVpnHasPerfectScoreFor100PercentFeatureTracesTest(){
        String configPath = ResourceUtils.getResourceFolderPathAsString("configs/openvpn_experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("test_variant_openvpn");
        Experiment experiment = new Experiment(false, variantBasePath);
        experiment.runExperiment(configPath);
        assertTrue(DatabaseResultUtils.checkF1OfSingleResult(1.0));
    }

    @Test
    public void compareArtefactNumbersOfVevosAndRepository(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant_openvpn/Variant9");
        int conditionedCodeLines = AnalyzeVariantUtils.getNumberOfConditionedCodeLines(variantFolderPath);
        System.out.println(conditionedCodeLines);
    }
}
