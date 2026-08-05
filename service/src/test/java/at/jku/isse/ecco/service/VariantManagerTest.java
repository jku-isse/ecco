package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VariantManager is only reachable through EccoService's public addVariant/removeVariant/updateVariant
 * methods (it reads owner's package-private listeners/transactionStrategy/repositoryDao fields directly,
 * so it can't be driven against a mocked EccoService) - these tests exercise it through a real,
 * filesystem-backed EccoService the same way CommitVariantReproTest does.
 */
public class VariantManagerTest {

    private EccoService initServiceWithOneCommit(Path workDir) throws IOException {
        Path repoDir = workDir.resolve(".ecco");
        EccoService service = new EccoService();
        service.setRepositoryDir(repoDir);
        service.init();

        Path p = workDir.resolve("core");
        Files.createDirectories(p);
        Files.writeString(p.resolve("core.txt"), "core\n");
        service.setBaseDir(p);
        service.commit("first commit", "Core");

        return service;
    }

    @Test
    @Timeout(30)
    public void addVariantAddsDistinctVariant() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-add");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            assertEquals(1, service.getRepository().getVariants().size(), "commit auto-creates one variant");

            Configuration emptyConfiguration = service.parseConfigurationString("");
            service.addVariant(emptyConfiguration, "empty-variant", "no features selected");

            ArrayList<Variant> variants = service.getRepository().getVariants();
            assertEquals(2, variants.size());

            Variant added = variants.stream().filter(v -> "empty-variant".equals(v.getName())).findFirst().orElseThrow();
            assertEquals("no features selected", added.getDescription());
            assertEquals(emptyConfiguration.getConfigurationString(), added.getConfiguration().getConfigurationString());
        }
    }

    @Test
    @Timeout(30)
    public void addVariantDoesNotDuplicateExistingConfiguration() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-add-dup");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            assertEquals(1, service.getRepository().getVariants().size());

            Configuration coreConfiguration = service.parseConfigurationString("Core");
            service.addVariant(coreConfiguration, "duplicate", "should not be added");

            assertEquals(1, service.getRepository().getVariants().size(), "adding an already-present configuration must be a no-op");
        }
    }

    @Test
    @Timeout(30)
    public void removeVariantByIdRemovesVariant() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-remove-id");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            service.addVariant(service.parseConfigurationString(""), "second", "desc");
            assertEquals(2, service.getRepository().getVariants().size());

            Variant second = service.getRepository().getVariants().stream()
                    .filter(v -> "second".equals(v.getName())).findFirst().orElseThrow();

            service.removeVariant(second.getId());

            ArrayList<Variant> remaining = service.getRepository().getVariants();
            assertEquals(1, remaining.size());
            assertNotEquals("second", remaining.get(0).getName());
        }
    }

    @Test
    @Timeout(30)
    public void removeVariantByConfigurationRemovesVariant() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-remove-config");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            Configuration emptyConfiguration = service.parseConfigurationString("");
            service.addVariant(emptyConfiguration, "second", "desc");
            assertEquals(2, service.getRepository().getVariants().size());

            service.removeVariant(emptyConfiguration);

            ArrayList<Variant> remaining = service.getRepository().getVariants();
            assertEquals(1, remaining.size());
            assertNotEquals("second", remaining.get(0).getName());
        }
    }

    @Test
    @Timeout(30)
    public void removeVariantWithUnknownIdIsNoOp() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-remove-unknown");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            int before = service.getRepository().getVariants().size();

            assertDoesNotThrow(() -> service.removeVariant("does-not-exist"));

            assertEquals(before, service.getRepository().getVariants().size());
        }
    }

    @Test
    @Timeout(30)
    public void updateVariantUpdatesNameAndConfiguration() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-update");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            Variant original = service.getRepository().getVariants().get(0);
            Configuration emptyConfiguration = service.parseConfigurationString("");

            service.updateVariant(emptyConfiguration, "renamed", original.getId());

            Variant updated = service.getRepository().getVariant(original.getId());
            assertEquals("renamed", updated.getName());
            assertEquals(emptyConfiguration.getConfigurationString(), updated.getConfiguration().getConfigurationString());
        }
    }

    @Test
    @Timeout(30)
    public void updateVariantWithUnknownIdIsNoOp() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-update-unknown");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            Variant before = service.getRepository().getVariants().get(0);

            assertDoesNotThrow(() -> service.updateVariant(service.parseConfigurationString(""), "renamed", "does-not-exist"));

            Variant unchanged = service.getRepository().getVariant(before.getId());
            assertEquals(before.getName(), unchanged.getName());
            assertEquals(before.getConfiguration().getConfigurationString(), unchanged.getConfiguration().getConfigurationString());
        }
    }

    /**
     * "Core'" (trailing apostrophe) forces {@code parseConfigurationString} to mint a brand new
     * FeatureRevision instead of reusing the feature's latest one (EccoService.java:623-629), which is
     * the only way to accumulate more than one revision of the same feature for these tests.
     */
    private EccoService initServiceWithTwoRevisionsOfCore(Path workDir) throws IOException {
        Path repoDir = workDir.resolve(".ecco");
        EccoService service = new EccoService();
        service.setRepositoryDir(repoDir);
        service.init();

        Path p = workDir.resolve("core");
        Files.createDirectories(p);
        service.setBaseDir(p);

        Files.writeString(p.resolve("core.txt"), "core v1\n");
        service.commit("first commit", "Core'");

        Files.writeString(p.resolve("core.txt"), "core v2\n");
        service.commit("second commit", "Core'");

        return service;
    }

    @Test
    @Timeout(30)
    public void updateFeatureRevisionSwapsVariantToNewRevision() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-update-revision");
        try (EccoService service = initServiceWithTwoRevisionsOfCore(workDir)) {
            Feature core = service.getRepository().getFeatures().stream()
                    .filter(f -> "Core".equals(f.getName())).findFirst().orElseThrow();
            assertEquals(2, core.getRevisions().size(), "each 'Core'' commit should mint a new revision");

            ArrayList<Variant> variants = service.getRepository().getVariants();
            assertEquals(2, variants.size(), "each commit's distinct configuration should auto-create its own variant");

            Variant firstVariant = variants.get(0);
            FeatureRevision originalRevision = firstVariant.getConfiguration().getFeatureRevisions()[0];
            FeatureRevision otherRevision = core.getRevisions().stream()
                    .filter(r -> !r.equals(originalRevision)).findFirst().orElseThrow();

            service.updateFeatureRevision(originalRevision, otherRevision.getFeatureRevisionString(), firstVariant.getId());

            Variant updated = service.getRepository().getVariant(firstVariant.getId());
            FeatureRevision[] updatedRevisions = updated.getConfiguration().getFeatureRevisions();
            assertEquals(1, updatedRevisions.length);
            assertEquals(otherRevision.getFeatureRevisionString(), updatedRevisions[0].getFeatureRevisionString());
        }
    }

    @Test
    @Timeout(30)
    public void removeFeatureRevisionClearsSingleRevisionConfiguration() throws IOException {
        Path workDir = Files.createTempDirectory("variant-manager-remove-revision");
        try (EccoService service = initServiceWithOneCommit(workDir)) {
            Variant variant = service.getRepository().getVariants().get(0);
            FeatureRevision revision = variant.getConfiguration().getFeatureRevisions()[0];

            service.removeFeatureRevision(revision, variant.getId());

            Variant updated = service.getRepository().getVariant(variant.getId());
            assertEquals(0, updated.getConfiguration().getFeatureRevisions().length);
            assertEquals("", updated.getConfiguration().getConfigurationString());
        }
    }
}
