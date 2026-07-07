package at.jku.isse.ecco.test;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for Trees.checkConsistency(), pinning down current behavior before
 * removing a redundant O(n) check from its recursive path: parentHasNodeAsChild(node) does
 * node.getParent().getChildren().contains(node), an O(n) scan, called once per node in the whole
 * tree - so O(n^2) for a wide node (e.g. the image adapter's one-child-per-pixel node). It's
 * provably redundant for every node reached via the recursive descent, since the loop that
 * recurses into a child already obtained that child by iterating node.getChildren() (so
 * node.getChildren().contains(child) is trivially true) and separately already checks
 * child.getParent() != node. It's only ever non-trivial for the top-level call, and even there
 * only when called on a genuinely non-root node - checkConsistency in this codebase is always
 * called on an association's root node, whose getParent() is null, making the check a no-op in
 * every real call path today. Still, the fix preserves the check at the top-level entry point.
 */
public class TreesCheckConsistencyTest {

	private static EntityFactory ef() {
		return new SerEntityFactory();
	}

	@Test
	public void checkConsistency_wellFormedTree_doesNotThrow() {
		EntityFactory ef = ef();
		RootNode.Op root = ef.createRootNode();
		Node.Op a = ef.createNode(new TestArtifactData("A"));
		Node.Op b = ef.createNode(new TestArtifactData("B"));
		root.addChild(a);
		a.addChild(b);

		assertDoesNotThrow(() -> Trees.checkConsistency(root));
	}

	/**
	 * A child whose parent pointer doesn't match its actual parent must still be caught - via the
	 * loop's own "child.getParent() != node" check, not the redundant parentHasNodeAsChild() one.
	 */
	@Test
	public void checkConsistency_childWithWrongParentPointer_stillThrows() {
		EntityFactory ef = ef();
		RootNode.Op root = ef.createRootNode();
		Node.Op a = ef.createNode(new TestArtifactData("A"));
		Node.Op b = ef.createNode(new TestArtifactData("B"));
		root.addChild(a);
		a.addChild(b);

		b.setParent(root); // corrupt: b is a's child in the list, but now points to root as parent

		assertThrows(IllegalStateException.class, () -> Trees.checkConsistency(root));
	}

	/**
	 * Regression guard against reintroducing O(n^2): a wide node (many children under one parent,
	 * as the image adapter produces) must check quickly.
	 */
	@Test
	@Timeout(20)
	public void checkConsistency_manySiblings_completesWithoutQuadraticBlowup() {
		EntityFactory ef = ef();
		RootNode.Op root = ef.createRootNode();
		Node.Op parent = ef.createNode(new TestArtifactData("parent"));
		root.addChild(parent);

		int n = 20000;
		for (int i = 0; i < n; i++) {
			parent.addChild(ef.createNode(new TestArtifactData("n" + i)));
		}

		long start = System.nanoTime();
		Trees.checkConsistency(root);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		System.out.println("checkConsistency() over " + n + " siblings took " + elapsedMs + "ms");
	}
}
