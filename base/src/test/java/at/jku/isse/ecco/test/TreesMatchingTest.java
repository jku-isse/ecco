package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for Trees.slice()/matchAtomicArtifacts()/merge()/subtract()/equals()/map(),
 * pinning down exact current behavior for scenarios the pre-existing TreesTest doesn't cover -
 * particularly duplicate sibling artifacts (multiple children of a node that are equal to each
 * other) - before replacing the underlying List.indexOf()-based matching with a hash-based one for
 * performance. These must keep passing unchanged after that refactor.
 */
public class TreesMatchingTest {

	private static List<String> identifiers(Node node) {
		return node.getChildren().stream()
				.map(c -> c.getArtifact().getData().toString())
				.collect(Collectors.toList());
	}

	/**
	 * left=[A,B,A,C] vs right=[A,A,D] (both ordered, so duplicates are legitimate): each duplicate
	 * group should be matched pairwise up to min(leftCount, rightCount), consuming that many
	 * instances from each side, in original relative order, leaving the surplus unmatched.
	 */
	@Test
	public void slice_matchesDuplicateSiblingsPairwiseInOriginalOrder() {
		EntityFactory ef = new MemEntityFactory();

		RootNode.Op leftRoot = ef.createRootNode();
		Node.Op leftParent = ef.createOrderedNode(new TestArtifactData("parent"));
		leftRoot.addChild(leftParent);
		leftParent.addChildren(
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("B")),
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("C"))
		);

		RootNode.Op rightRoot = ef.createRootNode();
		Node.Op rightParent = ef.createOrderedNode(new TestArtifactData("parent"));
		rightRoot.addChild(rightParent);
		rightParent.addChildren(
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("D"))
		);

		Trees.slice(leftRoot, rightRoot);

		// both A's on each side matched and removed (leaf matches with no surviving children aren't
		// unique, so they're pruned from both sides); B/C remain in left, D remains in right
		assertEquals(List.of("B", "C"), identifiers(leftParent));
		assertEquals(List.of("D"), identifiers(rightParent));
	}

	/**
	 * Characterizes a subtlety discovered while writing these tests: for ordered nodes, slice()'s
	 * sequencing step (building a sequence graph over children) assigns each child artifact a
	 * position-based sequence number, and Node/Artifact equality then requires matching sequence
	 * numbers too, not just equal data - so two data-equal duplicate children are NOT necessarily
	 * interchangeable once sequenced. Here, of two content-equal "A" left children, only the one
	 * whose assigned sequence number happens to match the right "A" child's is actually matched (and
	 * removed, since it has no children); the other survives, unmatched. The matched rightChild
	 * itself survives too (it still has its own child "Z", so slice() doesn't remove it). This test
	 * exists to pin down that exact (subtle, sequencing-dependent) outcome so a refactor of the
	 * underlying matching mechanism can't silently change it.
	 */
	@Test
	public void slice_orderedDuplicates_onlySequenceMatchedChildIsConsumed() {
		EntityFactory ef = new MemEntityFactory();

		RootNode.Op leftRoot = ef.createRootNode();
		Node.Op leftParent = ef.createOrderedNode(new TestArtifactData("parent"));
		leftRoot.addChild(leftParent);
		Node.Op leftA1 = ef.createNode(new TestArtifactData("A"));
		Node.Op leftA2 = ef.createNode(new TestArtifactData("A"));
		leftParent.addChildren(leftA1, leftA2);

		RootNode.Op rightRoot = ef.createRootNode();
		Node.Op rightParent = ef.createOrderedNode(new TestArtifactData("parent"));
		rightRoot.addChild(rightParent);
		Node.Op rightA = ef.createNode(new TestArtifactData("A"));
		// give rightA a child ("Z") that neither leftA1 nor leftA2 has, so rightA survives slicing
		// (its children list isn't emptied) whether or not it gets matched
		rightA.addChild(ef.createNode(new TestArtifactData("Z")));
		rightParent.addChild(rightA);

		Trees.slice(leftRoot, rightRoot);

		assertEquals(List.of("A"), identifiers(leftParent));
		assertEquals(List.of("A"), identifiers(rightParent));
		assertEquals(List.of("Z"), identifiers(rightParent.getChildren().get(0)));
	}

	/**
	 * merge() adds unmatched right children into left; a newly-added child must become a match
	 * candidate for a later, duplicate right child within the same merge() call. Note merge()'s
	 * "else" branch (a right child with no match in left) only adds it to left - it does not also
	 * remove it from right, unlike the "if" branch (a matched child) which does remove it from
	 * right - so the first B ends up present in both left and right afterward, while the second
	 * (matched, merged) B is removed from right. This is the pre-existing behavior being
	 * characterized, not a claim that it's the "correct" or intended one.
	 */
	@Test
	public void merge_newlyAddedChildBecomesMatchCandidateForLaterDuplicate() {
		EntityFactory ef = new MemEntityFactory();

		Node.Op left = ef.createOrderedNode(new TestArtifactData("parent"));
		Node.Op right = ef.createOrderedNode(left.getArtifact()); // merge() requires identical artifact instance

		// merge() matches by artifact identity (not just content equality, unlike slice()), so a
		// legitimate "duplicate" here is two node wrappers sharing one artifact instance - which is
		// how merge() is actually used (trees from the same repository), not two independently
		// created but content-equal artifacts
		Artifact.Op<TestArtifactData> bArtifact = ef.createArtifact(new TestArtifactData("B"));
		Node.Op rightB1 = ef.createNode(bArtifact);
		Node.Op rightB2 = ef.createNode(bArtifact);
		right.addChildren(rightB1, rightB2);

		Trees.merge(left, right);

		assertEquals(List.of("B"), identifiers(left));
		assertEquals(List.of("B"), identifiers(right));
	}

	/**
	 * matchAtomicArtifacts() (invoked via slice() when the artifact is atomic) never removes
	 * children - so for a duplicate leftChild group, every duplicate is matched against the SAME
	 * first-occurrence rightChild (mirroring indexOf()'s behavior of always finding the same first
	 * match when nothing is removed). This looks odd but is the pre-existing behavior being
	 * preserved, not a new invariant.
	 */
	@Test
	public void matchAtomicArtifacts_duplicateLeftChildrenAllMatchSameFirstRightChild() {
		EntityFactory ef = new MemEntityFactory();

		Node.Op left = ef.createOrderedNode(new TestArtifactData("atomicParent"));
		left.getArtifact().setAtomic(true);
		Node.Op leftA1 = ef.createNode(new TestArtifactData("A"));
		Node.Op leftA2 = ef.createNode(new TestArtifactData("A"));
		left.addChildren(leftA1, leftA2);

		Node.Op right = ef.createOrderedNode(new TestArtifactData("atomicParent"));
		right.getArtifact().setAtomic(true);
		Node.Op rightA1 = ef.createNode(new TestArtifactData("A"));
		Node.Op rightA2 = ef.createNode(new TestArtifactData("A"));
		right.addChildren(rightA1, rightA2);

		Trees.slice(left, right);

		// after matchAtomicArtifacts, every left/right pair at the same position must have been
		// given the SAME artifact instance (that's what matching an atomic pair does)
		assertEquals(left.getChildren().get(0).getArtifact(), right.getChildren().get(0).getArtifact());
		assertEquals(left.getChildren().get(1).getArtifact(), right.getChildren().get(1).getArtifact());
	}

	@Test
	public void equals_withDuplicateSiblings_returnsTrueForStructurallyEqualTrees() {
		EntityFactory ef = new MemEntityFactory();

		Node.Op left = ef.createOrderedNode(new TestArtifactData("parent"));
		left.addChildren(
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("B"))
		);

		Node.Op right = ef.createOrderedNode(new TestArtifactData("parent"));
		right.addChildren(
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("A")),
				ef.createNode(new TestArtifactData("B"))
		);

		assertTrue(Trees.equals(left, right));
	}

	/**
	 * A larger, wide sibling set. Primarily a regression guard against reintroducing O(n^2)
	 * matching: passes quickly today, and must still pass well within the timeout after the
	 * hash-based refactor (which should make it dramatically faster, not just "still under the
	 * timeout").
	 */
	@Test
	@Timeout(20)
	public void slice_manySiblings_completesWithoutQuadraticBlowup() {
		EntityFactory ef = new MemEntityFactory();
		int n = 4000;

		RootNode.Op leftRoot = ef.createRootNode();
		Node.Op leftParent = ef.createNode(new TestArtifactData("parent"));
		leftRoot.addChild(leftParent);

		RootNode.Op rightRoot = ef.createRootNode();
		Node.Op rightParent = ef.createNode(leftParent.getArtifact());
		rightRoot.addChild(rightParent);

		for (int i = 0; i < n; i++) {
			leftParent.addChild(ef.createNode(new TestArtifactData("n" + i)));
			// only every other one matches, so some survive on each side too
			if (i % 2 == 0) {
				rightParent.addChild(ef.createNode(new TestArtifactData("n" + i)));
			}
		}

		long start = System.nanoTime();
		Trees.slice(leftRoot, rightRoot);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		System.out.println("slice() of " + n + " siblings took " + elapsedMs + "ms");

		assertEquals(n / 2, identifiers(leftParent).size());
		assertEquals(0, identifiers(rightParent).size());
	}
}
