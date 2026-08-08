package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PartialOrderGraph#matchChildren(Node)} is the correlation step the ReorderChildrenDialog
 * POG-guidance feature needs: given a parent node's children, find each one's own node in the
 * PartialOrderGraph so {@link PartialOrderGraph.Op#canReach(PartialOrderGraph.Node, PartialOrderGraph.Node)}
 * can check whether the graph has already fixed a pair's relative order. Pins down both the
 * matching itself and the canReach() calls a caller would actually make with the result.
 */
public class PartialOrderGraphMatchChildrenTest {

	@Test
	public void matchChildren_correlatesEachChildToItsOwnPogNode_andCanReachReflectsFixedVsConcurrentPairs() {
		Artifact.Op<?> artifactA = new SerArtifact<>(new TestArtifactData("A"));
		Artifact.Op<?> artifactB = new SerArtifact<>(new TestArtifactData("B"));
		Artifact.Op<?> artifactC = new SerArtifact<>(new TestArtifactData("C"));

		// head -> {A -> B, C} -> tail: A/B is a fixed chain, C is a separate, concurrent branch -
		// neither A nor B has any fixed relation to C.
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail()); // a fresh POG starts with a default head->tail edge
		SerPartialOrderGraphNode pogNodeA = new SerPartialOrderGraphNode(artifactA);
		SerPartialOrderGraphNode pogNodeB = new SerPartialOrderGraphNode(artifactB);
		SerPartialOrderGraphNode pogNodeC = new SerPartialOrderGraphNode(artifactC);
		// canReach() identifies nodes by sequence number (see its own javadoc on why it's node-owned,
		// not artifact-owned), which every freshly-created node otherwise shares as
		// PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER - distinct values are needed for canReach() to
		// tell these three apart at all.
		pogNodeA.setSequenceNumber(1);
		pogNodeB.setSequenceNumber(2);
		pogNodeC.setSequenceNumber(3);
		pog.getHead().addChild(pogNodeA);
		pog.getHead().addChild(pogNodeC);
		pogNodeA.addChild(pogNodeB);
		pogNodeB.addChild(pog.getTail());
		pogNodeC.addChild(pog.getTail());

		SerNode parentNode = new SerNode(new SerArtifact<>(new TestArtifactData("Parent")));
		SerNode childNodeA = new SerNode(artifactA);
		SerNode childNodeB = new SerNode(artifactB);
		SerNode childNodeC = new SerNode(artifactC);
		parentNode.addChild(childNodeA);
		parentNode.addChild(childNodeB);
		parentNode.addChild(childNodeC);

		Map<Node, PartialOrderGraph.Node> matched = pog.matchChildren(parentNode);

		assertSame(pogNodeA, matched.get(childNodeA));
		assertSame(pogNodeB, matched.get(childNodeB));
		assertSame(pogNodeC, matched.get(childNodeC));

		assertTrue(PartialOrderGraph.Op.canReach(pogNodeA, pogNodeB), "A -> B is a fixed relation");
		assertFalse(PartialOrderGraph.Op.canReach(pogNodeA, pogNodeC), "A and C are concurrent");
		assertFalse(PartialOrderGraph.Op.canReach(pogNodeC, pogNodeA), "A and C are concurrent");
		assertFalse(PartialOrderGraph.Op.canReach(pogNodeB, pogNodeC), "B and C are concurrent");
		assertFalse(PartialOrderGraph.Op.canReach(pogNodeC, pogNodeB), "B and C are concurrent");
	}

}
