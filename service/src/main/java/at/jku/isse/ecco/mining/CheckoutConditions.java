package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.logic.LogicCondition;
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
    public static Map<String, LogicCondition> valid(Repository repository) {
        Map<String, LogicCondition> valid = new HashMap<>();
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
    public static Map<String, LogicCondition> minimize(Collection<? extends Association> associations,
                                               List<ConstraintMiner.Suggestion> trustedSuggestions,
                                               Collection<? extends Feature> features) {
        return minimize(read(associations, trustedSuggestions, features));
    }

    /**
     * All {@link #minimize(Input)} needs of the repository: the feature model and each association's
     * condition as terms - read holding the service's lock, since a commit changes the counters the
     * conditions are computed from, so that the minimization can run without it.
     */
    public record Input(Formula featureModel, Map<String, List<PresenceConditionMinimizer.Term>> termsById) {
    }

    public static Input read(Collection<? extends Association> associations,
                             List<ConstraintMiner.Suggestion> trustedSuggestions,
                             Collection<? extends Feature> features) {
        Map<String, List<PresenceConditionMinimizer.Term>> termsById = new HashMap<>();
        for (Association association : associations)
            termsById.put(association.getId(), ModuleConditionBridge.toRevisionTerms(association.computeCondition()));
        return new Input(FeatureModelFormula.compileRevisionAware(trustedSuggestions, features), termsById);
    }

    public static Map<String, LogicCondition> minimize(Input input) {
        Map<String, LogicCondition> result = new HashMap<>();
        for (Map.Entry<String, List<PresenceConditionMinimizer.Term>> entry : input.termsById().entrySet()) {
            List<PresenceConditionMinimizer.Term> minimized = PresenceConditionMinimizer.minimize(input.featureModel(), entry.getValue());
            result.put(entry.getKey(), LogicCondition.of(PresenceConditionMinimizer.toFormula(minimized)));
        }
        return result;
    }
}
