package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LazyCompositionNode.activate()'s childless-node cleanup (LazyCompositionNode.java, in the
 * unwantedNodes branch) had its isUnique() check inverted: it removed unique childless children
 * instead of non-unique ones, contradicting its own comment ("remove non-unique children without
 * children") and the identical {@code !isUnique() && getChildren().isEmpty()} idiom used
 * throughout Trees.java. The buggy branch only runs when unwantedNodes is non-empty, and nothing
 * outside this class ever calls addUnwantedNode(s) in production, so this exercises it directly via
 * the public addUnwantedNode() API instead of through any real caller.
 */
public class LazyCompositionNodeTest {

	private final EntityFactory ef = new SerEntityFactory();

	@Test
	public void unwantedNodesCleanupKeepsUniqueChildlessNodesAndRemovesNonUniqueOnes() {
		Node.Op origRoot = ef.createRootNode();

		Node.Op uniqueLeaf = ef.createNode(new TestArtifactData("uniqueLeaf"));
		origRoot.addChild(uniqueLeaf);
		uniqueLeaf.setParent(origRoot);

		Node.Op nonUniqueLeaf = ef.createNode(new TestArtifactData("nonUniqueLeaf"));
		nonUniqueLeaf.setUnique(false);
		origRoot.addChild(nonUniqueLeaf);
		nonUniqueLeaf.setParent(origRoot);

		// An unrelated, childless, un-added node just to make unwantedNodes non-empty (the guard at
		// LazyCompositionNode.java:108) without matching (by artifact identity, via
		// LazyCompositionNode.equals()) either real child -- otherwise activate()'s recursive
		// addUnwantedNodes()/activate() calls on the real children would themselves flip their
		// uniqueness and contaminate the very thing being tested.
		Node.Op dummyUnwanted = ef.createNode(new TestArtifactData("dummyUnwanted"));

		LazyCompositionNode composed = new LazyCompositionNode(new DefaultOrderSelector());
		composed.addOrigNode(origRoot);
		composed.addUnwantedNode(dummyUnwanted);

		List<? extends Node> children = composed.getChildren();

		assertTrue(children.stream().anyMatch(n -> "uniqueLeaf".equals(n.getArtifact().getData().toString())),
				"a unique childless leaf must not be stripped by the childless-node cleanup");
		assertFalse(children.stream().anyMatch(n -> "nonUniqueLeaf".equals(n.getArtifact().getData().toString())),
				"a non-unique childless stub is exactly what the cleanup is meant to remove");
	}

	/**
	 * activate() used to find each child's composed counterpart with a linear list scan, quadratic in
	 * the number of children - an image (one child per pixel) never finished composing, which hung
	 * the artifact graph and the commit details. The children must still be merged exactly as before:
	 * equal children of different original nodes become one composed child, in first-seen order.
	 */
	@Test
	@Timeout(value = 20, unit = TimeUnit.SECONDS)
	public void manyChildrenAreMergedInFirstSeenOrder() {
		int n = 60_000;
		Node.Op first = ef.createRootNode();
		Node.Op second = ef.createRootNode();
		for (int i = 0; i < n; i++) {
			Node.Op child = ef.createNode(new TestArtifactData("c" + i));
			child.setUnique(false);
			Node.Op grandChild = ef.createNode(new TestArtifactData("first" + i));
			child.addChild(grandChild);
			first.addChild(child);
		}
		// overlaps the second half of the first node's children, then adds as many new ones
		for (int i = n / 2; i < n + n / 2; i++) {
			Node.Op child = ef.createNode(new TestArtifactData("c" + i));
			child.addChild(ef.createNode(new TestArtifactData("second" + i)));
			second.addChild(child);
		}

		LazyCompositionNode composed = new LazyCompositionNode(new DefaultOrderSelector());
		composed.addOrigNode(first);
		composed.addOrigNode(second);
		List<? extends Node> children = composed.getChildren();

		assertEquals(n + n / 2, children.size());
		for (int i = 0; i < children.size(); i++) {
			assertEquals("c" + i, children.get(i).getArtifact().getData().toString());
		}
		assertFalse(children.get(0).isUnique(), "only in the first node, which is not unique there");
		assertTrue(children.get(n / 2).isUnique(), "unique in the second node");
		assertEquals(2, children.get(n / 2).getChildren().size(), "both originals' children are merged");
		assertEquals(1, children.get(n + n / 2 - 1).getChildren().size());
	}
}
