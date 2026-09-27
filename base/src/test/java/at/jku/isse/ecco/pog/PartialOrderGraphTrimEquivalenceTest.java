package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * trim() walked the graph depth-first without remembering visited nodes, so a node reachable along
 * several paths was processed once per path - exponential in the number of sequential "diamonds"
 * (measured: 22 diamonds 1.6s, x4 per 2 more), which is what subset()/fork/pull with deselected
 * features runs for every ordered artifact. It also looked every node up in a List of symbols.
 * The original implementation is kept below as the oracle: the trimmed graphs must be identical.
 */
public class PartialOrderGraphTrimEquivalenceTest {

    @Test
    @Timeout(120)
    public void trimMatchesTheOriginalImplementationOnRandomGraphs() {
        Random random = new Random(11);
        for (int round = 0; round < 3_000; round++) {
            long seed = random.nextLong();
            List<Artifact.Op<?>> artifactsA = new ArrayList<>();
            List<Artifact.Op<?>> artifactsB = new ArrayList<>();
            PartialOrderGraph.Op a = randomGraph(new Random(seed), artifactsA);
            PartialOrderGraph.Op b = randomGraph(new Random(seed), artifactsB);
            // keep a random subset (same positions in both copies)
            Random pick = new Random(seed ^ 0x5DEECE66DL);
            List<Artifact.Op<?>> keepA = new ArrayList<>(), keepB = new ArrayList<>();
            for (int i = 0; i < artifactsA.size(); i++) {
                if (pick.nextInt(3) != 0) {
                    keepA.add(artifactsA.get(i));
                    keepB.add(artifactsB.get(i));
                }
            }
            a.trim(keepA);
            referenceTrim(b, keepB);
            assertEquals(describe(b), describe(a), "round " + round);
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // the old code never finishes: fail, don't hang
    public void manySequentialDiamondsTrimQuickly() {
        List<Artifact.Op<?>> keep = new ArrayList<>();
        SerPartialOrderGraph pog = new SerPartialOrderGraph();
        PartialOrderGraph.Node.Op cur = pog.getHead();
        cur.removeChild(pog.getTail());
        for (int i = 0; i < 200; i++) {
            SerArtifact<?> l = new SerArtifact<>(new TestArtifactData("l" + i)), r = new SerArtifact<>(new TestArtifactData("r" + i)), j = new SerArtifact<>(new TestArtifactData("j" + i));
            SerPartialOrderGraphNode ln = new SerPartialOrderGraphNode(l), rn = new SerPartialOrderGraphNode(r), jn = new SerPartialOrderGraphNode(j);
            cur.addChild(ln);
            cur.addChild(rn);
            ln.addChild(jn);
            rn.addChild(jn);
            keep.add(l);
            keep.add(j);
            cur = jn;
        }
        cur.addChild(pog.getTail());

        pog.trim(keep);

        assertEquals(2 + 400, pog.collectNodes().size());
    }

    /** A graph built by merging a few random sequences over a small alphabet: branches and joins. */
    private static PartialOrderGraph.Op randomGraph(Random random, List<Artifact.Op<?>> created) {
        SerPartialOrderGraph pog = new SerPartialOrderGraph();
        int sequences = 1 + random.nextInt(4);
        for (int s = 0; s < sequences; s++) {
            List<Artifact.Op<?>> sequence = new ArrayList<>();
            int length = random.nextInt(12);
            for (int i = 0; i < length; i++) {
                SerArtifact<?> artifact = new SerArtifact<>(new TestArtifactData("x" + random.nextInt(6)));
                sequence.add(artifact);
                created.add(artifact);
            }
            pog.merge(sequence);
        }
        return pog;
    }

    /** Canonical description: every node (by artifact data + sequence number) with its sorted successors. */
    private static String describe(PartialOrderGraph.Op pog) {
        List<String> lines = new ArrayList<>();
        for (PartialOrderGraph.Node.Op node : pog.collectNodes()) {
            List<String> next = new ArrayList<>();
            for (PartialOrderGraph.Node.Op n : node.getNext()) next.add(label(n));
            Collections.sort(next);
            lines.add(label(node) + " -> " + next);
        }
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private static String label(PartialOrderGraph.Node node) {
        return node.getArtifact() == null ? (node.getNext().isEmpty() ? "TAIL" : "HEAD") : node.getArtifact().getData() + "#" + ((PartialOrderGraph.Node.Op) node).getSequenceNumber();
    }

    // ---- the original implementation, verbatim, as the oracle ----

    private static void referenceTrim(PartialOrderGraph.Op pog, Collection<? extends Artifact.Op<?>> symbols) {
        LinkedList<PartialOrderGraph.Node.Op> stack = new LinkedList<>();
        stack.push(pog.getHead());
        while (!stack.isEmpty()) {
            PartialOrderGraph.Node.Op current = stack.pop();
            if (current.getArtifact() != null && !symbols.contains(current.getArtifact())) {
                for (PartialOrderGraph.Node.Op parent : new ArrayList<>(current.getPrevious())) {
                    for (PartialOrderGraph.Node.Op child : current.getNext()) {
                        if (!parent.getNext().contains(child)) {
                            parent.addChild(child);
                        }
                    }
                    parent.removeChild(current);
                }
                for (PartialOrderGraph.Node.Op child : new ArrayList<>(current.getNext())) {
                    current.removeChild(child);
                    stack.push(child);
                }
            } else {
                for (PartialOrderGraph.Node.Op child : current.getNext()) {
                    stack.push(child);
                }
            }
        }
    }
}
