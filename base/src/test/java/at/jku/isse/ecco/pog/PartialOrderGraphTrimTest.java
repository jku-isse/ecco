package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PartialOrderGraph.Op.trim() reconnects every parent of a removed node directly to every one of
 * that node's children (bypassing it), without first checking whether that (parent, child) edge
 * already exists - unlike addRelations() elsewhere in this file, which explicitly guards with
 * `!thisNode.getNext().contains(thisNextNode)` before calling addChild() for the same reason. A
 * diamond pattern where two removed siblings both bypass to the same downstream node hits this:
 * each sibling's bypass loop independently reconnects the shared parent to the shared child,
 * producing a literal duplicate edge (SerPartialOrderGraphNode backs next/previous with plain
 * ArrayLists, so nothing dedups it). The duplicate doesn't crash anything immediately - it's added
 * symmetrically to both next and previous, so the Kahn's-algorithm-style traversal counters used
 * throughout this file stay internally self-consistent - but it corrupts the graph's true edge
 * multiplicity: collectNodeSequencings()/estimateLinearizationCount() see a false 2-way branch on
 * what's actually a single successor, feeding the exact factorial-blowup risk this file otherwise
 * works hard to cap. trim() is live in production (Repository.java's commit path).
 */
public class PartialOrderGraphTrimTest {

	@Test
	public void trimmingADiamondsTwoBranchesDoesNotDuplicateTheReconvergingEdge() {
		Artifact.Op<?> artifactA = new SerArtifact<>(new TestArtifactData("A"));
		Artifact.Op<?> artifactB = new SerArtifact<>(new TestArtifactData("B"));
		Artifact.Op<?> artifactC = new SerArtifact<>(new TestArtifactData("C"));

		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail()); // a fresh POG starts with a default head->tail edge
		SerPartialOrderGraphNode pogNodeA = new SerPartialOrderGraphNode(artifactA);
		SerPartialOrderGraphNode pogNodeB = new SerPartialOrderGraphNode(artifactB);
		SerPartialOrderGraphNode pogNodeC = new SerPartialOrderGraphNode(artifactC);
		pogNodeA.setSequenceNumber(1);
		pogNodeB.setSequenceNumber(2);
		pogNodeC.setSequenceNumber(3);

		// diamond: head -> {A, B} -> C -> tail
		pog.getHead().addChild(pogNodeA);
		pog.getHead().addChild(pogNodeB);
		pogNodeA.addChild(pogNodeC);
		pogNodeB.addChild(pogNodeC);
		pogNodeC.addChild(pog.getTail());

		// trim away A and B, keep only C - both of C's parents get bypassed to head
		pog.trim(Set.of(artifactC));

		assertEquals(1, pog.getHead().getNext().size(),
				"head should have exactly one (not duplicated) edge to C after both of C's other parents are trimmed");
		assertEquals(1, pogNodeC.getPrevious().size(),
				"C should have exactly one (not duplicated) incoming edge from head after trim");
	}
}
