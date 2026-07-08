package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares {@link PartialOrderGraph.Op#directPoaAlignment(PartialOrderGraph.Op)} (the spike) against
 * {@link PartialOrderGraph.Op#iterativeLcsAlignment(PartialOrderGraph.Op)} (current production
 * behavior, unmodified) - both on match count (the optimization objective both are supposed to
 * maximize) and on wall-clock cost as branch count grows.
 * <p>
 * Neither {@code merge()} nor {@code align()} is touched by this test - graphs are built directly
 * via {@link SerPartialOrderGraphNode} rather than repeated {@code merge()} calls, specifically so
 * that building the test fixtures themselves doesn't pay {@code iterativeLcsAlignment}'s blowup cost.
 * <p>
 * <b>directPoaAlignment is known-buggy</b> - see the {@code KNOWN_BUG_...} test below and
 * {@link PartialOrderGraph.Op#directPoaAlignment(PartialOrderGraph.Op)}'s javadoc for the mechanism.
 * It was briefly wired into {@code align()} on 2026-07-08 and immediately reverted after this
 * full-suite run caught {@code PartialOrderGraphTest} regressing - the "compares equal" tests above
 * this class's known-bug test are real (equal on the scenarios they cover) but not a proof of general
 * correctness, since the bug only manifests when a concurrent branch's needed order actively conflicts
 * with the arbitrary order {@link PartialOrderGraph.Op#collectNodes()} happens to produce.
 */
public class DirectPoaAlignmentSpikeTest {

	private Artifact.Op<?> A(String id) {
		return new SerArtifact<>(new TestArtifactData(id));
	}

	private PartialOrderGraph.Op linearPog(List<Artifact.Op<?>> artifacts) {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		SerPartialOrderGraphNode cur = (SerPartialOrderGraphNode) pog.getHead();
		pog.getHead().removeChild(pog.getTail());
		for (Artifact.Op<?> artifact : artifacts) {
			SerPartialOrderGraphNode next = new SerPartialOrderGraphNode(artifact);
			cur.addChild(next);
			cur = next;
		}
		cur.addChild((SerPartialOrderGraphNode) pog.getTail());
		return pog;
	}

	/**
	 * Builds a "this" pog that already has sequence numbers assigned (as if produced by prior
	 * merges), with the given branch structure: a chain of branchPoints, each fanning out into
	 * branchFactor parallel single-node alternatives that reconverge before the next branch point.
	 * iterativeLcsAlignment's collectNodeSequencings() would enumerate (branchFactor!)^branchPoints
	 * linearizations of this graph alone.
	 */
	private PartialOrderGraph.Op branchyPog(int branchPoints, int branchFactor) {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode cur = (SerPartialOrderGraphNode) pog.getHead();
		int seq = PartialOrderGraph.INITIAL_SEQUENCE_NUMBER;

		for (int bp = 0; bp < branchPoints; bp++) {
			SerPartialOrderGraphNode sync = new SerPartialOrderGraphNode(A("sync" + bp));
			sync.getArtifact().setSequenceNumber(seq++);
			for (int alt = 0; alt < branchFactor; alt++) {
				Artifact.Op<?> artifact = A("bp" + bp + "alt" + alt);
				artifact.setSequenceNumber(seq++);
				SerPartialOrderGraphNode altNode = new SerPartialOrderGraphNode(artifact);
				cur.addChild(altNode);
				altNode.addChild(sync);
			}
			cur = sync;
		}
		cur.addChild((SerPartialOrderGraphNode) pog.getTail());
		pog.setMaxIdentifier(seq);
		return pog;
	}

	@Test
	public void branchesInThisVsLinearOther_sameMatchCountAsExhaustive() {
		// "this" has one concurrent branch point (2 alternatives) already resolved with sequence
		// numbers, like ConcurrentBranchSequencingTest builds - mirrors mergingWithBranchesWorks()
		// in PartialOrderGraphTest but comparing both alignment implementations directly.
		PartialOrderGraph.Op thisPog = branchyPog(1, 3);

		// order matters: bp0alt1 -> sync0 is the only real edge in thisPog, so this is the only
		// order in which both can be matched at once (respecting order is the whole point of
		// alignment - swapping them would make only one of the two matchable, not both).
		PartialOrderGraph.Op otherLinear = linearPog(Arrays.asList(A("bp0alt1"), A("sync0")));

		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = thisPog.iterativeLcsAlignment(otherLinear);
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = thisPog.directPoaAlignment(otherLinear);

		assertEquals(oldResult.size(), newResult.size(), "match count (the actual optimization objective) should be identical");
		assertEquals(2, oldResult.size(), "both 'bp0alt1' and 'sync0' should match, in that order");
	}

	/**
	 * KNOWN BUG, reproduced directly: exact setup from PartialOrderGraphTest.mergingWithBranchesWorks(),
	 * calling both alignment implementations directly instead of going through merge()/align(). The
	 * fix for that test wiring directPoaAlignment() into align() (2026-07-08) broke it - this is why.
	 * <p>
	 * thisPog ends up with an unresolved concurrent branch {"2","3"} between "1" and "4" (from two
	 * prior merges - see below). Both are equally valid predecessors-wise (neither is an ancestor of
	 * the other), so a correct aligner must be able to match them in *either* relative order depending
	 * on what "other" needs. otherPog is linear "1,2,3,4,5", which needs "2" matched before "3".
	 * <p>
	 * iterativeLcsAlignment tries every relative ordering of the branch (that's its entire point) and
	 * finds all 5 nodes matchable. directPoaAlignment fixes ONE arbitrary topological order up front
	 * (whatever collectNodes()'s stack traversal happens to produce) and uses each node's fixed array
	 * position as an implicit "already decided" ordering constraint even between nodes that aren't
	 * actually ordered relative to each other in the graph - so if collectNodes() happens to place
	 * "3" before "2", matching "2" forces the DP's j-index backward relative to any future match of
	 * "3", making the two mutually exclusive. Only 4 of 5 nodes end up matchable, not 5 - a real
	 * correctness gap in the current design, not a tie-breaking difference.
	 */
	@Test
	public void KNOWN_BUG_concurrentBranchOrderConflictsWithOther_directPoaFindsFewerMatches() {
		List<Artifact.Op<?>> thisArtifacts1 = Arrays.asList(A("1"), A("3"), A("4"), A("5"));
		List<Artifact.Op<?>> thisArtifacts2 = Arrays.asList(A("1"), A("2"), A("4"), A("5"));
		PartialOrderGraph.Op thisPog = new SerPartialOrderGraph();
		thisPog.merge(thisArtifacts1);
		thisPog.merge(thisArtifacts2);

		List<Artifact.Op<?>> otherArtifacts = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"));
		PartialOrderGraph.Op otherPog = new SerPartialOrderGraph();
		otherPog.merge(otherArtifacts);

		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = thisPog.iterativeLcsAlignment(otherPog);
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = thisPog.directPoaAlignment(otherPog);

		assertEquals(5, oldResult.size(), "old (exhaustive) finds all 5 - this is the correct answer");
		assertEquals(4, newResult.size(), "new (direct DP) only finds 4 - documents the current bug, not a spec");
	}

	@Test
	public void complexMerge_pogVsPog_sameMatchCountAsExhaustive() {
		// adapted from PartialOrderGraphTest's (commented-out) ComplexMergeTest: two independently
		// branchy pogs aligned against each other (the general DAG-vs-DAG case Trees.java actually
		// exercises via pog.merge(otherPog), not just the "one linear commit" case).
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new SerPartialOrderGraph();
		pog1.merge(artifacts1);
		pog1.merge(artifacts3);

		PartialOrderGraph.Op pog2 = new SerPartialOrderGraph();
		pog2.merge(artifacts2);
		pog2.merge(artifacts4);

		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = pog1.iterativeLcsAlignment(pog2);
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = pog1.directPoaAlignment(pog2);

		assertEquals(oldResult.size(), newResult.size(), "match count should be identical for pog-vs-pog alignment too");
	}

	@Test
	public void noOverlap_bothFindZeroMatches() {
		PartialOrderGraph.Op thisPog = branchyPog(2, 3);
		PartialOrderGraph.Op otherLinear = linearPog(Arrays.asList(A("totally"), A("unrelated")));

		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = thisPog.iterativeLcsAlignment(otherLinear);
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = thisPog.directPoaAlignment(otherLinear);

		assertEquals(0, oldResult.size());
		assertEquals(0, newResult.size());
	}

	@Test
	public void emptyOther_bothFindZeroMatches() {
		PartialOrderGraph.Op thisPog = branchyPog(1, 2);
		PartialOrderGraph.Op otherEmpty = linearPog(new ArrayList<>());

		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = thisPog.iterativeLcsAlignment(otherEmpty);
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = thisPog.directPoaAlignment(otherEmpty);

		assertEquals(0, oldResult.size());
		assertEquals(0, newResult.size());
	}

	/**
	 * The actual point of the spike: as concurrent branches grow, iterativeLcsAlignment's cost
	 * explodes ((branchFactor!)^branchPoints linearizations of "this" alone) while directPoaAlignment
	 * stays fast (roughly O(V_this * V_other), no enumeration) - and both still agree on the match
	 * count. Parameters were picked empirically (see a throwaway timing probe run separately, not
	 * checked in): branchPoints=3/branchFactor=4 -> old ~50ms; one step further,
	 * branchPoints=3/branchFactor=5 ((5!)^3 = 1,728,000 linearizations) -> old throws
	 * OutOfMemoryError while *enumerating* linearizations in extendNodeSequencings(), before even
	 * reaching alignment - not asserted here since OOM behavior is heap-size-dependent and not
	 * suitable for a real test, but a striking data point: this isn't just "slow", it can crash.
	 */
	@Test
	@Timeout(30)
	public void manyBranches_newStaysFastAndAgreesWithOld_oldConfirmedSlow() {
		int branchPoints = 3;
		int branchFactor = 4;
		PartialOrderGraph.Op thisPog = branchyPog(branchPoints, branchFactor);

		// align a linear "other" that matches every sync point plus one alternative per branch,
		// so there's a real, non-trivial best alignment to find (not just 0 or "everything").
		List<Artifact.Op<?>> otherArtifacts = new ArrayList<>();
		for (int bp = 0; bp < branchPoints; bp++) {
			otherArtifacts.add(A("bp" + bp + "alt0"));
			otherArtifacts.add(A("sync" + bp));
		}
		PartialOrderGraph.Op otherLinear = linearPog(otherArtifacts);

		long newStart = System.nanoTime();
		MutableIntObjectMap<PartialOrderGraph.Node.Op> newResult = thisPog.directPoaAlignment(otherLinear);
		long newMs = (System.nanoTime() - newStart) / 1_000_000;

		long oldStart = System.nanoTime();
		MutableIntObjectMap<PartialOrderGraph.Node.Op> oldResult = thisPog.iterativeLcsAlignment(otherLinear);
		long oldMs = (System.nanoTime() - oldStart) / 1_000_000;

		System.out.println("branchPoints=" + branchPoints + " branchFactor=" + branchFactor
				+ " -> old: " + oldMs + "ms (" + oldResult.size() + " matches), new: " + newMs + "ms (" + newResult.size() + " matches)");

		assertEquals(oldResult.size(), newResult.size(), "match count should be identical");
		assertEquals(2 * branchPoints, newResult.size(), "should match both the sync node and one alternative per branch point");
		assertTrue(newMs < 1000, "direct DP should stay well under a second even with (4!)^3 = 13,824 linearizations for the old algorithm");
	}

	/**
	 * Option 1 from the follow-up discussion: rather than replacing iterativeLcsAlignment outright
	 * (which is the correct algorithm, just slow/crashy on pathological graphs), estimate the
	 * linearization count cheaply via {@link PartialOrderGraph.Op#estimateLinearizationCount} and
	 * fall back to {@link PartialOrderGraph.Op#directPoaAlignment} only once it's over
	 * {@code LINEARIZATION_CAP} - accepting directPoaAlignment's possible under-matching (not
	 * correctness-breaking, see its javadoc) only in the cases where the exact algorithm would
	 * otherwise OOM/hang. This goes through the real production entry point, {@code align()}.
	 * <p>
	 * branchPoints=3/branchFactor=5 ((5!)^3 = 1,728,000 linearizations) is the exact configuration
	 * that OOMs when iterativeLcsAlignment is called directly (see the test above) - this confirms
	 * align() no longer does that.
	 */
	@Test
	@Timeout(30)
	public void fallbackViaAlign_pathologicalGraphNoLongerCrashes() {
		int branchPoints = 3;
		int branchFactor = 5;
		PartialOrderGraph.Op thisPog = branchyPog(branchPoints, branchFactor);

		List<Artifact.Op<?>> otherArtifacts = new ArrayList<>();
		for (int bp = 0; bp < branchPoints; bp++) {
			otherArtifacts.add(A("bp" + bp + "alt0"));
			otherArtifacts.add(A("sync" + bp));
		}

		long start = System.nanoTime();
		thisPog.align(otherArtifacts);
		long ms = (System.nanoTime() - start) / 1_000_000;

		long matched = otherArtifacts.stream().filter(a -> a.getSequenceNumber() != PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER).count();
		System.out.println("fallback align(): " + ms + "ms, " + matched + "/" + otherArtifacts.size() + " matched");

		assertTrue(ms < 5000, "should stay fast by falling back instead of enumerating 1.7M linearizations");
		assertEquals(2 * branchPoints, matched, "directPoaAlignment happens to find the full optimum on this particular graph shape too");
	}
}
