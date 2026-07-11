package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.junit.jupiter.api.Test;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntailmentTest {

    @Test
    public void entails_trueWhenGoalIsALogicalConsequence() {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula a = f.literal("A", true);
        Formula b = f.literal("B", true);
        Formula known = f.and(a, f.implication(a, b)); // A & (A -> B)

        assertTrue(Entailment.entails(known, b));
    }

    @Test
    public void entails_falseWhenGoalIsUnrelated() {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula known = f.literal("A", true);
        Formula unrelated = f.literal("B", true);

        assertFalse(Entailment.entails(known, unrelated));
    }

    @Test
    public void entails_falseWhenKnownContradictsGoal() {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula known = f.literal("A", false); // ~A
        Formula goal = f.literal("A", true);   // A

        assertFalse(Entailment.entails(known, goal));
    }

    @Test
    public void solverOverload_leavesSolverStateUnchangedAcrossCalls() {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SATSolver solver = MiniSat.miniSat(f);
        solver.add(f.and(f.literal("A", true), f.implication(f.literal("A", true), f.literal("B", true))));

        assertTrue(Entailment.entails(solver, f.literal("B", true)));
        // solver must be back to exactly "known" after the first call -- re-checking the same goal,
        // and a different, unrelated one, must both still work correctly, proving no leftover state
        assertTrue(Entailment.entails(solver, f.literal("B", true)));
        assertFalse(Entailment.entails(solver, f.literal("C", true)));
        assertTrue(solver.sat() == org.logicng.datastructures.Tristate.TRUE);
    }
}
