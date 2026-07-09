package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Characterizes whether PartialOrderGraph.merge() depends on every node's artifact being a
 * distinct object instance (even across otherwise-unrelated merges), as opposed to just being
 * data-equal. Motivated by the MapDB prototype's node/artifact ID-indirection work: making POG node
 * artifacts resolve to a single canonical, GLOBALLY-SHARED instance per logical artifact (instead of
 * accidentally-always-distinct duplicate objects, which is what every existing PartialOrderGraphTest
 * fixture uses via fresh A(id) calls) triggered "POG node count mismatch" in a real repository
 * reload - this reproduces that in isolation, independent of the whole persistence stack, per
 * mapdb-step2-maintree-pog-blocker.
 */
public class PartialOrderGraphSharedArtifactMergeTest {

	private Artifact.Op<?> A(String id) {
		return new SerArtifact<>(new TestArtifactData(id));
	}

	/**
	 * Control: same shape as PartialOrderGraphTest.mergingWithBranchesWorks(), every artifact its
	 * own distinct (if data-equal) object - must succeed, as that existing test already establishes.
	 */
	@Test
	public void merge_distinctArtifactInstances_succeeds() {
		List<Artifact.Op<?>> thisArtifacts1 = Arrays.asList(A("1"), A("3"), A("4"), A("5"));
		List<Artifact.Op<?>> thisArtifacts2 = Arrays.asList(A("1"), A("2"), A("4"), A("5"));
		PartialOrderGraph.Op thisPog = new SerPartialOrderGraph();
		thisPog.merge(thisArtifacts1);
		thisPog.merge(thisArtifacts2);

		List<Artifact.Op<?>> otherArtifacts = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"));
		PartialOrderGraph.Op otherPog = new SerPartialOrderGraph();
		otherPog.merge(otherArtifacts);

		thisPog.merge(otherPog);
	}

	/**
	 * The actual question: identical shape, but "1", "4", and "5" are the SAME artifact object
	 * instance reused across thisPog's construction AND otherPog's construction - exactly what
	 * resolving POG node artifacts against a global id-based index (instead of leaving them as
	 * independently-deserialized duplicates) produces when the same logical artifact legitimately
	 * appears in multiple graphs.
	 */
	@Test
	public void merge_sharedArtifactInstanceAcrossGraphs() {
		Artifact.Op<?> shared1 = A("1");
		Artifact.Op<?> shared4 = A("4");
		Artifact.Op<?> shared5 = A("5");

		List<Artifact.Op<?>> thisArtifacts1 = Arrays.asList(shared1, A("3"), shared4, shared5);
		List<Artifact.Op<?>> thisArtifacts2 = Arrays.asList(shared1, A("2"), shared4, shared5);
		PartialOrderGraph.Op thisPog = new SerPartialOrderGraph();
		thisPog.merge(thisArtifacts1);
		thisPog.merge(thisArtifacts2);

		List<Artifact.Op<?>> otherArtifacts = Arrays.asList(shared1, A("2"), A("3"), shared4, shared5);
		PartialOrderGraph.Op otherPog = new SerPartialOrderGraph();
		otherPog.merge(otherArtifacts);

		thisPog.merge(otherPog);
	}
}
