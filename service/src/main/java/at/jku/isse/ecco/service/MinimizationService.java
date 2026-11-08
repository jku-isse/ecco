package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.mining.AcceptedConstraints;
import at.jku.isse.ecco.mining.CheckoutConditions;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.MinimizationBasis;
import at.jku.isse.ecco.repository.Repository;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The minimized presence conditions stored on the associations of one {@link EccoService}, with the
 * fingerprints ({@link MinimizationBasis}) that tell whether they still hold: those of a "Minimize
 * Presence Conditions" run, shown to the user, and the revision-exact ones checkout can use.
 */
final class MinimizationService {

    private final EccoService owner;

    MinimizationService(EccoService owner) {
        this.owner = owner;
    }

    /**
     * The fingerprint ({@link MinimizationBasis}) of every association as it is now, by association
     * id. A minimization run takes these when it starts, before reading the repository, and passes
     * them to {@link #persistMinimizedConditions} - taking them when persisting would bless results
     * computed before a commit that landed during the run.
     */
    Map<String, String> minimizationBases() {
        this.owner.checkInitialized();
        String repositoryBasis = this.repositoryBasis();
        Map<String, String> bases = new HashMap<>();
        for (Association association : this.owner.getRepository().getAssociations())
            bases.put(association.getId(), MinimizationBasis.of(association, repositoryBasis));
        return bases;
    }

    /**
     * The persisted minimized conditions that still hold, by association id: those whose fingerprint
     * ({@link MinimizationBasis}) matches the repository as it is now. A later commit can change an
     * association's condition, or add a configuration that removes a constraint the minimization
     * relied on; its minimized condition is then left out rather than shown or used stale.
     */
    Map<String, String> validMinimizedConditions() {
        this.owner.checkInitialized();
        Map<String, String> valid = new HashMap<>();
        String repositoryBasis = null;
        for (Association association : this.owner.getRepository().getAssociations()) {
            Association.Op op = (Association.Op) association;
            if (op.getMinimizedCondition() == null || op.getMinimizedConditionBasis() == null)
                continue;
            if (repositoryBasis == null)
                repositoryBasis = this.repositoryBasis();
            if (op.getMinimizedConditionBasis().equals(MinimizationBasis.of(association, repositoryBasis)))
                valid.put(association.getId(), op.getMinimizedCondition());
        }
        return valid;
    }

    private String repositoryBasis() {
        return MinimizationBasis.ofRepository(ConfigurationBridge.readConfigurations(this.owner.getCommits()),
                AcceptedConstraints.acceptedSignatures(this.owner.getRepository().getConstraints()));
    }

    /**
     * Persists a "Minimize Presence Conditions" run's results onto the associations they were computed
     * for (see {@code Association.Op#setMinimizedCondition}), each with the fingerprint taken when the
     * run started ({@link #minimizationBases}), so they survive a repository close/reopen and are only
     * trusted while still valid ({@link #validMinimizedConditions}). One batched write for the whole run.
     * Entries whose association id no longer exists, or that have no fingerprint, are skipped.
     *
     * @param minimizedByAssociationId association id -&gt; minimized condition string
     *                                 ({@code PresenceConditionMinimizer.format(...)})
     * @param basisByAssociationId     association id -&gt; fingerprint, from {@link #minimizationBases}
     */
    void persistMinimizedConditions(Map<String, String> minimizedByAssociationId, Map<String, String> basisByAssociationId) {
        this.owner.checkInitialized();
        checkNotNull(minimizedByAssociationId);
        checkNotNull(basisByAssociationId);
        if (minimizedByAssociationId.isEmpty()) return;

        this.owner.writeTransaction("Error persisting minimized conditions.", repository -> {
            for (Association.Op association : repository.getAssociations()) {
                String minimizedCondition = minimizedByAssociationId.get(association.getId());
                String basis = basisByAssociationId.get(association.getId());
                if (minimizedCondition != null && basis != null) {
                    association.setMinimizedCondition(minimizedCondition, basis);
                    // re-persist an already-tracked association -- safe/idempotent, same idiom
                    // Repository.Op#extract() uses to mark a mutated-in-place association dirty.
                    repository.addAssociation(association);
                }
            }
            return repository;
        });
    }

    /**
     * Computes and stores, for every association, a revision-exact minimized condition checkout can use
     * ({@link CheckoutConditions}), under the trusted accepted constraints ({@link EccoService#acceptedSuggestions}).
     * The fingerprints and the conditions are read holding the service; the minimization runs without
     * it, so it does not block other calls, and results a commit overtook are stored but never used.
     *
     * @return the number of associations a condition was stored for
     */
    int minimizeConditionsForCheckout() {
        // the conditions computed and the feature model compiled here, holding the service: a commit
        // changes the counters the conditions come from - only their minimization runs without it
        record Read(Map<String, String> bases, CheckoutConditions.Input input) {
        }
        Read read;
        synchronized (this.owner) {
            this.owner.checkInitialized();
            read = this.owner.readTransaction("Error reading the repository for minimization.", () -> {
                Repository.Op repository = this.owner.context.repositoryDao.load();
                return new Read(CheckoutConditions.bases(repository), CheckoutConditions.read(repository.getAssociations(),
                        this.owner.acceptedSuggestions(repository), repository.getFeatures()));
            });
        }
        Map<String, String> bases = read.bases();
        Map<String, LogicCondition> minimized = CheckoutConditions.minimize(read.input());
        this.owner.writeTransaction("Error persisting checkout conditions.", repository -> {
            for (Association.Op association : repository.getAssociations()) {
                LogicCondition condition = minimized.get(association.getId());
                String basis = bases.get(association.getId());
                if (condition != null && basis != null) {
                    association.setCheckoutCondition(condition, basis);
                    repository.addAssociation(association);
                }
            }
            return repository;
        });
        return minimized.size();
    }

    /** The stored checkout conditions that are still valid, by association id - see {@link CheckoutConditions#valid}. */
    Map<String, LogicCondition> validCheckoutConditions() {
        this.owner.checkInitialized();
        return this.owner.readTransaction(null, () -> CheckoutConditions.valid(this.owner.context.repositoryDao.load()));
    }
}
