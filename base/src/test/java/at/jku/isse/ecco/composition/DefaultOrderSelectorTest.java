package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultOrderSelector#select(Node)} is the sole producer of {@code getUncertainOrders()},
 * which {@code Checkout#getOrderWarnings()} surfaces to the GUI -- no prior test exercised its
 * actual ambiguity-detection logic against a real {@code PartialOrderGraph} branch point (see
 * {@code CheckoutComposerTest}, which only covers accessor/null/empty-tree behavior). This pins
 * down that a real branch point (a POG node with >1 next-node) gets flagged by recording the exact
 * {@code Node} instance passed to {@code select()} -- not the artifact, and not a copy -- since the
 * GUI's reorder dialog (see {@code CheckoutDetailView}) depends on getting back the real node it can
 * mutate.
 */
public class DefaultOrderSelectorTest {

	@Test
	public void select_withPogBranchPoint_flagsTheParentNodeAsUncertain() {
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));

		// head -> {X, Y} -> tail, with no ordering constraint between X and Y -- a genuine branch point.
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail()); // a fresh POG starts with a default head->tail edge
		SerPartialOrderGraphNode pogNodeX = new SerPartialOrderGraphNode(childX);
		SerPartialOrderGraphNode pogNodeY = new SerPartialOrderGraphNode(childY);
		pog.getHead().addChild(pogNodeX);
		pog.getHead().addChild(pogNodeY);
		pogNodeX.addChild(pog.getTail());
		pogNodeY.addChild(pog.getTail());

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		parentArtifact.setOrdered(true);
		parentArtifact.setPartialOrderGraph(pog);

		SerNode parentNode = new SerNode(parentArtifact);
		parentNode.addChild(new SerNode(childX));
		parentNode.addChild(new SerNode(childY));

		DefaultOrderSelector selector = new DefaultOrderSelector();
		List<Node> orderedChildren = selector.select(parentNode);

		assertEquals(2, orderedChildren.size());
		assertEquals(1, selector.getUncertainOrders().size());
		assertSame(parentNode, selector.getUncertainOrders().iterator().next());
	}

	@Test
	public void select_withLinearPog_doesNotFlagAnyNodeAsUncertain() {
		Artifact.Op<?> childA = new SerArtifact<>(new TestArtifactData("A"));
		Artifact.Op<?> childB = new SerArtifact<>(new TestArtifactData("B"));

		// head -> A -> B -> tail, a single fully-determined order.
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode pogNodeA = new SerPartialOrderGraphNode(childA);
		SerPartialOrderGraphNode pogNodeB = new SerPartialOrderGraphNode(childB);
		pog.getHead().addChild(pogNodeA);
		pogNodeA.addChild(pogNodeB);
		pogNodeB.addChild(pog.getTail());

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		parentArtifact.setOrdered(true);
		parentArtifact.setPartialOrderGraph(pog);

		SerNode parentNode = new SerNode(parentArtifact);
		parentNode.addChild(new SerNode(childA));
		parentNode.addChild(new SerNode(childB));

		DefaultOrderSelector selector = new DefaultOrderSelector();
		List<Node> orderedChildren = selector.select(parentNode);

		assertEquals(2, orderedChildren.size());
		assertTrue(selector.getUncertainOrders().isEmpty());
	}

}
