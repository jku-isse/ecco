package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.FeatureModel;
import at.jku.isse.ecco.web.domain.model.FeatureVersionModel;
import at.jku.isse.ecco.web.domain.model.NumberRevisionsPerFeature;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeatureRepository is a thin wrapper around EccoService.getRepository().getFeatures() - tested
 * against a real, temp-dir-backed EccoService (via EccoApplication, exactly like the CLI/service
 * tests elsewhere in this suite), not mocks, since the point is verifying the real feature/revision
 * data flows through correctly.
 */
@Timeout(30)
public class FeatureRepositoryTest {

    private Path workDir;
    private EccoApplication application;

    @BeforeEach
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("feature-repository-test");
        application = new EccoApplication();
        application.init(workDir.toString());
    }

    @AfterEach
    public void tearDown() {
        application.close();
    }

    private void commitVariant(String name, String configuration, String... extraFileNames) throws IOException {
        Path variantDir = workDir.resolve(name);
        Files.createDirectories(variantDir);
        Files.writeString(variantDir.resolve("core.txt"), "core\n");
        for (String fileName : extraFileNames) {
            Files.writeString(variantDir.resolve(fileName), fileName + "\n");
        }
        EccoService service = application.getEccoService();
        service.setBaseDir(variantDir);
        service.commit(name, configuration);
    }

    @Test
    public void getFeaturesReturnsEveryFeatureSeenAcrossAllCommits() throws IOException {
        commitVariant("core", "Core");
        commitVariant("branchA", "Core, BranchA", "a.txt");
        commitVariant("branchB", "Core, BranchB", "b.txt");

        FeatureRepository featureRepository = new FeatureRepository(application);
        FeatureModel[] features = featureRepository.getFeatures();

        List<String> names = Arrays.stream(features).map(FeatureModel::getName).collect(Collectors.toList());
        assertEquals(3, names.size());
        assertTrue(names.containsAll(List.of("Core", "BranchA", "BranchB")));
        for (FeatureModel feature : features) {
            assertEquals("", feature.getDescription(), "a freshly committed feature has no description yet");
        }
    }

    @Test
    public void updateFeatureChangesTheDescriptionOfTheMatchingFeatureOnly() throws IOException {
        commitVariant("core", "Core");
        commitVariant("branchA", "Core, BranchA", "a.txt");

        FeatureRepository featureRepository = new FeatureRepository(application);
        FeatureModel update = new FeatureModel("Core", "the core feature");

        FeatureModel[] updated = featureRepository.updateFeature(update);

        FeatureModel coreResult = Arrays.stream(updated).filter(f -> f.getName().equals("Core")).findFirst().orElseThrow();
        FeatureModel branchAResult = Arrays.stream(updated).filter(f -> f.getName().equals("BranchA")).findFirst().orElseThrow();
        assertEquals("the core feature", coreResult.getDescription());
        assertEquals("", branchAResult.getDescription(), "only the targeted feature's description should change");
    }

    /**
     * A bare feature name in a configuration string (e.g. "Core") reuses the feature's most recent
     * revision rather than creating a new one - see EccoService#parseConfigurationString(). A
     * trailing "'" (e.g. "Core'") is what actually forces a brand new revision, so that's what these
     * tests use to get a deterministic revision count instead of assuming one revision per commit.
     */
    @Test
    public void getFeatureVersionsFromFeatureReturnsOneVersionPerNewRevisionCreated() throws IOException {
        commitVariant("core1", "Core'");
        commitVariant("core2", "Core'");
        commitVariant("branchA", "Core', BranchA", "a.txt");

        FeatureRepository featureRepository = new FeatureRepository(application);
        FeatureVersionModel[] versions = featureRepository.getFeatureVersionsFromFeature("Core");

        assertEquals(3, versions.length, "each of the 3 commits forced its own new feature revision via the trailing '");
        for (FeatureVersionModel version : versions) {
            assertNotNull(version.getVersion());
        }
    }

    @Test
    public void getFeatureVersionsFromFeatureReturnsEmptyArrayForAnUnknownFeature() throws IOException {
        commitVariant("core", "Core");

        FeatureRepository featureRepository = new FeatureRepository(application);

        assertEquals(0, featureRepository.getFeatureVersionsFromFeature("NoSuchFeature").length);
    }

    @Test
    public void updateFeatureVersionFromFeatureChangesOnlyTheMatchingVersion() throws IOException {
        commitVariant("core1", "Core'");
        commitVariant("core2", "Core'");

        FeatureRepository featureRepository = new FeatureRepository(application);
        FeatureVersionModel[] before = featureRepository.getFeatureVersionsFromFeature("Core");
        String targetVersion = before[0].getVersion();

        FeatureVersionModel edit = new FeatureVersionModel(targetVersion, "first core revision");
        FeatureVersionModel[] after = featureRepository.updateFeatureVersionFromFeature("Core", edit);

        FeatureVersionModel changed = Arrays.stream(after).filter(v -> v.getVersion().equals(targetVersion)).findFirst().orElseThrow();
        assertEquals("first core revision", changed.getDescription());

        long unchangedCount = Arrays.stream(after).filter(v -> !v.getVersion().equals(targetVersion)).filter(v -> v.getDescription() == null).count();
        assertEquals(after.length - 1, unchangedCount, "every other revision must keep its original null description (a FeatureRevision's description, unlike a Feature's, has no default)");
    }

    @Test
    public void getNumberRevisionsPerFeatureCountsOnlyFeaturesWithAtLeastOneRevision() throws IOException {
        commitVariant("core1", "Core'");
        commitVariant("core2", "Core'");
        commitVariant("branchA", "Core', BranchA", "a.txt");

        FeatureRepository featureRepository = new FeatureRepository(application);
        NumberRevisionsPerFeature[] counts = featureRepository.getNumberRevisionsPerFeature();

        NumberRevisionsPerFeature core = Arrays.stream(counts).filter(c -> c.getFeatureName().equals("Core")).findFirst().orElseThrow();
        NumberRevisionsPerFeature branchA = Arrays.stream(counts).filter(c -> c.getFeatureName().equals("BranchA")).findFirst().orElseThrow();
        assertEquals(3, core.getNumberRevisions());
        assertEquals(1, branchA.getNumberRevisions());
    }
}
