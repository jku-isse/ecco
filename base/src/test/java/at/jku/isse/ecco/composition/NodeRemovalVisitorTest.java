package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NodeRemovalVisitor.dfVisit() had 100% line coverage but only 37.5% complexity coverage before this
 * (see the coverageSummary Gradle task) - it was only ever exercised indirectly through
 * CheckoutComposer's end-to-end checkout tests, which happen to walk every line at least once but
 * never independently exercise each branch of its 4-way OR condition. These isolate the visitor
 * directly with mocked Configuration/EvaluationStrategy/FeatureTrace, since its logic is entirely a
 * handful of boolean checks against those interfaces - no real domain-object construction needed to
 * exercise every branch.
 */
public class NodeRemovalVisitorTest {

    private final EntityFactory ef = new SerEntityFactory();

    private Node.Op childOf(Node.Op parent, Node.Op child) {
        parent.addChild(child);
        child.setParent(parent);
        return child;
    }

    @Test
    public void removesANonUniqueChildlessNode() {
        Node.Op parent = ef.createRootNode();
        Node.Op child = childOf(parent, ef.createNode(new TestArtifactData("child")));
        child.setUnique(false);

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(mock(Configuration.class), mock(EvaluationStrategy.class));
        visitor.dfVisit(child);

        assertFalse(parent.getChildren().contains(child));
    }

    @Test
    public void keepsANonUniqueNodeThatHasChildren() {
        Node.Op parent = ef.createRootNode();
        Node.Op child = childOf(parent, ef.createNode(new TestArtifactData("child")));
        child.setUnique(false);
        childOf(child, ef.createNode(new TestArtifactData("grandchild")));

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(mock(Configuration.class), mock(EvaluationStrategy.class));
        visitor.dfVisit(child);

        assertTrue(parent.getChildren().contains(child), "a non-unique node with children is only a partial skeleton match, not removable");
    }

    @Test
    public void removesAChildlessNodeWithNoArtifact() {
        Node.Op parent = ef.createRootNode();
        Node.Op child = childOf(parent, ef.createNode()); // no artifact - e.g. a plugin/folder node

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(mock(Configuration.class), mock(EvaluationStrategy.class));
        visitor.dfVisit(child);

        assertFalse(parent.getChildren().contains(child));
    }

    @Test
    public void removesAChildlessNodeWithNoFeatureTrace() {
        // SerNode(Artifact.Op) (the constructor behind ef.createNode(artifactData)) always auto-creates
        // a FeatureTrace alongside the artifact - the only way to get a node with a real artifact but a
        // null feature trace is the no-arg constructor path (ef.createNode()) with setArtifact() called
        // afterward, bypassing that auto-creation.
        Node.Op parent = ef.createRootNode();
        Node.Op child = ef.createNode();
        child.setArtifact(ef.createArtifact(new TestArtifactData("child")));
        childOf(parent, child);
        assertNull(child.getFeatureTrace());

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(mock(Configuration.class), mock(EvaluationStrategy.class));
        visitor.dfVisit(child);

        assertFalse(parent.getChildren().contains(child));
    }

    @Test
    public void removesAChildlessNodeWhoseFeatureTraceDoesNotHold() {
        Node.Op parent = ef.createRootNode();
        Node.Op child = childOf(parent, ef.createNode(new TestArtifactData("child")));
        Configuration configuration = mock(Configuration.class);
        EvaluationStrategy evaluationStrategy = mock(EvaluationStrategy.class);
        FeatureTrace featureTrace = mock(FeatureTrace.class);
        when(featureTrace.holds(configuration, evaluationStrategy)).thenReturn(false);
        child.setFeatureTrace(featureTrace);

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(configuration, evaluationStrategy);
        visitor.dfVisit(child);

        assertFalse(parent.getChildren().contains(child));
    }

    @Test
    public void keepsANodeThatIsUniqueHasAnArtifactAndWhoseFeatureTraceHolds() {
        Node.Op parent = ef.createRootNode();
        Node.Op child = childOf(parent, ef.createNode(new TestArtifactData("child")));
        Configuration configuration = mock(Configuration.class);
        EvaluationStrategy evaluationStrategy = mock(EvaluationStrategy.class);
        FeatureTrace featureTrace = mock(FeatureTrace.class);
        when(featureTrace.holds(configuration, evaluationStrategy)).thenReturn(true);
        child.setFeatureTrace(featureTrace);

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(configuration, evaluationStrategy);
        visitor.dfVisit(child);

        assertTrue(parent.getChildren().contains(child));
    }

    @Test
    public void keepsANonRemovableNodeEvenWithoutAParent() {
        // removeParent() is a no-op when there is no parent (Node.java:286-292) - dfVisit() must not
        // throw just because the node it's asked to remove happens to be unattached.
        Node.Op orphan = ef.createNode(new TestArtifactData("orphan"));
        orphan.setUnique(false);

        NodeRemovalVisitor visitor = new NodeRemovalVisitor(mock(Configuration.class), mock(EvaluationStrategy.class));

        assertDoesNotThrow(() -> visitor.dfVisit(orphan));
    }
}
