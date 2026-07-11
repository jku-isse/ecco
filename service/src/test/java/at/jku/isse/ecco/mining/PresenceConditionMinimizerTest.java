package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PresenceConditionMinimizerTest {

    private static boolean satisfiable(Formula formula) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SATSolver solver = MiniSat.miniSat(f);
        solver.add(formula);
        return solver.sat() == Tristate.TRUE;
    }

    private static boolean isTautology(Formula formula) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        return satisfiable(formula) && !satisfiable(f.not(formula));
    }

    @Test
    public void requiresConstraint_dropsRedundantPositiveLiteral() {
        ConstraintMiner.Suggestion requires = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.REQUIRES, "A", "B", 0.5, 1.0, 4, List.of());
        Formula featureModel = FeatureModelFormula.compile(List.of(requires));

        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A", "B"), Set.of()));

        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);

        assertEquals(1, minimized.size());
        assertEquals(Set.of("A"), minimized.get(0).positive);
        assertEquals(Set.of(), minimized.get(0).negative);
    }

    @Test
    public void excludesConstraint_dropsRedundantNegativeLiteral() {
        ConstraintMiner.Suggestion excludes = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.EXCLUDES, "A", "B", 0.5, 1.0, 4, List.of());
        Formula featureModel = FeatureModelFormula.compile(List.of(excludes));

        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A"), Set.of("B")));

        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);

        assertEquals(1, minimized.size());
        assertEquals(Set.of("A"), minimized.get(0).positive);
        assertEquals(Set.of(), minimized.get(0).negative);
    }

    @Test
    public void duplicateTerm_isDropped() {
        Formula featureModel = FeatureModelFormula.compile(List.of()); // no constraints

        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A"), Set.of()),
                new PresenceConditionMinimizer.Term(Set.of("A"), Set.of()));

        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);

        assertEquals(1, minimized.size());
    }

    @Test
    public void mandatoryFeature_collapsesDisjunctionToTautology() {
        ConstraintMiner.Suggestion mandatory = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.MANDATORY, "A", null, 1.0, 1.0, 5, List.of());
        Formula featureModel = FeatureModelFormula.compile(List.of(mandatory));

        // "A or B" is trivially true once A is mandatory, regardless of B
        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A"), Set.of()),
                new PresenceConditionMinimizer.Term(Set.of("B"), Set.of()));

        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);

        assertTrue(isTautology(PresenceConditionMinimizer.toFormula(minimized)));
    }

    @Test
    public void noApplicableConstraints_leavesTermUnchanged() {
        Formula featureModel = FeatureModelFormula.compile(List.of()); // tautology, no constraints

        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A", "B"), Set.of()));

        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);

        assertEquals(1, minimized.size());
        assertEquals(Set.of("A", "B"), minimized.get(0).positive);
    }

    @Test
    public void minimizedResult_isEquivalentToOriginalUnderFeatureModel() {
        ConstraintMiner.Suggestion requiresAB = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.REQUIRES, "A", "B", 0.5, 1.0, 4, List.of());
        ConstraintMiner.Suggestion excludesBC = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.EXCLUDES, "B", "C", 0.5, 1.0, 4, List.of());
        Formula featureModel = FeatureModelFormula.compile(List.of(requiresAB, excludesBC));

        List<PresenceConditionMinimizer.Term> terms = List.of(
                new PresenceConditionMinimizer.Term(Set.of("A", "B"), Set.of("C")),
                new PresenceConditionMinimizer.Term(Set.of("D"), Set.of()));

        Formula original = PresenceConditionMinimizer.toFormula(terms);
        List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);
        Formula result = PresenceConditionMinimizer.toFormula(minimized);

        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula disagreement = f.and(featureModel, f.not(f.equivalence(original, result)));
        assertFalse(satisfiable(disagreement),
                "minimized formula must agree with the original wherever the feature model holds");
    }
}
