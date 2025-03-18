package unit;

import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExperimentConfigurationTest {

    @Test
    public void propertiesFileCanBeRead() throws ResourceException {
        String propertiesFilePath = ResourceUtils.getResourceFolderPathAsString("configuration/test_experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        ExperimentConfiguration config = new ExperimentConfiguration(propertiesFilePath, variantBasePath);
    }

    @Test
    public void numbersOfVariantsAreIterated() throws ResourceException {
        String propertiesFilePath = ResourceUtils.getResourceFolderPathAsString("configuration/test_experiment.properties");
        Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
        ExperimentConfiguration config = new ExperimentConfiguration(propertiesFilePath, variantBasePath);
        List<ExperimentRunConfiguration> runConfigs = new LinkedList<>();
        for (int i = 1; i <= 48; i++){
            runConfigs.add(config.getNextRunConfiguration());
        }
        List<ExperimentRunConfiguration> nullConfigs = runConfigs.stream().filter(Objects::isNull).toList();
        assertTrue(nullConfigs.isEmpty());
    }

}
