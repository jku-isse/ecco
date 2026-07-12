package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes {@link Checkout#getSurplusModules()} entries that are pure syntactic noise: a
 * {@link ModuleRevision} term absorbed ({@code X + XY = X}) by a simpler term already present on the
 * SAME association's own condition. Unlike {@link SurplusModuleSuppressor}, this needs no accepted
 * feature model -- {@link PresenceConditionMinimizer#absorb} is an unconditional boolean identity,
 * true in every configuration, so it can never remove a genuinely-needed warning.
 *
 * <p>Root cause this addresses: {@code Association.Op#computeLikelyCondition()} enumerates every
 * {@code Module} an association has ever co-occurred with and keeps ALL of them that pass its
 * sufficiency test, without checking minimality -- so an association accumulates, across its commit
 * history, every combination of other features that merely happened to stay constant (e.g.
 * always-absent) so far, not just the minimal necessary term. On real x8 this produced 100,000+
 * surplus entries for a single novel intensional checkout, almost all of it exactly this kind of
 * redundant restatement rather than a real gap (see {@code CONSTRAINT_MINING_DESIGN.md}'s
 * "Surplus-module suppression" section for the full investigation and real-scale numbers).
 *
 * <p>Lives in {@code service}, not {@code base}, even though the root cause is in
 * {@code Association}/{@code Repository} ({@code base}): {@code base} does not depend on
 * {@code service}, so this cannot be wired directly into {@code Repository.Op#compose()} itself.
 * Instead it runs as a post-hoc filter over the {@code Checkout} that {@code compose()} already
 * produced -- the same architecture {@link SurplusModuleSuppressor} already uses, and for the same
 * reason: {@code EccoService.compose()} is the natural, already-established seam for this class of
 * fix.
 */
public final class SurplusLatticeAbsorber {

    private SurplusLatticeAbsorber() {
    }

    /**
     * Mutates {@code checkout.getSurplusModules()} in place, removing entries whose revision-exact
     * term is absorbed by a simpler term on the same owning association's condition. An entry with no
     * absorbing counterpart (including every association's own minimal term) is left untouched --
     * conservative by construction, same as {@link SurplusModuleSuppressor}.
     */
    public static void suppressAbsorbed(Checkout checkout, Repository repository) {
        Map<ModuleRevision, String> surplus = checkout.getSurplusModules();
        if (surplus.isEmpty()) return;

        Map<String, List<ModuleRevision>> candidatesByAssociationId = new HashMap<>();
        for (Map.Entry<ModuleRevision, String> entry : surplus.entrySet()) {
            candidatesByAssociationId.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Map<String, Association> associationsById = new HashMap<>();
        for (Association association : repository.getAssociations()) {
            if (candidatesByAssociationId.containsKey(association.getId())) {
                associationsById.put(association.getId(), association);
            }
        }

        for (Map.Entry<String, List<ModuleRevision>> group : candidatesByAssociationId.entrySet()) {
            Association association = associationsById.get(group.getKey());
            if (association == null) continue; // trace id didn't resolve; leave those entries as-is

            Condition condition = association.computeCondition();
            List<PresenceConditionMinimizer.Term> allTerms = ModuleConditionBridge.toRevisionTerms(condition);
            List<PresenceConditionMinimizer.Term> survivors = PresenceConditionMinimizer.absorb(allTerms);

            for (ModuleRevision candidate : group.getValue()) {
                PresenceConditionMinimizer.Term candidateTerm = ModuleConditionBridge.toRevisionTerm(candidate);
                boolean survives = false;
                for (PresenceConditionMinimizer.Term survivor : survivors) {
                    if (survivor.positive.equals(candidateTerm.positive) && survivor.negative.equals(candidateTerm.negative)) {
                        survives = true;
                        break;
                    }
                }
                if (!survives) {
                    surplus.remove(candidate);
                }
            }
        }
    }
}
