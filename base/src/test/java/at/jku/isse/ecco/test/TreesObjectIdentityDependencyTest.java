package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterizes whether main-tree building (Trees.merge(), used by Repository.merge() during
 * fork/pull/push - see Repository.java:986/1258, EccoService.java's various repository.merge()
 * call sites) requires true Java object identity between "the same" artifact/node as reused
 * across trees, or just equal data.
 *
 * Motivation: any storage backend that stores entities independently and reconstructs them on
 * load (e.g. a MapDB HTreeMap.get() per lookup) produces a NEW object instance every time it
 * resolves an ID, unless a per-transaction identity cache forces repeat lookups of the same ID to
 * return the same instance. Before building that cache is worth designing/costing, this pins down
 * whether skipping it would actually break something real, using the current Ser/in-memory
 * behavior as the baseline - not guessing from reading the source.
 */
public class TreesObjectIdentityDependencyTest {

	/**
	 * Control: the real, currently-correct usage - both trees reference the exact SAME artifact
	 * instance (as they always do today, since nothing round-trips through independent
	 * deserialization). merge() must succeed. This confirms the test's own setup is a valid
	 * merge() scenario before the negative case below is trusted.
	 */
	@Test
	public void merge_sameArtifactInstance_succeeds() {
		EntityFactory ef = new SerEntityFactory();

		Node.Op left = ef.createOrderedNode(new TestArtifactData("parent"));
		Node.Op right = ef.createOrderedNode(left.getArtifact());

		Artifact.Op<TestArtifactData> shared = ef.createArtifact(new TestArtifactData("shared-child"));
		left.addChild(ef.createNode(shared));
		right.addChild(ef.createNode(shared));

		Trees.merge(left, right);

		assertEquals(1, left.getChildren().size());
	}

	/**
	 * The actual question: two DISTINCT artifact instances holding equal data (equals()/hashCode()
	 * both true) - exactly what an ID-keyed store would produce if the same logical entity got
	 * independently deserialized twice (no identity cache) rather than resolved to one shared
	 * in-memory instance. merge() compares with "!=" (Trees.java:292), not .equals(), so this must
	 * throw given current behavior - if it doesn't, the identity-cache requirement below would be
	 * unnecessary and this test should fail to signal that the premise changed.
	 */
	@Test
	public void merge_distinctButEqualArtifactInstances_throws() {
		EntityFactory ef = new SerEntityFactory();

		Node.Op left = ef.createOrderedNode(new TestArtifactData("parent"));
		Node.Op right = ef.createOrderedNode(left.getArtifact());

		Artifact.Op<TestArtifactData> leftChildArtifact = ef.createArtifact(new TestArtifactData("shared-child"));
		Artifact.Op<TestArtifactData> rightChildArtifact = ef.createArtifact(new TestArtifactData("shared-child"));
		// same data, different instances - confirm the premise before relying on it
		assertEquals(leftChildArtifact.getData(), rightChildArtifact.getData());

		left.addChild(ef.createNode(leftChildArtifact));
		right.addChild(ef.createNode(rightChildArtifact));

		EccoException ex = assertThrows(EccoException.class, () -> Trees.merge(left, right));
		assertEquals("Artifact instance must be identical, i.e. trees must originate from the same repository.", ex.getMessage());
	}
}