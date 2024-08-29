package unit;

import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExperimentConfigurationTest {

    @Test
    public void propertiesFileCanBeRead(){
        String propertiesFilePath = ResourceUtils.getResourceFolderPathAsString("configuration/test_experiment.properties");
        ExperimentConfiguration config = new ExperimentConfiguration(propertiesFilePath);
    }

    @Test
    public void numbersOfVariantsAreIterated(){
        String propertiesFilePath = ResourceUtils.getResourceFolderPathAsString("configuration/test_experiment.properties");
        ExperimentConfiguration config = new ExperimentConfiguration(propertiesFilePath);
        List<ExperimentRunConfiguration> runConfigs = new LinkedList<>();
        for (int i = 1; i <= 48; i++){
            runConfigs.add(config.getNextRunConfiguration());
        }
        List<ExperimentRunConfiguration> nullConfigs = runConfigs.stream().filter(Objects::isNull).toList();
        assertTrue(nullConfigs.isEmpty());
    }

}
