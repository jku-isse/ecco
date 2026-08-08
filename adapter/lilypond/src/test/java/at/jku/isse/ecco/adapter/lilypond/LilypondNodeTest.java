package at.jku.isse.ecco.adapter.lilypond;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers {@link LilypondNode}'s linked-list operations directly - the simplest, most foundational
 * building block every reader/transformer/writer in this adapter is built on top of. */
public class LilypondNodeTest {

	private static String walk(LilypondNode<String> from) {
		StringBuilder sb = new StringBuilder();
		LilypondNode<String> n = from;
		while (n != null) {
			sb.append(n.getName());
			n = n.getNext();
		}
		return sb.toString();
	}

	@Test
	void appendChainsNodesAndSetsLevel() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		LilypondNode<String> b = a.append("b", "b", 3);

		assertEquals(b, a.getNext());
		assertEquals(a, b.getPrev());
		assertEquals(3, b.getLevel());
	}

	@Test
	void removeSplicesOutAMiddleNodeWithoutLosingTheRest() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		LilypondNode<String> b = a.append("b", "b", 0);
		LilypondNode<String> c = b.append("c", "c", 0);
		LilypondNode<String> d = c.append("d", "d", 0);

		b.remove();

		assertEquals(c, a.getNext());
		assertEquals(a, c.getPrev());
		assertEquals(d, c.getNext());
		assertEquals(c, d.getPrev());
		assertEquals("acd", walk(a));
	}

	@Test
	void removeOnTheLastNodeLeavesAValidShorterChain() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		LilypondNode<String> b = a.append("b", "b", 0);

		b.remove();

		assertNull(a.getNext());
		assertEquals("a", walk(a));
	}

	@Test
	void cutOnTheHeadNodeDetachesItAndLeavesTheRestAsAValidChain() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		LilypondNode<String> b = a.append("b", "b", 0);
		LilypondNode<String> c = b.append("c", "c", 0);

		a.cut();

		assertNull(b.getPrev());
		assertEquals("bc", walk(b));
		// the cut node itself is fully detached, not just unreachable from its old neighbors
		assertNull(a.getNext());
		assertNull(a.getPrev());
	}

	@Test
	void insertAfterPlacesANodeBetweenTwoExistingOnes() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		LilypondNode<String> c = a.append("c", "c", 0);
		LilypondNode<String> b = new LilypondNode<>("b", "b");

		b.insertAfter(a);

		assertEquals("abc", walk(a));
		assertEquals(a, b.getPrev());
		assertEquals(c, b.getNext());
		assertEquals(b, c.getPrev());
	}

	@Test
	void changeLevelByAdjustsLevelRelatively() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		a.setLevel(2);

		a.changeLevelBy(3);
		assertEquals(5, a.getLevel());

		a.changeLevelBy(-1);
		assertEquals(4, a.getLevel());
	}

	@Test
	void aFreshNodeHasNoNeighbors() {
		LilypondNode<String> a = new LilypondNode<>("a", "a");
		assertNull(a.getNext());
		assertNull(a.getPrev());
		assertFalse(walk(a).isEmpty());
		assertTrue(walk(a).equals("a"));
	}
}
