package sample;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.sample.VevosFeatureSampler;
import at.jku.isse.ecco.experiment.utils.vevos.VevosUtils;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.variantsync.vevos.simulation.io.Resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class RecreatingSampleTest {

    String REPO_NAME = "openvpn";
    Path VEVOS_GROUND_TRUTH_BASE = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\Tools\\VEVOS_Extraction\\ground-truth");
    Path VEVOS_GROUND_TRUTH = VEVOS_GROUND_TRUTH_BASE.resolve("openvpn");
    Path VEVOS_REPO = VEVOS_GROUND_TRUTH_BASE.resolve("REPOS\\openvpn");

    @Mock
    ExperimentRunConfiguration runConfig;

    Path resourceBasePath = ResourceUtils.getResourceFolderPath("");
    Path creationSamplePath = resourceBasePath.resolve("sample_creation");
    Path recreationSamplePath = resourceBasePath.resolve("sample_recreation");

    public RecreatingSampleTest() throws ResourceException {
    }


    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        Files.createDirectories(this.creationSamplePath);
        Files.createDirectories(this.recreationSamplePath);
        when(runConfig.getMinVariantFeatures()).thenReturn(5);
        when(runConfig.getMaxVariantFeatures()).thenReturn(10);
        when(runConfig.getVariantsDir()).thenReturn(this.creationSamplePath);
        when(runConfig.getVevosGroundTruthDatasetPath()).thenReturn(VEVOS_GROUND_TRUTH);
        when(runConfig.getVevosSplRepositoryBasePath()).thenReturn(VEVOS_REPO);
        when(runConfig.getRepositoryName()).thenReturn(REPO_NAME);
    }

    @AfterEach
    public void teardown() throws DirectoryException {
        DirectoryUtils.deleteFolderIfItExists(this.creationSamplePath);
        DirectoryUtils.deleteFolderIfItExists(this.recreationSamplePath);
    }

    @Test
    public void recreatedSampleIsIdenticalToOriginal() throws Resources.ResourceIOException, IOException {
        VevosFeatureSampler sampler = new VevosFeatureSampler();

        // create sample
        sampler.sample(this.runConfig, 3);

        // recreate sample
        List<Path> variantPaths = VevosUtils.getVariantFolders(creationSamplePath);
        List<String> configList = new LinkedList<>();
        for (Path variantPath : variantPaths){
            configList.add(VevosUtils.variantPathToConfigString(variantPath));
        }
        List<List<String>> featureLists = configList.stream().map(variantConfiguration -> {
            String[] featureArray = variantConfiguration.split(",");
            List<String> featureNames = new java.util.ArrayList<>(Arrays.stream(featureArray).map(String::trim).toList());
            featureNames.remove("BASE");
            return featureNames;
        }).toList();

        sampler.createSampleVariants(VEVOS_GROUND_TRUTH_BASE, REPO_NAME, recreationSamplePath, featureLists);

        File creationFolder = new File(creationSamplePath.toUri());
        File recreationFolder = new File(recreationSamplePath.toUri());

        // compare original with recreation
        assertTrue(DirectoryUtils.foldersAreEqual(creationFolder.toPath(), recreationFolder.toPath()));
    }
}
