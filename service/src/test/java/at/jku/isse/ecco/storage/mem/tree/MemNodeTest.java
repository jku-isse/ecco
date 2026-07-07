package at.jku.isse.ecco.storage.mem.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for MemNode's duplicate-child detection (addChild/addChildren on
 * non-ordered vs ordered nodes), pinning down current behavior before optimizing
 * addChildWithoutNumberUpdate()/addChildren()'s O(n) per-call "already contained?" scan (a
 * List.contains() linear search run on every single addChild(), making building a node with n
 * children one at a time O(n^2)).
 */
public class MemNodeTest {

	static class TestData implements ArtifactData {
		private final String id;

		TestData(String id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof TestData && ((TestData) o).id.equals(id);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(id);
		}

		@Override
		public String toString() {
			return id;
		}
	}

	@Test
	public void addChild_nonOrdered_throwsOnDuplicate() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createNode(new TestData("parent"));

		parent.addChild(ef.createNode(new TestData("A")));

		assertThrows(EccoException.class, () -> parent.addChild(ef.createNode(new TestData("A"))));
		assertEquals(1, parent.getChildren().size());
	}

	@Test
	public void addChild_ordered_allowsDuplicate() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createOrderedNode(new TestData("parent"));

		parent.addChild(ef.createNode(new TestData("A")));
		parent.addChild(ef.createNode(new TestData("A")));

		assertEquals(2, parent.getChildren().size());
	}

	@Test
	public void addChildren_nonOrdered_throwsOnDuplicateWithinBatch() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createNode(new TestData("parent"));

		assertThrows(EccoException.class, () -> parent.addChildren(
				ef.createNode(new TestData("A")),
				ef.createNode(new TestData("B")),
				ef.createNode(new TestData("A"))
		));
	}

	@Test
	public void addChildren_nonOrdered_throwsOnDuplicateAgainstExistingChild() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createNode(new TestData("parent"));
		parent.addChild(ef.createNode(new TestData("A")));

		assertThrows(EccoException.class, () -> parent.addChildren(
				ef.createNode(new TestData("B")),
				ef.createNode(new TestData("A"))
		));
	}

	@Test
	public void addChildren_nonOrdered_addsAllWhenNoDuplicates() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createNode(new TestData("parent"));
		Node.Op a = ef.createNode(new TestData("A"));
		Node.Op b = ef.createNode(new TestData("B"));
		Node.Op c = ef.createNode(new TestData("C"));

		parent.addChildren(a, b, c);

		assertEquals(List.of(a, b, c), parent.getChildren());
		assertEquals(3, parent.getNumberOfChildren());
		assertSame(parent, a.getParent());
		assertSame(parent, b.getParent());
		assertSame(parent, c.getParent());
	}

	@Test
	public void addChildren_ordered_allowsDuplicates() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createOrderedNode(new TestData("parent"));

		parent.addChildren(
				ef.createNode(new TestData("A")),
				ef.createNode(new TestData("A"))
		);

		assertEquals(2, parent.getChildren().size());
	}

	/**
	 * Regression guard against reintroducing O(n^2) behavior: passes quickly today, and must
	 * still pass well within the timeout (and much faster in practice) after optimizing the
	 * duplicate check to be O(n) for a batch instead of O(n) per call.
	 */
	@Test
	@Timeout(20)
	public void addChildren_manyChildren_completesWithoutQuadraticBlowup() {
		MemEntityFactory ef = new MemEntityFactory();
		Node.Op parent = ef.createNode(new TestData("parent"));
		int n = 20000;

		List<Node.Op> children = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			children.add(ef.createNode(new TestData("n" + i)));
		}

		long start = System.nanoTime();
		parent.addChildren(children.toArray(new Node.Op[0]));
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		System.out.println("addChildren() of " + n + " children took " + elapsedMs + "ms");

		assertEquals(n, parent.getChildren().size());
	}
}
