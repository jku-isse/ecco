package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
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
 * Covers the smaller Repository.Op default methods not already exercised by
 * RepositoryOpExtractTest/RepositoryOpSubsetTest: getFeaturesByName(), setRetroactiveConditions(),
 * collectArtifacts(), and diff(). SerRepository does not override any of these (grepped), so they
 * genuinely run the base interface's default implementation, not a subclass override.
 * <p>
 * map(RootNode.Op) - real production use at EccoService.java:1637 (re-mapping a reader's freshly
 * re-read tree onto the repository's existing artifacts, e.g. so a changed file being edited live
 * keeps its trace information - see Repository.java's javadoc on map()) - is NOT covered here: it
 * requires an independently-built "reader" tree matched via Trees.map()'s content-equality-based
 * matching against the committed tree, which is really exercising Trees.map() itself (already its
 * own subsystem, see TreesMatchingTest) rather than anything specific to Repository.Op.
 */
public class RepositoryOpMiscTest {

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
    public void getFeaturesByNameReturnsOnlyMatchingFeatures() {
        Repository.Op repository = newRepository();
        repository.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");
        repository.extract(singleFeatureConfiguration("B"), Set.of(fileNode("fileB.txt")), "alice");

        assertEquals(1, repository.getFeaturesByName("A").size());
        assertEquals("A", repository.getFeaturesByName("A").iterator().next().getName());
        assertTrue(repository.getFeaturesByName("does-not-exist").isEmpty());
    }

    @Test
    @Timeout(10)
    public void setRetroactiveConditionsSetsTheConditionOnEveryUniqueNode() {
        Repository.Op repository = newRepository();
        repository.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");

        repository.setRetroactiveConditions();

        Association.Op association = repository.getAssociations().iterator().next();
        Node.Op fileNode = association.getRootNode().getChildren().iterator().next();
        assertTrue(fileNode.isUnique());
        assertNotNull(fileNode.getFeatureTrace().getRetroactiveConditionString());
        assertEquals(association.computeCondition().toLogicString(), fileNode.getFeatureTrace().getRetroactiveConditionString());
    }

    @Test
    @Timeout(10)
    public void collectArtifactsDeduplicatesASharedArtifactByIdentity() {
        Repository.Op repository = newRepository();
        Artifact.Op<TestArtifactData> sharedArtifact = ef.createArtifact(new TestArtifactData("shared"));
        Node.Op child1 = ef.createNode();
        child1.setArtifact(sharedArtifact);
        Node.Op child2 = ef.createNode();
        child2.setArtifact(sharedArtifact);
        // extract() takes a Set<Node.Op> of top-level nodes, and Node equality is content-based, so
        // child1/child2 (both content-equal, empty-of-children nodes wrapping the same data) can't be
        // passed directly - Set.of() would collapse them into one element before extract() even runs.
        // Distinctly-named wrapper parents keep them apart as top-level nodes while both still point
        // at the exact same Artifact.Op instance underneath.
        Node.Op wrapper1 = fileNode("wrapper1");
        wrapper1.addChild(child1);
        Node.Op wrapper2 = fileNode("wrapper2");
        wrapper2.addChild(child2);

        repository.extract(singleFeatureConfiguration("A"), Set.of(wrapper1, wrapper2), "alice");

        assertEquals(3, repository.collectArtifacts().size(), "wrapper1 + wrapper2 + the one shared artifact reachable from both, deduplicated");
    }

    @Test
    @Timeout(10)
    public void collectArtifactsReturnsEveryDistinctArtifact() {
        Repository.Op repository = newRepository();
        repository.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");
        repository.extract(singleFeatureConfiguration("B"), Set.of(fileNode("fileB.txt")), "alice");

        assertEquals(2, repository.collectArtifacts().size());
    }

    @Test
    @Timeout(10)
    public void diffIsNotYetImplemented() {
        Repository.Op repository = newRepository();

        assertThrows(UnsupportedOperationException.class, repository::diff);
    }
}
