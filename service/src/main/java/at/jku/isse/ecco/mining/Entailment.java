package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;
import org.logicng.solvers.SolverState;

/**
 * One-directional SAT entailment: does {@code known} guarantee {@code goal}, checked by
 * SAT-refuting {@code known AND NOT goal}. Sibling to {@link PresenceConditionMinimizer}'s private
 * {@code isEquivalentUnderCareSet} (which checks two-way equivalence via {@code NOT(a<->b)}) -- this
 * is the plain one-directional form, needed wherever the question is "is Y guaranteed" rather than
 * "are X and Y interchangeable".
 */
public final class Entailment {

    private Entailment() {
    }

    /** Builds and discards its own solver -- for a single, one-off entailment check. */
    public static boolean entails(Formula known, Formula goal) {
        SATSolver solver = MiniSat.miniSat(FormulaFactoryProvider.getFormulaFactory());
        solver.add(known);
        return entails(solver, goal);
    }

    /**
     * {@code known} must already be in {@code solver}. Pushes {@code NOT(goal)} via
     * {@link SATSolver#saveState()}/{@code loadState}, solves, and pops it back off, leaving the
     * solver exactly as it was for the next call -- same reuse pattern as
     * {@code PresenceConditionMinimizer.isEquivalentUnderCareSet}, for callers checking many goals
     * against the same known facts without re-encoding them each time.
     */
    public static boolean entails(SATSolver solver, Formula goal) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SolverState state = solver.saveState();
        try {
            solver.add(f.not(goal));
            return solver.sat() == Tristate.FALSE;
        } finally {
            solver.loadState(state);
        }
    }
}