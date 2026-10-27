package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A copy of an order graph has every node's neighbours - previous and next - in the order of the
 * original's: merging aligns by walking them, so a copy with another order matched two equal lines
 * (two "}") the other way round, and a fork's next commit lost one where its origin's did not.
 */
public class PartialOrderGraphCopyTest {

    @Test
    public void aCopyKeepsTheOrderOfEveryNodesNeighbours() {
        Random random = new Random(3);
        for (int round = 0; round < 2_000; round++) {
            PartialOrderGraph.Op original = randomGraph(new Random(random.nextLong()));
            PartialOrderGraph.Op copy = new SerPartialOrderGraph();
            copy.copy(original);
            assertEquals(describe(original), describe(copy), "round " + round);
        }
    }

    private static PartialOrderGraph.Op randomGraph(Random random) {
        SerPartialOrderGraph pog = new SerPartialOrderGraph();
        for (int s = 0, sequences = 2 + random.nextInt(4); s < sequences; s++) {
            List<Artifact.Op<?>> sequence = new ArrayList<>();
            for (int i = 0, length = random.nextInt(12); i < length; i++)
                sequence.add(new SerArtifact<>(new TestArtifactData("x" + random.nextInt(4))));
            pog.merge(sequence);
        }
        return pog;
    }

    /** every node, by artifact data and sequence number, with its neighbours in their order */
    private static String describe(PartialOrderGraph.Op pog) {
        List<String> lines = new ArrayList<>();
        for (PartialOrderGraph.Node.Op node : pog.collectNodes()) {
            List<String> previous = new ArrayList<>(), next = new ArrayList<>();
            for (PartialOrderGraph.Node.Op n : node.getPrevious()) previous.add(label(n));
            for (PartialOrderGraph.Node.Op n : node.getNext()) next.add(label(n));
            lines.add(previous + " -> " + label(node) + " -> " + next);
        }
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private static String label(PartialOrderGraph.Node.Op node) {
        if (node.getArtifact() == null)
            return node.getNext().isEmpty() ? "TAIL" : "HEAD";
        return node.getArtifact().getData() + "#" + node.getSequenceNumber();
    }
}
