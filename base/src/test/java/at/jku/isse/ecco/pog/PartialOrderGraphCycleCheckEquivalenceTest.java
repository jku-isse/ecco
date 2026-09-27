package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * merge()'s cycle check ran canReach(v, u) for every edge u -> v - one full graph walk per edge, half
 * of a large ordered file's commit time. PartialOrderGraph.Op.hasCycle() is a topological sort
 * instead; the literal original check is kept below as the oracle.
 */
public class PartialOrderGraphCycleCheckEquivalenceTest {

    @Test
    @Timeout(120)
    public void hasCycleMatchesTheOriginalCheck() {
        Random random = new Random(3);
        int cycles = 0;
        for (int round = 0; round < 3_000; round++) {
            SerPartialOrderGraph pog = new SerPartialOrderGraph();
            for (int s = 1 + random.nextInt(3); s > 0; s--) {
                List<Artifact.Op<?>> sequence = new ArrayList<>();
                for (int i = random.nextInt(10); i > 0; i--)
                    sequence.add(new SerArtifact<>(new TestArtifactData("x" + random.nextInt(5))));
                pog.merge(sequence);
            }
            List<PartialOrderGraph.Node.Op> nodes = new ArrayList<>(pog.collectNodes());
            // random extra edges between real nodes - some of them create cycles
            List<PartialOrderGraph.Node.Op> real = nodes.stream().filter(n -> n.getArtifact() != null).toList();
            for (int e = random.nextInt(3); e > 0 && real.size() > 1; e--) {
                PartialOrderGraph.Node.Op from = real.get(random.nextInt(real.size())), to = real.get(random.nextInt(real.size()));
                if (from != to && !from.getNext().contains(to)) from.addChild(to);
            }
            boolean expected = referenceHasCycle(nodes);
            if (expected) cycles++;
            assertEquals(expected, PartialOrderGraph.Op.hasCycle(nodes), "round " + round);
        }
        assertTrue(cycles > 100, "the rounds must include graphs with cycles: " + cycles);
    }

    // ---- the original check, verbatim, as the oracle ----
    private static boolean referenceHasCycle(Collection<PartialOrderGraph.Node.Op> nodes) {
        for (PartialOrderGraph.Node.Op thisNode : nodes)
            if (thisNode.getArtifact() != null)
                for (PartialOrderGraph.Node.Op nextNode : thisNode.getNext())
                    if (PartialOrderGraph.Op.canReach(nextNode, thisNode))
                        return true;
        return false;
    }
}
