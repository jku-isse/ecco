package at.jku.isse.ecco.core;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A constraint kind means the same as a clause (the feature model) and as a check on a selection (the warnings, the feature toggles). */
public class ConstraintKindTest {

    @Test
    public void theClauseAndTheCheckAgreeOnEverySelection() {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        for (Constraint.Kind kind : Constraint.Kind.values()) {
            String b = kind == Constraint.Kind.MANDATORY ? null : "B";
            for (int mask = 0; mask < 4; mask++) {
                Set<String> selected = new HashSet<>();
                Assignment assignment = new Assignment();
                if ((mask & 1) != 0) { selected.add("A"); assignment.addLiteral(f.literal("A", true)); } else assignment.addLiteral(f.literal("A", false));
                if ((mask & 2) != 0) { selected.add("B"); assignment.addLiteral(f.literal("B", true)); } else assignment.addLiteral(f.literal("B", false));
                assertEquals(!kind.clause(f, "A", b).evaluate(assignment), kind.isViolatedBy(selected, "A", b), kind + " with " + selected);
            }
        }
        assertEquals("REQUIRES A -> B violated: A is selected without B", Constraint.Kind.REQUIRES.violation("A", "B"));
        assertEquals("MANDATORY A", Constraint.Kind.MANDATORY.describe("A", null));
    }
}
