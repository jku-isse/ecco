package at.jku.isse.ecco.maintree;

import at.jku.isse.ecco.maintree.building.BoostConditionVisitor;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An association is boosted - its one proactive condition given to all its artifacts - unless it
 * holds contradicting proactive conditions. Conditions were compared as strings, so the same
 * condition written differently ("A & B", "B & A") counted as a contradiction.
 */
public class BoostConditionTest {

    private final SerEntityFactory entityFactory = new SerEntityFactory();

    private Node.Op association(String... proactiveConditions) {
        Node.Op root = this.entityFactory.createNode(this.entityFactory.createArtifact(new TestData("root")));
        int i = 0;
        for (String condition : proactiveConditions) {
            Node.Op node = this.entityFactory.createNode(this.entityFactory.createArtifact(new TestData("line " + i++)));
            if (condition != null)
                node.getFeatureTrace().setProactiveCondition(condition);
            root.addChild(node);
        }
        return root;
    }

    private static BoostConditionVisitor visit(Node.Op association) {
        BoostConditionVisitor visitor = new BoostConditionVisitor();
        association.traverse(visitor);
        return visitor;
    }

    @Test
    public void oneConditionIsBoosted() {
        BoostConditionVisitor visitor = visit(this.association("A", null, "A"));
        assertTrue(visitor.isBoostPossible());
        assertEquals("A", visitor.getBoostCondition());
    }

    @Test
    public void equivalentConditionsWrittenDifferentlyAreBoosted() {
        assertTrue(visit(this.association("A & B", "B & A", null)).isBoostPossible());
        assertTrue(visit(this.association("A | ~B", "~(~A & B)")).isBoostPossible());
    }

    @Test
    public void contradictingConditionsAreNotBoosted() {
        assertFalse(visit(this.association("A", "B")).isBoostPossible());
        // a contradiction stays one, whatever comes after it
        assertFalse(visit(this.association("A", "B", "A")).isBoostPossible());
        assertFalse(visit(this.association("A & B", "A")).isBoostPossible());
    }

    @Test
    public void noConditionIsNotBoosted() {
        assertFalse(visit(this.association(null, null)).isBoostPossible());
    }

    private record TestData(String name) implements at.jku.isse.ecco.artifact.ArtifactData {
    }
}
