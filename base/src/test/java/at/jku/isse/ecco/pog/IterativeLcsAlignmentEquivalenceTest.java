package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PartialOrderGraph.Op.iterativeLcsAlignment() aligned every pair of the two graphs' sequencings in
 * full and kept the first largest alignment - exponential in the open branches of a file (a tinycc
 * commit took over 10 minutes). It now computes the LCS length of every sequencing at once, sharing
 * their common work, and aligns only the first pair of largest length. The alignment decides which
 * lines of a commit are the same as lines stored before, so it must be EXACTLY the one of before:
 * the same keys, matched to the same nodes. The implementation of before is kept here as the oracle.
 * Graphs are built as commits build them, by merging variants of a file - few distinct lines, many
 * repeated ones ("}", ""), insertions at the same places in different variants (open branches).
 */
public class IterativeLcsAlignmentEquivalenceTest {

	private static final String[] COMMON = {"}", "", "{", "x++;", "return x;", "break;"};

	/** iterativeLcsAlignment as it was: every pair aligned, the first largest kept. */
	private static MutableIntObjectMap<Node.Op> reference(PartialOrderGraph.Op pog, PartialOrderGraph.Op other) {
		Node.Op[][] thisPaths = pog.collectNodeSequencings();
		Node.Op[][] otherPaths = other.collectNodeSequencings();
		Collection<MutableIntObjectMap<Node.Op>> alignmentResults = new LinkedList<>();
		for (Node.Op[] thisPath : thisPaths)
			for (Node.Op[] otherPath : otherPaths)
				alignmentResults.add(pog.alignPaths(thisPath, otherPath));
		return alignmentResults.stream().max(Comparator.comparingInt(MutableIntObjectMap::size)).orElseThrow();
	}

	private static List<Artifact.Op<?>> artifacts(List<String> lines) {
		List<Artifact.Op<?>> artifacts = new ArrayList<>();
		for (String line : lines)
			artifacts.add(new SerArtifact<>(new TestArtifactData(line)));
		return artifacts;
	}

	private static List<String> randomLines(Random random, int n, int unique) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < n; i++)
			lines.add(random.nextInt(3) == 0 ? "line " + random.nextInt(unique) : COMMON[random.nextInt(COMMON.length)]);
		return lines;
	}

	/** A variant: some lines inserted (often new ones, at the same places other variants insert), removed or moved. */
	private static List<String> variant(Random random, List<String> base, int number) {
		List<String> lines = new ArrayList<>(base);
		for (int edits = 1 + random.nextInt(3); edits > 0; edits--) {
			int at = lines.isEmpty() ? 0 : random.nextInt(lines.size() + 1);
			switch (random.nextInt(5)) {
				case 0, 1 -> lines.add(Math.min(at, lines.size()), "only in " + number + "." + edits);
				case 2 -> lines.add(Math.min(at, lines.size()), COMMON[random.nextInt(COMMON.length)]);
				case 3 -> { if (!lines.isEmpty() && at < lines.size()) lines.remove(at); }
				default -> {
					if (lines.size() > 1 && at < lines.size()) {
						String moved = lines.remove(at);
						lines.add(random.nextInt(lines.size() + 1), moved);
					}
				}
			}
		}
		return lines;
	}

	private static void assertSameAlignment(MutableIntObjectMap<Node.Op> expected, MutableIntObjectMap<Node.Op> actual, String where) {
		assertEquals(expected.keySet(), actual.keySet(), where + ": matched sequence numbers");
		expected.forEachKeyValue((key, node) -> assertSame(node, actual.get(key), where + ": node matched to " + key));
	}

	@Test
	@Timeout(600)
	public void alignsAsBeforeOnGraphsBuiltFromVariants() {
		int compared = 0, branched = 0, maxSequencings = 0;
		for (int round = 0; round < 400; round++) {
			Random random = new Random(round);
			List<String> base = randomLines(random, 3 + random.nextInt(40), 10 + random.nextInt(30));
			PartialOrderGraph.Op pog = new SerPartialOrderGraph();
			int variants = 2 + random.nextInt(9);
			for (int v = 0; v < variants; v++)
				pog.merge(artifacts(variant(random, base, v)));
			if (pog.estimateLinearizationCount(5_000) >= 5_000)
				continue; // the oracle aligns every sequencing in full

			for (int o = 0; o < 4; o++) {
				PartialOrderGraph.Op other;
				if (o < 3) {
					other = pog.fromList(artifacts(random.nextBoolean() ? variant(random, base, 100 + o) : randomLines(random, random.nextInt(30), 20)));
				} else {
					// an other graph with open branches of its own
					other = new SerPartialOrderGraph();
					other.merge(artifacts(variant(random, base, 200)));
					other.merge(artifacts(variant(random, base, 201)));
				}
				int sequencings = pog.collectNodeSequencings().length * other.collectNodeSequencings().length;
				maxSequencings = Math.max(maxSequencings, sequencings);
				if (sequencings > 1)
					branched++;
				assertSameAlignment(reference(pog, other), pog.iterativeLcsAlignment(other), "round " + round + " other " + o);
				compared++;
			}
		}
		assertTrue(branched > 500, "graphs with open branches: " + branched + " of " + compared);
		assertTrue(maxSequencings >= 64, "most sequencings compared: " + maxSequencings);
	}

	/** Several orders of one branch give the same LCS length - the first of them in the order of the sequencings wins, as before. */
	@Test
	public void ofEquallyLongAlignmentsTheFirstIsTaken() {
		PartialOrderGraph.Op pog = new SerPartialOrderGraph();
		pog.merge(artifacts(List.of("a", "x", "b")));
		pog.merge(artifacts(List.of("a", "y", "b")));
		pog.merge(artifacts(List.of("a", "z", "b")));
		for (List<String> other : List.of(List.of("a", "b"), List.of("a", "y", "x", "b"), List.of("a", "z", "y", "x", "b"), List.of("x", "y"))) {
			PartialOrderGraph.Op otherPog = pog.fromList(artifacts(other));
			assertSameAlignment(reference(pog, otherPog), pog.iterativeLcsAlignment(otherPog), other.toString());
		}
	}
}
