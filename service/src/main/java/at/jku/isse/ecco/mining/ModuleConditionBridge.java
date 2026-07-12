package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a {@link Condition}'s {@link Module}s into the plain, feature-name-only
 * {@link PresenceConditionMinimizer.Term} form that {@link PresenceConditionMinimizer} operates on --
 * mirroring {@code Module}'s own pos/neg {@code Feature[]} conjunction shape, ORed across modules.
 *
 * <p>The OR-across-modules assumption comes from {@link Condition#holds}'s actual implementation
 * ("a condition holds ... when at least one of its module revisions holds", unconditionally, not
 * gated on {@link Condition#getType()}) -- {@code getType()} is a display-string convention used by
 * {@code getModuleConditionString()} and friends, not what actually decides inclusion. This bridge
 * mirrors the real (executable) semantics, not the display one.
 *
 * <p>Deliberately feature-level only, discarding {@code ModuleRevision}'s revision-level detail
 * (same scope limitation as the rest of the constraint-mining feature); read-only, never constructs
 * or persists a {@code Module}/{@code Condition}.
 */
public final class ModuleConditionBridge {

    private ModuleConditionBridge() {
    }

    public static List<PresenceConditionMinimizer.Term> toTerms(Condition condition) {
        List<PresenceConditionMinimizer.Term> terms = new ArrayList<>();
        for (Module module : condition.getModules().keySet()) {
            Set<String> positive = new HashSet<>();
            for (Feature feature : module.getPos()) positive.add(feature.getName());
            Set<String> negative = new HashSet<>();
            for (Feature feature : module.getNeg()) negative.add(feature.getName());
            terms.add(new PresenceConditionMinimizer.Term(positive, negative));
        }
        return terms;
    }

    /**
     * Builds ONE revision-exact {@link PresenceConditionMinimizer.Term} for a single
     * {@code ModuleRevision}: positive side uses {@link FeatureRevision#getLogicLiteralRepresentation()}
     * (revision-exact atoms), negative side stays plain feature names (mirrors
     * {@code ModuleRevision.getNeg(): Feature[]}'s own semantics -- exclusion is "at any revision",
     * asymmetric by design versus the positive side).
     *
     * <p><b>Atom-vocabulary warning</b>: the atoms here are NOT the same vocabulary as
     * {@link #toTerms(Condition)} (feature-name-only). A {@link PresenceConditionMinimizer.Term}
     * from this method must only be combined, via SAT, with a formula from
     * {@link FeatureModelFormula#compileRevisionAware} -- never with {@link FeatureModelFormula#compile}
     * or with feature-name-only {@code Term}s; mixing the two silently produces a meaningless SAT
     * problem, no error.
     */
    public static PresenceConditionMinimizer.Term toRevisionTerm(ModuleRevision moduleRevision) {
        Set<String> positive = new HashSet<>();
        for (FeatureRevision featureRevision : moduleRevision.getPos()) {
            positive.add(featureRevision.getLogicLiteralRepresentation());
        }
        Set<String> negative = new HashSet<>();
        for (Feature feature : moduleRevision.getNeg()) {
            negative.add(feature.getName());
        }
        return new PresenceConditionMinimizer.Term(positive, negative);
    }

    /**
     * Revision-aware sibling of {@link #toTerms(Condition)}: converts a whole {@link Condition} into
     * one {@link PresenceConditionMinimizer.Term} per {@link ModuleRevision} (via {@link
     * #toRevisionTerm(ModuleRevision)}), across every {@link Module} in {@link Condition#getModules()}
     * -- not just one term per {@code Module} key, since a single {@code Module} (a pos/neg
     * feature-name shape) can carry multiple {@code ModuleRevision}s (distinct revision picks for
     * that same shape).
     *
     * <p>Exists for callers that need {@link PresenceConditionMinimizer#absorb} to operate correctly
     * on a revision-carrying repository -- feature-level {@link #toTerms} would conflate different
     * revisions of the same feature into one atom, incorrectly treating e.g. {@code Old@r1} and
     * {@code Old@r2} as identical for subsumption purposes. Same atom-vocabulary warning as {@link
     * #toRevisionTerm}: only pairs meaningfully with revision-aware machinery
     * ({@link FeatureModelFormula#compileRevisionAware}), never with {@link #toTerms}'s plain
     * feature-name output.
     */
    public static List<PresenceConditionMinimizer.Term> toRevisionTerms(Condition condition) {
        List<PresenceConditionMinimizer.Term> terms = new ArrayList<>();
        for (Collection<ModuleRevision> moduleRevisions : condition.getModules().values()) {
            for (ModuleRevision moduleRevision : moduleRevisions) {
                terms.add(toRevisionTerm(moduleRevision));
            }
        }
        return terms;
    }
}
