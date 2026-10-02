package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import org.logicng.formulas.Formula;

import java.util.*;

/**
 * Minimized presence conditions a checkout can use in place of the associations' own (see
 * {@code Repository.Op#compose(Configuration, Map)}): revision-exact, in the variables the main tree's
 * node conditions use, and equivalent to them for every configuration the accepted feature model
 * allows.
 * <p>
 * The minimized conditions shown in the GUI ({@code ParallelMinimization}) are feature-level: they
 * read "A" where a node condition reads a specific revision of A, so they cannot stand in for node
 * conditions once a feature has more than one revision. These are computed separately, from each
 * module revision:
 * <ul>
 * <li>a positive feature revision becomes its revision literal
 * ({@link FeatureRevision#getLogicLiteralRepresentation()}), as in the node conditions;</li>
 * <li>a negative feature becomes the negation of every one of its revisions - the node conditions
 * negate the disjunction of its revisions, and no feature-name atom (which the checkout assignment
 * names differently once sanitized) is left in the result;</li>
 * </ul>
 * minimized under {@link FeatureModelFormula#compileRevisionAware} with the trusted accepted
 * constraints. Each is stored with a fingerprint ({@link MinimizationBasis}) of the association's
 * condition, the distinct revision-level configurations and the accepted constraints, and only used
 * while that matches.
 */
public final class CheckoutConditions {

    private CheckoutConditions() {
    }

    /** The part of the fingerprint shared by every association, from the repository as it is. */
    public static String repositoryBasis(Repository repository) {
        List<Set<String>> configurations = new ArrayList<>();
        for (Commit commit : repository.getCommits()) {
            Configuration configuration = commit.getConfiguration();
            if (configuration == null) continue;
            Set<String> revisions = new HashSet<>();
            for (FeatureRevision featureRevision : configuration.getFeatureRevisions())
                revisions.add(featureRevision.getLogicLiteralRepresentation());
            configurations.add(revisions);
        }
        return MinimizationBasis.ofRepository(configurations, AcceptedConstraints.acceptedSignatures(repository.getConstraints()));
    }

    /** Association id -&gt; fingerprint, for the repository as it is now. */
    public static Map<String, String> bases(Repository repository) {
        String repositoryBasis = repositoryBasis(repository);
        Map<String, String> bases = new HashMap<>();
        for (Association association : repository.getAssociations())
            bases.put(association.getId(), MinimizationBasis.of(association, repositoryBasis));
        return bases;
    }

    /** The stored checkout conditions whose fingerprint still matches, by association id. */
    public static Map<String, String> valid(Repository repository) {
        Map<String, String> valid = new HashMap<>();
        String repositoryBasis = null;
        for (Association association : repository.getAssociations()) {
            Association.Op op = (Association.Op) association;
            if (op.getCheckoutCondition() == null || op.getCheckoutConditionBasis() == null)
                continue;
            if (repositoryBasis == null)
                repositoryBasis = repositoryBasis(repository);
            if (op.getCheckoutConditionBasis().equals(MinimizationBasis.of(association, repositoryBasis)))
                valid.put(association.getId(), op.getCheckoutCondition());
        }
        return valid;
    }

    /**
     * Minimizes every association's condition under the revision-aware feature model built from
     * {@code trustedSuggestions}; association id -&gt; LogicNG condition string.
     */
    public static Map<String, String> minimize(Collection<? extends Association> associations,
                                               List<ConstraintMiner.Suggestion> trustedSuggestions,
                                               Collection<? extends Feature> features) {
        Formula featureModel = FeatureModelFormula.compileRevisionAware(trustedSuggestions, features);
        Map<String, String> result = new HashMap<>();
        for (Association association : associations) {
            List<PresenceConditionMinimizer.Term> terms = revisionTerms(association.computeCondition());
            List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(featureModel, terms);
            result.put(association.getId(), PresenceConditionMinimizer.toFormula(minimized).toString());
        }
        return result;
    }

    static List<PresenceConditionMinimizer.Term> revisionTerms(Condition condition) {
        List<PresenceConditionMinimizer.Term> terms = new ArrayList<>();
        for (Collection<ModuleRevision> moduleRevisions : condition.getModules().values()) {
            for (ModuleRevision moduleRevision : moduleRevisions) {
                Set<String> positive = new HashSet<>();
                for (FeatureRevision featureRevision : moduleRevision.getPos())
                    positive.add(featureRevision.getLogicLiteralRepresentation());
                Set<String> negative = new HashSet<>();
                for (Feature feature : moduleRevision.getNeg())
                    for (FeatureRevision featureRevision : feature.getRevisions())
                        negative.add(featureRevision.getLogicLiteralRepresentation());
                terms.add(new PresenceConditionMinimizer.Term(positive, negative));
            }
        }
        return terms;
    }
}
