package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PartialOrderGraph.Op#collectNodeSequencings()} enumerates every valid topological
 * ordering of a partial order graph by generating all N! permutations at each node with N
 * concurrent (unresolved) children - see the {@code Permutation.generatePermutations} call in
 * {@code extendNodeSequencings}. It backs {@code align()}, which every {@code merge()} (i.e.
 * every commit) calls, so this cost isn't confined to some opt-in analysis feature - it runs on
 * the ordinary commit path. The only prior coverage (CollectPogPathsTest) stayed at 0-2 concurrent
 * branches, i.e. the cases where N! isn't yet distinguishable from N. These tests exercise 3-way
 * and 4-way concurrency to make the actual growth curve (6, then 24 orderings) visible and pin
 * it down as a regression check, without going anywhere near branch counts that would make the
 * test itself slow - deliberately small per the same reasoning as
 * at.jku.isse.ecco.service.CommitAssociationReproTest. Do not raise the branch counts here without
 * checking runtime first; this is a known unfixed risk area, not a place to casually stress-test.
 */
public class ConcurrentBranchSequencingTest {

	@Test
	@Timeout(10)
	public void collectNodeSequencings_withThreeConcurrentBranches_producesAllSixOrderings() {
		SerPartialOrderGraph pog = threeWayBranch();

		PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();

		assertEquals(6, sequencings.length);
		assertEquals(Set.of("A,B,C", "A,C,B", "B,A,C", "B,C,A", "C,A,B", "C,B,A"), toOrderingStrings(sequencings));
	}

	@Test
	@Timeout(10)
	public void collectNodeSequencings_withFourConcurrentBranches_producesAllTwentyFourOrderings() {
		SerPartialOrderGraph pog = fourWayBranch();

		PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();

		assertEquals(24, sequencings.length);
	}

	private Set<String> toOrderingStrings(PartialOrderGraph.Node.Op[][] sequencings) {
		Set<String> orderings = new HashSet<>();
		for (PartialOrderGraph.Node.Op[] sequencing : sequencings) {
			orderings.add(Arrays.stream(sequencing).map(node -> node.getArtifact().toString()).collect(Collectors.joining(",")));
		}
		return orderings;
	}

	/** head -> {A, B, C} -> tail, with no ordering constraint between A, B and C. */
	private SerPartialOrderGraph threeWayBranch() {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail()); // a fresh POG starts with a default head->tail edge
		for (SerPartialOrderGraphNode branch : nodesFor("A", "B", "C")) {
			pog.getHead().addChild(branch);
			branch.addChild(pog.getTail());
		}
		return pog;
	}

	/** head -> {A, B, C, D} -> tail, with no ordering constraint between any of the four. */
	private SerPartialOrderGraph fourWayBranch() {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail()); // a fresh POG starts with a default head->tail edge
		for (SerPartialOrderGraphNode branch : nodesFor("A", "B", "C", "D")) {
			pog.getHead().addChild(branch);
			branch.addChild(pog.getTail());
		}
		return pog;
	}

	private List<SerPartialOrderGraphNode> nodesFor(String... ids) {
		return Arrays.stream(ids).map(id -> new SerPartialOrderGraphNode(new SerArtifact<>(new TestArtifactData(id)))).collect(Collectors.toList());
	}
}
