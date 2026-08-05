package at.jku.isse.ecco.repository;

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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository.Op.subset() (base/src/main/java/at/jku/isse/ecco/repository/Repository.java) is what
 * fork/pullFeaturesRepository actually use to build the subset repository merged into the target
 * (see EccoService#forkAlreadyOpen -> Repository.Op#subset, and Repository.Op#copy, a thin wrapper
 * around it). subset_old()/merge_old() are dead code - grepped, nothing outside Repository.java
 * itself calls them - so deliberately not tested here.
 */
public class RepositoryOpSubsetTest {

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
    public void subsetWithNothingDeselectedCopiesEverything() {
        Repository.Op source = newRepository();
        source.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");

        Repository.Op subset = source.subset(List.of(), source.getMaxOrder(), ef);

        assertEquals(1, subset.getFeatures().size());
        assertEquals(1, subset.getAssociations().size());
        Association.Op copiedAssociation = subset.getAssociations().iterator().next();
        assertEquals("fileA.txt", copiedAssociation.getRootNode().getChildren().iterator().next().getArtifact().getData().toString());
    }

    @Test
    @Timeout(10)
    public void subsetExcludesTheFeatureAndAssociationOfADeselectedRevision() {
        Repository.Op source = newRepository();
        Configuration configurationA = singleFeatureConfiguration("A");
        FeatureRevision revisionA = configurationA.getFeatureRevisions()[0];
        source.extract(configurationA, Set.of(fileNode("fileA.txt")), "alice");
        source.extract(singleFeatureConfiguration("B"), Set.of(fileNode("fileB.txt")), "alice");

        Repository.Op subset = source.subset(List.of(revisionA), source.getMaxOrder(), ef);

        assertTrue(subset.getFeatures().stream().noneMatch(f -> "A".equals(f.getName())), "A's only revision was deselected, so A itself should be dropped");
        assertTrue(subset.getFeatures().stream().anyMatch(f -> "B".equals(f.getName())));
        assertEquals(1, subset.getAssociations().size(), "only B's association should survive");
        assertEquals("fileB.txt", subset.getAssociations().iterator().next().getRootNode().getChildren().iterator().next().getArtifact().getData().toString());
    }

    @Test
    @Timeout(10)
    public void subsetWithAReducedMaxOrderCoarsensAssociationsInsteadOfDroppingThem() {
        // Committing A and B together gives the association THREE module observations, not one -
        // Repository.Op.addConfigurationModules() computes the full powerset, so alongside the joint
        // d^1(A,B) module (Module.getOrder() = pos.length + neg.length - 1, so two positive features
        // is order 1) it also separately observes d^0(A) and d^0(B) alone. subset()'s per-module order
        // filter (Repository.java: "module.getOrder() <= newRepository.getMaxOrder()") drops only the
        // joint module when maxOrder is reduced below it - the two order-0 observations still survive
        // and are enough on their own to keep the association (with a coarser, less specific
        // condition), so a reduced maxOrder does NOT simply drop associations built from
        // above-the-limit commits the way a first read of the method might suggest.
        Repository.Op source = newRepository();
        FeatureRevision revisionA = ef.createFeature(UUID.randomUUID().toString(), "A").addRevision(UUID.randomUUID().toString());
        FeatureRevision revisionB = ef.createFeature(UUID.randomUUID().toString(), "B").addRevision(UUID.randomUUID().toString());
        Configuration combined = ef.createConfiguration(new FeatureRevision[]{revisionA, revisionB});
        source.extract(combined, Set.of(fileNode("fileAB.txt")), "alice");
        assertEquals(3, source.getAssociations().iterator().next().computeCondition().getModules().size());

        Repository.Op subsetAtOrderZero = source.subset(List.of(), 0, ef);
        assertEquals(2, subsetAtOrderZero.getFeatures().size(), "features are copied independently of maxOrder");
        assertEquals(1, subsetAtOrderZero.getAssociations().size(), "the association survives on its two order-0 (single-feature) observations alone");
        assertEquals(2, subsetAtOrderZero.getAssociations().iterator().next().computeCondition().getModules().size(), "only the joint order-1 module should have been dropped");

        Repository.Op subsetAtOrderOne = source.subset(List.of(), 1, ef);
        assertEquals(3, subsetAtOrderOne.getAssociations().iterator().next().computeCondition().getModules().size(), "maxOrder=1 is enough to also admit the joint A+B module");
    }

    @Test
    @Timeout(10)
    public void copyProducesAnIndependentFullCopy() {
        Repository.Op source = newRepository();
        source.extract(singleFeatureConfiguration("A"), Set.of(fileNode("fileA.txt")), "alice");

        Repository.Op copy = source.copy(ef);

        assertEquals(1, copy.getAssociations().size());

        // mutating the copy must not reach back into the source
        copy.extract(singleFeatureConfiguration("B"), Set.of(fileNode("fileB.txt")), "alice");
        assertEquals(1, source.getFeatures().size(), "source must be unaffected by commits made to the copy");
    }
}
