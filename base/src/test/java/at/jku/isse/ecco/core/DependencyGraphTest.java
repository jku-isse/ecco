package at.jku.isse.ecco.core;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DependencyGraph (base/src/main/java/at/jku/isse/ecco/core/DependencyGraph.java) computes
 * cross-association dependencies from ArtifactReference "uses" edges between unique artifacts -
 * used by Repository.Op.compose(Collection, boolean)/subset() to find associations a selection
 * transitively depends on. These build two associations with an explicit cross-reference between
 * their artifacts (ef.createNode(artifact) wires artifact.containingNode automatically, and nodes
 * are unique=true by default - see SerEntityFactory/SerNode), independent of any adapter.
 * <p>
 * The node.getParent()-based "parent" dependency edge (DependencyGraph.java:122-147, for a node
 * whose parent's artifact belongs to a DIFFERENT association than the node's own) only arises from
 * composed/merged trees (e.g. LazyCompositionRootNode) where a node's tree-parent and its
 * association diverge - not reproducible with a plain hand-built tree, so not covered here.
 */
public class DependencyGraphTest {

    private final EntityFactory ef = new SerEntityFactory();

    private record AssociationWithArtifact(Association.Op association, Artifact.Op<TestArtifactData> artifact) {
    }

    private AssociationWithArtifact associationWithArtifact(String artifactId) {
        Artifact.Op<TestArtifactData> artifact = ef.createArtifact(new TestArtifactData(artifactId));
        Node.Op node = ef.createNode(artifact);
        Association.Op association = ef.createAssociation(Set.of(node));
        association.setId(UUID.randomUUID().toString());
        return new AssociationWithArtifact(association, artifact);
    }

    @Test
    public void resolvedCrossReferenceProducesADependencyWhenBothAssociationsAreIncluded() {
        AssociationWithArtifact a = associationWithArtifact("A");
        AssociationWithArtifact b = associationWithArtifact("B");
        a.artifact().addUses(b.artifact(), "references");

        DependencyGraph dg = new DependencyGraph(List.of(a.association(), b.association()));

        assertEquals(1, dg.getDependencies().size());
        assertTrue(dg.getUnresolvedDependencies().isEmpty());
        DependencyGraph.Dependency dependency = dg.getDependency(a.association(), b.association());
        assertNotNull(dependency);
        assertEquals(1, dependency.getWeight());
    }

    @Test
    public void referenceToAnExcludedAssociationIsUnresolvedByDefault() {
        AssociationWithArtifact a = associationWithArtifact("A");
        AssociationWithArtifact b = associationWithArtifact("B");
        a.artifact().addUses(b.artifact(), "references");

        // LEAVE_REFERENCES_UNRESOLVED is the default (DependencyGraph.compute(Collection)) - only
        // a's association is included, so the reference to b's association can't be resolved.
        DependencyGraph dg = new DependencyGraph(List.of(a.association()));

        assertTrue(dg.getDependencies().isEmpty());
        assertEquals(1, dg.getUnresolvedDependencies().size());
        assertNotNull(dg.getUnresolvedDependency(a.association(), b.association()));
    }

    @Test
    public void includeAllReferencedAssociationsModePullsInTheMissingAssociation() {
        AssociationWithArtifact a = associationWithArtifact("A");
        AssociationWithArtifact b = associationWithArtifact("B");
        a.artifact().addUses(b.artifact(), "references");

        DependencyGraph dg = new DependencyGraph(List.of(a.association()), DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);

        assertEquals(Set.of(a.association(), b.association()), Set.copyOf(dg.getAssociations()));
        assertEquals(1, dg.getDependencies().size());
        assertTrue(dg.getUnresolvedDependencies().isEmpty());
    }

    /**
     * TRIM_UNRESOLVED_ARTIFACT_REFERENCES is dead code - grepped, nothing outside DependencyGraph.java
     * and this test ever constructs a DependencyGraph with this mode - and it turns out to be
     * completely broken: DependencyGraph.computeRec() calls {@code it.remove()} on the iterator from
     * {@code node.getArtifact().getUses().iterator()} to trim the dangling reference, but
     * SerArtifact.getUses() (SerArtifact.java:252-254) returns
     * {@code Collections.unmodifiableCollection(this.uses)} - an iterator over an unmodifiable view
     * can never support remove(). Every call with this mode throws UnsupportedOperationException
     * instead of trimming anything. Characterized as-is (the mode being unused in production is
     * presumably exactly why this was never caught); not fixed here, since fixing it means deciding
     * whether SerArtifact.getUses() should stop being defensively unmodifiable - a real design
     * tradeoff, not a test-only change.
     */
    @Test
    public void trimUnresolvedArtifactReferencesModeIsBrokenByAnUnmodifiableUsesCollection() {
        AssociationWithArtifact a = associationWithArtifact("A");
        AssociationWithArtifact b = associationWithArtifact("B");
        a.artifact().addUses(b.artifact(), "references");

        assertThrows(UnsupportedOperationException.class, () ->
                new DependencyGraph(List.of(a.association()), DependencyGraph.ReferencesResolveMode.TRIM_UNRESOLVED_ARTIFACT_REFERENCES));
    }

    @Test
    public void getGMLStringContainsOneNodePerAssociationAndOneEdgePerDependency() {
        AssociationWithArtifact a = associationWithArtifact("A");
        AssociationWithArtifact b = associationWithArtifact("B");
        a.artifact().addUses(b.artifact(), "references");

        DependencyGraph dg = new DependencyGraph(List.of(a.association(), b.association()));
        String gml = dg.getGMLString();

        assertTrue(gml.contains("id " + a.association().getId()));
        assertTrue(gml.contains("id " + b.association().getId()));
        assertTrue(gml.contains("source " + a.association().getId()));
        assertTrue(gml.contains("target " + b.association().getId()));
    }
}
