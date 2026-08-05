package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository.Op.extract()/compose() (base/src/main/java/at/jku/isse/ecco/repository/Repository.java)
 * are the core commit/checkout algorithms - previously exercised only indirectly, as a side effect
 * of service-module integration tests driving them through EccoService/real file I/O. These build a
 * bare Repository.Op (SerEntityFactory.createRepository()) and hand-built trees (TestArtifactData,
 * same pattern as ConcurrentBranchSequencingTest/NodeUtil) to exercise extract()/compose() in
 * isolation, independent of any adapter or persistence layer.
 */
public class RepositoryOpExtractTest {

    private final EntityFactory ef = new SerEntityFactory();

    private Repository.Op newRepository() {
        Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        return repository;
    }

    private Configuration singleFeatureConfiguration(String featureName) {
        Feature feature = ef.createFeature(UUID.randomUUID().toString(), featureName);
        FeatureRevision revision = feature.addRevision(UUID.randomUUID().toString());
        return ef.createConfiguration(new FeatureRevision[]{revision});
    }

    private Node.Op fileNode(String name) {
        return ef.createNode(new TestArtifactData(name));
    }

    @Test
    @Timeout(10)
    public void extractCreatesAnAssociationAndRegistersTheFeature() {
        Repository.Op repository = newRepository();
        Configuration configuration = singleFeatureConfiguration("A");

        Commit commit = repository.extract(configuration, Set.of(fileNode("fileA.txt")), "alice");

        assertNotNull(commit);
        assertEquals("alice", commit.getUsername());
        assertTrue(repository.getCommits().contains(commit));

        assertEquals(1, repository.getFeatures().size());
        assertEquals("A", repository.getFeatures().iterator().next().getName());

        assertEquals(1, repository.getAssociations().size());
        Association.Op association = repository.getAssociations().iterator().next();
        assertEquals(1, association.getRootNode().getChildren().size());
        assertEquals("fileA.txt", association.getRootNode().getChildren().iterator().next().getArtifact().getData().toString());
    }

    @Test
    @Timeout(10)
    public void extractRejectsAConfigurationWithTwoRevisionsOfTheSameFeature() {
        Repository.Op repository = newRepository();
        Feature feature = ef.createFeature(UUID.randomUUID().toString(), "A");
        FeatureRevision revision1 = feature.addRevision(UUID.randomUUID().toString());
        FeatureRevision revision2 = feature.addRevision(UUID.randomUUID().toString());
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revision1, revision2});

        assertThrows(EccoException.class, () -> repository.extract(configuration, Set.of(fileNode("fileA.txt")), "alice"));
    }

    @Test
    @Timeout(10)
    public void extractingTwoDifferentFeaturesProducesTwoIndependentAssociations() {
        Repository.Op repository = newRepository();

        repository.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");
        repository.extract(singleFeatureConfiguration("B"), Set.of(fileNode("fileB.txt")), "alice");

        assertEquals(2, repository.getFeatures().size());
        assertEquals(2, repository.getAssociations().size());
    }

    @Test
    @Timeout(10)
    public void extractOfContentSharedWithAnEarlierCommitProducesOneIntersectionAssociation() {
        Repository.Op repository = newRepository();

        repository.extract(singleFeatureConfiguration("A"), Set.of(fileNode("shared.txt")), "alice");
        assertEquals(1, repository.getAssociations().size());

        // "B"'s commit is built from an independently-constructed node carrying the same
        // TestArtifactData("shared.txt") - Trees.slice() (called from extract(Association, Commit),
        // Repository.java:591) matches nodes by artifact data equality, not identity, so this should
        // be recognized as the same content as "A"'s commit, not a separate, unrelated file.
        repository.extract(singleFeatureConfiguration("B"), Set.of(fileNode("shared.txt")), "alice");

        // both commits consisted of nothing but the shared file, so slicing out the intersection
        // should leave both original associations empty (and therefore removed - Repository.java:623-631),
        // with the fully-shared content surviving as a single new association whose condition covers
        // both A and B.
        assertEquals(1, repository.getAssociations().size(), "the two single-file commits should collapse into one shared association");
        Association.Op sharedAssociation = repository.getAssociations().iterator().next();
        assertEquals(1, sharedAssociation.getRootNode().getChildren().size());
        assertEquals("shared.txt", sharedAssociation.getRootNode().getChildren().iterator().next().getArtifact().getData().toString());
    }

    @Test
    @Timeout(10)
    public void composeAfterExtractReturnsTheCommittedContent() {
        Repository.Op repository = newRepository();
        Configuration configuration = singleFeatureConfiguration("A");
        repository.extract(configuration, Set.of(fileNode("fileA.txt")), "alice");
        repository.buildMainTree();

        Checkout checkout = repository.compose(configuration);

        assertNotNull(checkout.getNode());
        assertTrue(checkout.getMissing().isEmpty(), "nothing should be missing for the exact configuration just committed");
    }

    @Test
    @Timeout(10)
    public void composeOfAnUncommittedFeatureCombinationReportsAMissingModule() {
        Repository.Op repository = newRepository();
        Configuration configurationA = singleFeatureConfiguration("A");
        FeatureRevision revisionA = configurationA.getFeatureRevisions()[0];
        repository.extract(configurationA, Set.of(fileNode("fileA.txt")), "alice");

        Configuration configurationB = singleFeatureConfiguration("B");
        FeatureRevision revisionB = configurationB.getFeatureRevisions()[0];
        repository.extract(configurationB, Set.of(fileNode("fileB.txt")), "alice");
        repository.buildMainTree();

        // A and B were each committed on their own - never together - so the order-2 "A AND B"
        // module was never observed/counted (Repository.java's addConfigurationModules() only ever
        // builds modules from a single commit's own feature set).
        Configuration combined = ef.createConfiguration(new FeatureRevision[]{revisionA, revisionB});

        Checkout checkout = repository.compose(combined);

        assertFalse(checkout.getMissing().isEmpty(), "A and B were never committed together, so their combined module should be reported missing");
    }

    @Test
    @Timeout(10)
    public void mergeCopiesFeaturesConstraintsAndAssociationsFromTheOtherRepository() {
        Repository.Op source = newRepository();
        source.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");
        source.addConstraint(at.jku.isse.ecco.core.Constraint.Kind.MANDATORY, "A", null);

        Repository.Op target = newRepository();
        target.merge(source);

        assertEquals(1, target.getFeatures().size());
        assertEquals("A", target.getFeatures().iterator().next().getName());
        assertEquals(1, target.getAssociations().size());
        assertEquals(1, target.getConstraints().size());
    }

    /**
     * Repository.Op.merge()'s per-association loop (Repository.java, the "for every association in
     * other repository" section) builds a Commit via {@code this.extract(association, commit)} for
     * every merged association, but never calls {@code this.addCommit(commit)} on it afterward - so
     * none of those commits ever show up in the merged-into repository's commit log, even though the
     * feature/association data they carried was merged in correctly. Pinned here at the algorithm
     * level; also visible through the REST layer as
     * FileRepositoryServiceTest#forkRepositoryTransfersFeaturesButDropsTheCommitLog. Not fixed as
     * part of this (test-only) change - see feedback-risk-methodology in project memory on escalating
     * rather than changing core algorithms as a side effect of adding tests.
     */
    @Test
    @Timeout(10)
    public void mergeDoesNotRegisterItsOwnMergeCommits() {
        Repository.Op source = newRepository();
        source.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");

        Repository.Op target = newRepository();
        target.merge(source);

        assertTrue(target.getCommits().isEmpty(), "merge()'s per-association commits are never registered via addCommit()");
    }
}
