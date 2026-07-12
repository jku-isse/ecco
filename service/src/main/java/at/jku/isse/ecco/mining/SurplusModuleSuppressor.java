package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.module.ModuleRevision;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Suppresses {@link Checkout#getSurplusModules()} entries already provably implied by the accepted
 * feature model plus the modules already known to be desired -- i.e. non-actionable noise, not a
 * real gap. Never touches {@link Checkout#getMissing()}: a genuinely missing module means content
 * doesn't exist anywhere in the repository, which is unconditionally real regardless of what the
 * feature model says, unlike a surplus warning that can be an artifact of over-literal comparison
 * (see {@code Repository.Op#compose}'s {@code desiredModules} membership check).
 */
public final class SurplusModuleSuppressor {

    private static final Logger LOGGER = Logger.getLogger(SurplusModuleSuppressor.class.getName());

    private SurplusModuleSuppressor() {
    }

    /**
     * Mutates {@code checkout.getSurplusModules()} in place, removing entries entailed by
     * {@code revisionAwareFeatureModel} together with facts asserting every module in
     * {@code desiredModules} already holds. Conservative by construction: an entry is only removed
     * when entailment is *proven*; anything unprovable is left exactly as
     * {@code Repository.Op#compose} computed it -- this never adds a new surplus entry, and a real
     * revision mismatch (the wrong revision of a multi-revision feature) is never suppressed, since
     * the at-most-one-revision-per-feature clauses in
     * {@link FeatureModelFormula#compileRevisionAware} make the wrong revision's presence
     * unprovable, not provably-redundant. Also guards against unsatisfiable premises: if
     * {@code known} itself is contradictory (e.g. the checkout being composed conflicts with an
     * accepted constraint -- easy to do with mined constraints, and not the caller's fault), a
     * one-directional SAT entailment check against it is vacuously true for *any* goal (ex falso
     * quodlibet), which would otherwise silently suppress the entire surplus report regardless of
     * whether any individual entry has a real logical connection to the contradiction. In that case
     * nothing is suppressed at all -- the surplus map is left exactly as computed, same as for any
     * other unprovable entry.
     */
    public static void suppressEntailed(Checkout checkout, Set<ModuleRevision> desiredModules,
                                         Formula revisionAwareFeatureModel) {
        Map<ModuleRevision, String> surplus = checkout.getSurplusModules();
        if (surplus.isEmpty()) return;

        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        List<Formula> desiredFacts = new ArrayList<>();
        for (ModuleRevision desired : desiredModules) {
            desiredFacts.add(PresenceConditionMinimizer.toFormula(List.of(ModuleConditionBridge.toRevisionTerm(desired))));
        }
        Formula known = f.and(revisionAwareFeatureModel, f.and(desiredFacts));

        SATSolver solver = MiniSat.miniSat(f);
        solver.add(known);

        if (solver.sat() != Tristate.TRUE) {
            LOGGER.log(Level.WARNING, "Surplus-module suppression skipped: the checkout's desired "
                    + "modules conflict with the accepted feature model (premises unsatisfiable), so "
                    + "entailment can't be trusted. Leaving all surplus warnings as-is.");
            return;
        }

        Iterator<Map.Entry<ModuleRevision, String>> it = surplus.entrySet().iterator();
        while (it.hasNext()) {
            ModuleRevision candidate = it.next().getKey();
            Formula goal = PresenceConditionMinimizer.toFormula(List.of(ModuleConditionBridge.toRevisionTerm(candidate)));
            if (Entailment.entails(solver, goal)) {
                it.remove();
            }
        }
    }
}
