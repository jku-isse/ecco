package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.feature.SerFeatureRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal, isolated repro of the real-x8-scale finding from the intensional-checkout investigation
 * (see project memory "surplus-suppressor-unsat-premises-bug"): running
 * {@link SurplusModuleSuppressor#suppressEntailed} against real x8 with 40 genuinely novel
 * (never-committed-as-that-exact-combination) intensional configurations originally suppressed 100%
 * of surplus warnings on all 40 -- 106,009 baseline entries in, 0 remaining out, every single time.
 * Every one of those 40 combos also happened to violate one of x8's 60 accepted mined constraints
 * (mostly MANDATORY(header), since "header" was present in all 52 real commits).
 *
 * <p>This test proves the mechanism directly rather than by correlation: {@link Entailment#entails}
 * decides "known AND NOT(goal) is UNSAT" -- but {@code suppressEntailed} originally never checked that
 * {@code known} (accepted feature model AND desired-module facts) is satisfiable BEFORE trusting that
 * check. If {@code known} is already contradictory, "known AND NOT(goal)" is trivially UNSAT for
 * literally any goal (classical ex falso quodlibet), so entailment reported true for every surplus
 * candidate -- including ones with no real logical connection to the contradiction at all.
 * {@code suppressEntailed} now guards against this by checking {@code known}'s satisfiability first
 * and skipping suppression entirely (leaving the surplus map untouched) when it's UNSAT.
 *
 * <p>The contradiction is easy to trigger with completely ordinary inputs: a MANDATORY(X) constraint
 * (mined whenever a feature is present in 100% of witnessed commits -- an extremely common, easily
 * auto-accepted shape) combines with any desired {@link at.jku.isse.ecco.module.ModuleRevision} whose
 * negative feature set happens to include X (an ordinary presence-condition shape: "this content
 * applies when X is NOT selected"), which {@link ModuleConditionBridge#toRevisionTerm} faithfully
 * encodes as a literal NOT-X fact. MANDATORY(X) says X; the desired fact says NOT X. Contradiction,
 * with zero exotic setup required -- exactly what real x8 configurations that simply omitted the
 * always-present "header" feature produced at scale.
 */
public class SurplusModuleSuppressorUnsatPremisesBugTest {

    @Test
    public void mandatoryConstraintPlusOrdinaryNegatedDesiredFact_makesKnownUnsatisfiable() {
        SerFeature header = new SerFeature("f1", "header");
        SerFeatureRevision headerR1 = header.addRevision("1");
        SerFeature body = new SerFeature("f3", "Body");
        SerFeatureRevision bodyR1 = body.addRevision("1");

        // a desired module whose content applies "when Body is selected and header is NOT selected"
        // -- an entirely ordinary presence-condition shape, nothing pathological about it (a Module
        // needs >=1 positive feature by construction, hence Body alongside the negated header)
        SerModule desiredModule = new SerModule(new Feature[]{body}, new Feature[]{header});
        SerModuleRevision desiredRevision = desiredModule.addRevision(new FeatureRevision[]{bodyR1}, new Feature[]{header});

        ConstraintMiner.Suggestion mandatoryHeader = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.MANDATORY, "header", null, 1.0, 1.0, 52, List.of());
        Formula revisionAwareFeatureModel =
                FeatureModelFormula.compileRevisionAware(List.of(mandatoryHeader), List.of(header));

        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula desiredFact = PresenceConditionMinimizer.toFormula(
                List.of(ModuleConditionBridge.toRevisionTerm(desiredRevision)));
        Formula known = f.and(revisionAwareFeatureModel, desiredFact);

        SATSolver solver = MiniSat.miniSat(f);
        solver.add(known);
        assertEquals(Tristate.FALSE, solver.sat(),
                "MANDATORY(header) + a desired module whose neg set contains header must be directly contradictory");
    }

    @Test
    public void unsatPremises_leaveAnUnrelatedNeverEntailedSurplusEntryUntouched() {
        SerFeature header = new SerFeature("f1", "header");
        SerFeatureRevision headerR1 = header.addRevision("1");
        SerFeature unrelated = new SerFeature("f2", "Unrelated");
        SerFeatureRevision unrelatedR1 = unrelated.addRevision("1");
        SerFeature body = new SerFeature("f3", "Body");
        SerFeatureRevision bodyR1 = body.addRevision("1");

        // same contradictory premise pair as above
        SerModule desiredModule = new SerModule(new Feature[]{body}, new Feature[]{header});
        SerModuleRevision desiredRevision = desiredModule.addRevision(new FeatureRevision[]{bodyR1}, new Feature[]{header});

        ConstraintMiner.Suggestion mandatoryHeader = new ConstraintMiner.Suggestion(
                ConstraintMiner.Kind.MANDATORY, "header", null, 1.0, 1.0, 52, List.of());
        Formula revisionAwareFeatureModel =
                FeatureModelFormula.compileRevisionAware(List.of(mandatoryHeader), List.of(header, unrelated, body));

        // surplus candidate positively requires Unrelated@1 -- completely disjoint from header and
        // from the mandatory constraint; per unprovableSurplusEntry_isRetained in
        // SurplusModuleSuppressorTest, this is exactly the shape that must NEVER be suppressed under
        // consistent premises, since nothing connects it to anything known
        SerModule surplusModule = new SerModule(new Feature[]{unrelated}, new Feature[]{});
        SerModuleRevision surplusRevision = surplusModule.addRevision(new FeatureRevision[]{unrelatedR1}, new Feature[]{});

        Checkout checkout = new Checkout();
        checkout.getSurplusModules().put(surplusRevision, "assoc-1");

        SurplusModuleSuppressor.suppressEntailed(checkout, Set.of(desiredRevision), revisionAwareFeatureModel);

        // the satisfiability guard in suppressEntailed() must detect the contradictory premises
        // (unrelated to Unrelated@1 -- caused by the omitted MANDATORY(header) feature) and skip
        // suppression entirely, leaving the real, actionable surplus warning intact
        assertTrue(checkout.getSurplusModules().containsKey(surplusRevision),
                "an unrelated surplus entry with no real logical connection to the contradiction must "
                        + "still be retained -- suppressEntailed must guard against unsatisfiable premises "
                        + "before trusting any entailment result from them");
    }
}
