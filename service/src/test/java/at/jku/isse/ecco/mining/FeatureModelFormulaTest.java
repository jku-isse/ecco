package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.feature.SerFeatureRevision;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FeatureModelFormulaTest {

    private static boolean satisfiable(Formula formula) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        SATSolver solver = MiniSat.miniSat(f);
        solver.add(formula);
        return solver.sat() == Tristate.TRUE;
    }

    @Test
    public void noAcceptedSuggestions_producesTautology() {
        Formula formula = FeatureModelFormula.compile(List.of());
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        assertTrue(satisfiable(formula));
        assertFalse(satisfiable(f.not(formula)), "negation of a tautology must be unsatisfiable");
    }

    @Test
    public void hardRequires_forbidsAWithoutB() {
        ConstraintMiner.Suggestion requires = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.REQUIRES, "A", "B", 0.5, 1.0, 4, List.of());
        Formula formula = FeatureModelFormula.compile(List.of(requires));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula, f.literal("A", true), f.literal("B", false))),
                "A without B should be forbidden");
        assertTrue(satisfiable(f.and(formula, f.literal("A", true), f.literal("B", true))),
                "A with B should be allowed");
        assertTrue(satisfiable(f.and(formula, f.literal("A", false), f.literal("B", false))),
                "neither should be allowed");
    }

    @Test
    public void hardExcludes_forbidsBothTogether() {
        ConstraintMiner.Suggestion excludes = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.EXCLUDES, "A", "B", 0.5, 1.0, 4, List.of());
        Formula formula = FeatureModelFormula.compile(List.of(excludes));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula, f.literal("A", true), f.literal("B", true))));
        assertTrue(satisfiable(f.and(formula, f.literal("A", true), f.literal("B", false))));
    }

    @Test
    public void mandatory_forcesFeatureTrue() {
        ConstraintMiner.Suggestion mandatory = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.MANDATORY, "M", null, 1.0, 1.0, 5, List.of());
        Formula formula = FeatureModelFormula.compile(List.of(mandatory));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula, f.literal("M", false))));
        assertTrue(satisfiable(f.and(formula, f.literal("M", true))));
    }

    @Test
    public void nearMissSuggestion_isSkipped() {
        // a near-miss REQUIRES has a real counterexample, so it must never become a hard clause
        ConstraintMiner.Suggestion nearMiss = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.REQUIRES, "A", "B", 0.5, 0.8, 5, List.of(4));
        Formula formula = FeatureModelFormula.compile(List.of(nearMiss));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertTrue(satisfiable(f.and(formula, f.literal("A", true), f.literal("B", false))),
                "near-miss must not be compiled as a hard constraint");
    }

    @Test
    public void compileRevisionAware_atMostOneRevisionPerFeature_forbidsBothRevisionsTrue() {
        SerFeature feature = new SerFeature("f1", "Core");
        SerFeatureRevision r1 = feature.addRevision("1");
        SerFeatureRevision r2 = feature.addRevision("2");

        Formula formula = FeatureModelFormula.compileRevisionAware(List.of(), List.of(feature));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula,
                f.literal(r1.getLogicLiteralRepresentation(), true),
                f.literal(r2.getLogicLiteralRepresentation(), true))),
                "two revisions of the same feature must never be simultaneously selectable");
        assertTrue(satisfiable(f.and(formula, f.literal(r1.getLogicLiteralRepresentation(), true))),
                "selecting just one revision must remain satisfiable");
    }

    @Test
    public void compileRevisionAware_linkClause_revisionTrueEntailsFeatureTrue() {
        SerFeature feature = new SerFeature("f1", "Core");
        SerFeatureRevision r1 = feature.addRevision("1");

        Formula formula = FeatureModelFormula.compileRevisionAware(List.of(), List.of(feature));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula,
                f.literal(r1.getLogicLiteralRepresentation(), true),
                f.literal("Core", false))),
                "selecting a revision without its feature being present must be unsatisfiable");
    }

    @Test
    public void compileRevisionAware_propagatesMinedFeatureLevelConstraintsToRevisionAtoms() {
        SerFeature core = new SerFeature("f1", "Core");
        SerFeatureRevision coreR1 = core.addRevision("1");
        SerFeature other = new SerFeature("f2", "SomeFeature");

        ConstraintMiner.Suggestion excludes = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.EXCLUDES, "Core", "SomeFeature", 0.5, 1.0, 4, List.of());
        Formula formula = FeatureModelFormula.compileRevisionAware(List.of(excludes), List.of(core, other));
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

        assertFalse(satisfiable(f.and(formula,
                f.literal(coreR1.getLogicLiteralRepresentation(), true),
                f.literal("SomeFeature", true))),
                "a mined feature-name-level EXCLUDES must forbid the excluded feature even when only a revision atom of the other side is asserted");
    }
}