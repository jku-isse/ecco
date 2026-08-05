package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestCommit;
import at.jku.isse.ecco.rest.models.RestFeature;
import at.jku.isse.ecco.rest.models.RestFeatureRevision;
import at.jku.isse.ecco.rest.models.RestRepository;
import at.jku.isse.ecco.rest.models.RestVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RepositoryHandler holds a real, filesystem-backed EccoService (it constructs one itself rather
 * than accepting an injected instance), so - like VariantManagerTest/CommitVariantReproTest in the
 * service module - these drive it against real temp directories rather than mocking.
 */
public class RepositoryHandlerTest {

    private RepositoryHandler createRepositoryWithOneCommit(Path workDir, String featureConfig, String fileContent) throws IOException {
        RepositoryHandler handler = new RepositoryHandler(workDir, 1);
        handler.createRepository();

        Path commitFolder = workDir.resolve("commit-source");
        Files.createDirectories(commitFolder);
        Files.writeString(commitFolder.resolve("file.txt"), fileContent);
        handler.addCommit("first commit", featureConfig, commitFolder, "alice");

        return handler;
    }

    private RestVariant findVariantByName(RestRepository repository, String name) {
        return repository.getVariants().stream().filter(v -> name.equals(v.getName())).findFirst().orElseThrow();
    }

    @Test
    @Timeout(30)
    public void isInitializedReflectsWhetherTheEccoServiceHasBeenOpened() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-init");
        RepositoryHandler handler = new RepositoryHandler(workDir, 1);

        assertFalse(handler.isInitialized());

        handler.createRepository();

        assertTrue(handler.isInitialized());
    }

    @Test
    @Timeout(30)
    public void createRepositoryExposesIdAndDirectoryNameThroughGetRepository() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-create");
        RepositoryHandler handler = new RepositoryHandler(workDir, 42);

        handler.createRepository();
        RestRepository repository = handler.getRepository();

        assertEquals(42, repository.getRepositoryHandlerId());
        assertEquals(workDir.getFileName().toString(), repository.getName());
        assertEquals(workDir, handler.getPath());
    }

    @Test
    @Timeout(30)
    public void getRepositoryLazilyOpensAnAlreadyInitializedRepository() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-lazy-open");
        new RepositoryHandler(workDir, 1).createRepository();

        RepositoryHandler reopened = new RepositoryHandler(workDir, 2);
        assertFalse(reopened.isInitialized());

        RestRepository repository = reopened.getRepository();

        assertTrue(reopened.isInitialized());
        assertEquals(2, repository.getRepositoryHandlerId());
    }

    @Test
    @Timeout(30)
    public void addCommitCreatesACommitAndAnAutoVariant() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-commit");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");

        RestRepository repository = handler.getRepository();

        Collection<RestCommit> commits = repository.getCommits();
        assertEquals(1, commits.size());
        RestCommit commit = commits.iterator().next();
        assertEquals("first commit", commit.getCommitMessage());
        assertEquals("alice", commit.getUsername());

        assertEquals(1, repository.getVariants().size(), "committing auto-creates one variant");
    }

    @Test
    @Timeout(30)
    public void addVariantAddsADistinctVariant() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-add-variant");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");
        assertEquals(1, handler.getRepository().getVariants().size());

        RestRepository afterAdd = handler.addVariant("empty-variant", "", "no features selected");

        assertEquals(2, afterAdd.getVariants().size());
        RestVariant added = findVariantByName(afterAdd, "empty-variant");
        assertEquals("no features selected", added.getDescription());
        assertTrue(added.getConfiguration().getFeatureRevisions().isEmpty());
    }

    @Test
    @Timeout(30)
    public void removeVariantDeletesIt() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-remove-variant");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");
        handler.addVariant("second", "", "desc");
        String secondId = findVariantByName(handler.getRepository(), "second").getId();

        RestRepository afterRemove = handler.removeVariant(secondId);

        assertEquals(1, afterRemove.getVariants().size());
        assertNotEquals("second", afterRemove.getVariants().iterator().next().getName());
    }

    @Test
    @Timeout(30)
    public void variantSetNameDescriptionUpdatesTheVariant() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-set-name-description");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");
        String variantId = handler.getRepository().getVariants().iterator().next().getId();

        RestRepository updated = handler.variantSetNameDescription(variantId, "renamed", "new description");

        RestVariant variant = updated.getVariants().stream().filter(v -> v.getId().equals(variantId)).findFirst().orElseThrow();
        assertEquals("renamed", variant.getName());
        assertEquals("new description", variant.getDescription());
    }

    @Test
    @Timeout(30)
    public void variantAddFeatureAddsAnotherFeaturesLatestRevisionToTheVariant() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-variant-add-feature");
        RepositoryHandler handler = new RepositoryHandler(workDir, 1);
        handler.createRepository();

        Path commitFolderA = workDir.resolve("commit-a");
        Files.createDirectories(commitFolderA);
        Files.writeString(commitFolderA.resolve("a.txt"), "a\n");
        handler.addCommit("commit A", "A", commitFolderA, "alice");

        Path commitFolderB = workDir.resolve("commit-b");
        Files.createDirectories(commitFolderB);
        Files.writeString(commitFolderB.resolve("b.txt"), "b\n");
        handler.addCommit("commit B", "B", commitFolderB, "alice");

        RestRepository repository = handler.getRepository();
        assertEquals(2, repository.getVariants().size());

        RestVariant variantA = repository.getVariants().stream()
                .filter(v -> v.getConfiguration().getFeatureRevisions().stream().anyMatch(r -> "A".equals(r.getFeatureName())))
                .findFirst().orElseThrow();
        String featureBId = repository.getFeatures().stream().filter(f -> "B".equals(f.getName())).findFirst().orElseThrow().getId();

        RestRepository afterAdd = handler.variantAddFeature(variantA.getId(), featureBId);

        RestVariant updatedVariantA = afterAdd.getVariants().stream().filter(v -> v.getId().equals(variantA.getId())).findFirst().orElseThrow();
        List<String> featureNames = updatedVariantA.getConfiguration().getFeatureRevisions().stream().map(RestFeatureRevision::getFeatureName).toList();
        assertEquals(2, featureNames.size());
        assertTrue(featureNames.contains("A"));
        assertTrue(featureNames.contains("B"));
    }

    @Test
    @Timeout(30)
    public void variantUpdateFeatureSwapsInADifferentRevisionOfTheSameFeature() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-variant-update-feature");
        RepositoryHandler handler = new RepositoryHandler(workDir, 1);
        handler.createRepository();

        Path commitFolder = workDir.resolve("commit-source");
        Files.createDirectories(commitFolder);
        Files.writeString(commitFolder.resolve("core.txt"), "core v1\n");
        handler.addCommit("first commit", "Core'", commitFolder, "alice");
        Files.writeString(commitFolder.resolve("core.txt"), "core v2\n");
        handler.addCommit("second commit", "Core'", commitFolder, "alice");

        RestRepository repository = handler.getRepository();
        assertEquals(2, repository.getVariants().size());

        RestVariant firstVariant = repository.getVariants().iterator().next();
        String originalRevisionId = firstVariant.getConfiguration().getFeatureRevisions().iterator().next().getId();

        RestFeature coreFeature = repository.getFeatures().stream()
                .filter(f -> "Core".equals(f.getName())).findFirst().orElseThrow();
        String otherRevisionId = coreFeature.getRevisions().stream()
                .map(RestFeatureRevision::getId).filter(id -> !id.equals(originalRevisionId)).findFirst().orElseThrow();

        RestRepository afterUpdate = handler.variantUpdateFeature(firstVariant.getId(), "Core", otherRevisionId);

        RestVariant updated = afterUpdate.getVariants().stream().filter(v -> v.getId().equals(firstVariant.getId())).findFirst().orElseThrow();
        String updatedRevisionId = updated.getConfiguration().getFeatureRevisions().iterator().next().getId();
        assertEquals(otherRevisionId, updatedRevisionId);
    }

    @Test
    @Timeout(30)
    public void variantRemoveFeatureRemovesOnlyThatFeaturesRevision() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-variant-remove-feature");
        RepositoryHandler handler = new RepositoryHandler(workDir, 1);
        handler.createRepository();

        Path commitFolderA = workDir.resolve("commit-a");
        Files.createDirectories(commitFolderA);
        Files.writeString(commitFolderA.resolve("a.txt"), "a\n");
        handler.addCommit("commit A,B", "A',B'", commitFolderA, "alice");

        RestRepository repository = handler.getRepository();
        RestVariant variant = repository.getVariants().iterator().next();
        assertEquals(2, variant.getConfiguration().getFeatureRevisions().size());

        RestRepository afterRemove = handler.variantRemoveFeature(variant.getId(), "A");

        RestVariant updated = afterRemove.getVariants().stream().filter(v -> v.getId().equals(variant.getId())).findFirst().orElseThrow();
        List<String> remainingFeatures = updated.getConfiguration().getFeatureRevisions().stream().map(RestFeatureRevision::getFeatureName).toList();
        assertEquals(List.of("B"), remainingFeatures);
    }

    @Test
    @Timeout(30)
    public void setFeatureDescriptionUpdatesTheFeature() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-set-feature-description");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");
        String featureId = handler.getRepository().getFeatures().iterator().next().getId();

        RestRepository updated = handler.setFeatureDescription(featureId, "the core feature");

        assertEquals("the core feature", updated.getFeatures().stream().filter(f -> f.getId().equals(featureId)).findFirst().orElseThrow().getDescription());
    }

    @Test
    @Timeout(30)
    public void checkoutWritesTheConfiguredFilesToTheTargetPath() throws IOException {
        Path workDir = Files.createTempDirectory("repository-handler-checkout");
        RepositoryHandler handler = createRepositoryWithOneCommit(workDir, "Core", "core content\n");
        String variantId = handler.getRepository().getVariants().iterator().next().getId();

        Path checkoutPath = Files.createTempDirectory("repository-handler-checkout-target");
        handler.checkout(variantId, checkoutPath);

        assertTrue(Files.exists(checkoutPath.resolve("file.txt")));
        assertEquals("core content\n", Files.readString(checkoutPath.resolve("file.txt")));
    }
}
