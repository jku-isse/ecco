package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * canReach() compares nodes by sequence number (they may come from different graphs), but real
 * nodes whose number is not assigned (UNASSIGNED / NOT_MATCHED, both negative) then compared equal
 * to ANY other such node - e.g. two unrelated siblings "reached" each other, which merge()'s cycle
 * and redundancy checks report as a false cycle. Unassigned numbers now only match by identity.
 */
public class CanReachUnassignedTest {

    @Test
    public void unrelatedNodesWithUnassignedSequenceNumbersDoNotReachEachOther() {
        SerPartialOrderGraph pog = new SerPartialOrderGraph();
        PartialOrderGraph.Node.Op head = pog.getHead();
        PartialOrderGraph.Node.Op tail = pog.getTail();
        head.removeChild(tail);
        SerPartialOrderGraphNode a = new SerPartialOrderGraphNode(new SerArtifact<>(new TestArtifactData("a")));
        SerPartialOrderGraphNode b = new SerPartialOrderGraphNode(new SerArtifact<>(new TestArtifactData("b")));
        head.addChild(a);
        head.addChild(b);
        a.addChild(tail);
        b.addChild(tail);
        a.setSequenceNumber(PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER);
        b.setSequenceNumber(PartialOrderGraph.NOT_MATCHED_SEQUENCE_NUMBER);

        assertFalse(PartialOrderGraph.Op.canReach(a, b), "siblings don't reach each other");
        assertFalse(PartialOrderGraph.Op.canReach(b, a));
        assertTrue(PartialOrderGraph.Op.canReach(a, a), "a node reaches itself");
        assertTrue(PartialOrderGraph.Op.canReach(head, b), "head reaches b");
        assertTrue(PartialOrderGraph.Op.canReach(a, tail), "a reaches the tail");
    }
}
