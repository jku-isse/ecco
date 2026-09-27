package at.jku.isse.ecco.maintree;

import at.jku.isse.ecco.maintree.retroactive.condition.setter.RetroactiveConditionSetterVisitor;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A unique node without a FeatureTrace (created via createNode() without an artifact) made
 * RetroactiveConditionSetterVisitor - and with it the commit - fail with a NullPointerException
 * (hit once in practice and fixed only at that one creation site, EccoUtil.deepCopyTreeRec).
 * Like SerNode's own trace methods and BoostVisitor, it now skips a node without a trace.
 */
public class RetroactiveConditionSetterVisitorTest {

    @Test
    public void aUniqueNodeWithoutAFeatureTraceIsSkipped() {
        Node.Op node = new SerEntityFactory().createNode();
        node.setUnique(true);
        assertNull(node.getFeatureTrace(), "precondition: no feature trace");

        assertDoesNotThrow(() -> new RetroactiveConditionSetterVisitor("A").visit(node));
    }
}
