package at.jku.isse.ecco.util;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trees.slice()'s atomic-child branch (Trees.java, in the child-matching loop) used to set the
 * matched left child's parent pointer to the new intersection node unconditionally whenever the
 * child was atomic, but only actually attached it to intersection.getChildren() when the same
 * "keep" criterion used for non-atomic children (isUnique() || has non-atomic children) held. For a
 * non-unique atomic child, that meant: setParent(intersection) ran, but the child was never added to
 * intersection's children, and it was also unconditionally removed from both source trees (left and
 * right) - a dangling parent pointer, with the artifact effectively vanishing from all three trees.
 * A non-unique atomic artifact is a real case (a shared/skeleton node whose content type happens to
 * be atomic), reachable from slice()'s real callers, not just a contrived input.
 */
public class TreesSliceAtomicTest {

	private final EntityFactory ef = new SerEntityFactory();

	@Test
	public void nonUniqueAtomicChildIsPrunedCleanlyInsteadOfOrphaned() {
		Node.Op leftRoot = ef.createRootNode();
		Node.Op rightRoot = ef.createRootNode();

		Artifact.Op<?> leftArtifact = ef.createArtifact(new TestArtifactData("X"));
		leftArtifact.setAtomic(true);
		Artifact.Op<?> rightArtifact = ef.createArtifact(new TestArtifactData("X"));
		rightArtifact.setAtomic(true);

		Node.Op leftChild = ef.createNode(leftArtifact);
		leftChild.setUnique(false); // shared/skeleton, not exclusive to this slice
		Node.Op rightChild = ef.createNode(rightArtifact);
		rightChild.setUnique(false);

		leftRoot.addChild(leftChild);
		rightRoot.addChild(rightChild);

		Node.Op intersection = Trees.slice(leftRoot, rightRoot);

		assertFalse(intersection.getChildren().contains(leftChild),
				"a non-unique atomic child that isn't kept must not be attached to the intersection");
		assertNull(leftChild.getParent(),
				"a discarded child must not keep pointing at a parent it was never actually added to");
		assertFalse(leftRoot.getChildren().contains(leftChild), "matched content must still be removed from the left tree");
		assertFalse(rightRoot.getChildren().contains(rightChild), "matched content must still be removed from the right tree");
	}

	@Test
	public void uniqueAtomicChildIsStillAttachedToTheIntersection() {
		Node.Op leftRoot = ef.createRootNode();
		Node.Op rightRoot = ef.createRootNode();

		Artifact.Op<?> leftArtifact = ef.createArtifact(new TestArtifactData("X"));
		leftArtifact.setAtomic(true);
		Artifact.Op<?> rightArtifact = ef.createArtifact(new TestArtifactData("X"));
		rightArtifact.setAtomic(true);

		Node.Op leftChild = ef.createNode(leftArtifact);
		leftChild.setUnique(true);
		Node.Op rightChild = ef.createNode(rightArtifact);
		rightChild.setUnique(true);

		leftRoot.addChild(leftChild);
		rightRoot.addChild(rightChild);

		Node.Op intersection = Trees.slice(leftRoot, rightRoot);

		assertTrue(intersection.getChildren().contains(leftChild),
				"a unique atomic child must still become the intersection's child, as before");
		assertSame(intersection, leftChild.getParent());
	}
}
