package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.mining.AcceptedConstraints;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.mining.ConstraintViolationChecker;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Owns constraint-suggestion mining/acceptance for one {@link EccoService} instance: re-mining fresh
 * accepted suggestions ({@link #acceptedSuggestions}), accepting/un-accepting individual suggestions
 * into the repository, and checking a configuration against them. {@link EccoService#compose} keeps
 * its own orchestration logic (surplus absorption/suppression are not part of this cluster) but calls
 * back into {@link #acceptedSuggestions} for the pieces that live here. Uses
 * {@link EccoService#ACCEPTED_CONSTRAINT_MIN_WITNESS}/{@link EccoService#ACCEPTED_CONSTRAINT_CONFIDENCE}
 * rather than its own copies, since {@code ConstraintSuggestionsView} references those constants
 * directly on {@code EccoService}.
 */
public class ConstraintService {

    private final EccoService owner;

    public ConstraintService(EccoService owner) {
        this.owner = owner;
    }

    public List<ConstraintMiner.Suggestion> acceptedSuggestions(Repository repository) {
        List<Set<String>> configs = ConfigurationBridge.readConfigurations(owner);
        List<ConstraintMiner.Suggestion> mined =
                new ConstraintMiner(EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS, EccoService.ACCEPTED_CONSTRAINT_CONFIDENCE, null).mine(configs);
        Set<String> accepted = AcceptedConstraints.acceptedSignatures(repository.getConstraints());
        List<ConstraintMiner.Suggestion> result = new ArrayList<>();
        for (ConstraintMiner.Suggestion suggestion : mined) {
            if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                result.add(suggestion);
            }
        }
        return result;
    }

    private static Constraint.Kind toConstraintKind(ConstraintMiner.Kind kind) {
        return Constraint.Kind.valueOf(kind.name());
    }

    public void acceptConstraint(ConstraintMiner.Suggestion suggestion) {
        owner.checkInitialized();
        checkNotNull(suggestion);
        acceptConstraint(suggestion.kind, suggestion.a, suggestion.b);
    }

    public void acceptConstraint(ConstraintMiner.Kind kind, String featureA, String featureB) {
        owner.checkInitialized();
        checkNotNull(kind);
        checkNotNull(featureA);
        owner.writeTransaction(repository -> {
            accept(repository, toConstraintKind(kind), featureA, featureB);
            return repository;
        });
    }

    // a suggestion is accepted, rejected or neither - deciding one way withdraws the other decision
    private static void accept(Repository.Op repository, Constraint.Kind kind, String featureA, String featureB) {
        Constraint rejected = repository.getRejectedConstraint(Constraint.buildId(kind.name(), featureA, featureB));
        if (rejected != null) repository.removeRejectedConstraint(rejected);
        repository.addConstraint(kind, featureA, featureB);
    }

    private static void reject(Repository.Op repository, Constraint.Kind kind, String featureA, String featureB) {
        Constraint accepted = repository.getConstraint(Constraint.buildId(kind.name(), featureA, featureB));
        if (accepted != null) repository.removeConstraint(accepted);
        repository.addRejectedConstraint(kind, featureA, featureB);
    }

    /**
     * Accepts every suggestion in {@code suggestions} as ONE repository transaction - one
     * {@code repositoryDao.store()}, one {@code fireStatusChangedEvent()} - instead of calling
     * {@link #acceptConstraint(ConstraintMiner.Suggestion)} once per suggestion. That per-item
     * version was observed to make accepting many suggestions at once (e.g. from
     * {@code ConstraintSuggestionsView}'s multi-select) take a long time on the FX thread: each
     * individual accept does a full repository persist AND fires a real
     * {@link at.jku.isse.ecco.service.listener.EccoListener} event that every open tab (the Feature
     * Model graph, this class's own {@code ConstraintSuggestionsView} re-mining, ...) reacts to with
     * its own full, O(commits x features) re-scan - so N accepted suggestions did N of those
     * re-scans, not one. No-op if {@code suggestions} is empty (no transaction, no event).
     */
    public void acceptConstraints(List<ConstraintMiner.Suggestion> suggestions) {
        owner.checkInitialized();
        checkNotNull(suggestions);
        if (suggestions.isEmpty()) return;
        owner.writeTransaction(repository -> {
            for (ConstraintMiner.Suggestion suggestion : suggestions) {
                checkNotNull(suggestion);
                accept(repository, toConstraintKind(suggestion.kind), suggestion.a, suggestion.b);
            }
            return repository;
        });
    }

    public void unacceptConstraint(Constraint.Kind kind, String featureA, String featureB) {
        owner.checkInitialized();
        checkNotNull(kind);
        checkNotNull(featureA);
        owner.writeTransaction(repository -> {
            String id = Constraint.buildId(kind.name(), featureA, featureB);
            Constraint existing = repository.getConstraint(id);
            if (existing != null) repository.removeConstraint(existing);
            return repository;
        });
    }

    /** Batched "move back to pending" for multiple accepted constraints - see {@link #acceptConstraints}. */
    public void unacceptConstraints(List<ConstraintSuggestionPreferences.AcceptedConstraint> constraints) {
        owner.checkInitialized();
        checkNotNull(constraints);
        if (constraints.isEmpty()) return;
        owner.writeTransaction(repository -> {
            for (ConstraintSuggestionPreferences.AcceptedConstraint constraint : constraints) {
                checkNotNull(constraint);
                String id = Constraint.buildId(constraint.kind.name(), constraint.a, constraint.b);
                Constraint existing = repository.getConstraint(id);
                if (existing != null) repository.removeConstraint(existing);
            }
            return repository;
        });
    }

    /**
     * Records the suggestions as rejected in the repository, in one transaction, so they are not
     * proposed again - here or in any repository they reach by fork, pull or push. Withdraws an
     * acceptance of the same suggestion. No-op if {@code suggestions} is empty.
     */
    public void rejectConstraints(List<ConstraintMiner.Suggestion> suggestions) {
        owner.checkInitialized();
        checkNotNull(suggestions);
        if (suggestions.isEmpty()) return;
        owner.writeTransaction(repository -> {
            for (ConstraintMiner.Suggestion suggestion : suggestions) {
                checkNotNull(suggestion);
                reject(repository, toConstraintKind(suggestion.kind), suggestion.a, suggestion.b);
            }
            return repository;
        });
    }

    /** "Move back to pending" for rejected suggestions, in one transaction. No-op if empty. */
    public void unrejectConstraints(List<ConstraintSuggestionPreferences.AcceptedConstraint> constraints) {
        owner.checkInitialized();
        checkNotNull(constraints);
        if (constraints.isEmpty()) return;
        owner.writeTransaction(repository -> {
            for (ConstraintSuggestionPreferences.AcceptedConstraint constraint : constraints) {
                checkNotNull(constraint);
                Constraint existing = repository.getRejectedConstraint(Constraint.buildId(constraint.kind.name(), constraint.a, constraint.b));
                if (existing != null) repository.removeRejectedConstraint(existing);
            }
            return repository;
        });
    }

    /**
     * Moves the rejections this machine's preferences hold for this repository - where rejections
     * were kept before they were stored in the repository - into the repository, and forgets them
     * there. A suggestion the repository has accepted in the meantime stays accepted.
     *
     * @return how many rejections the preferences held; 0 means nothing was written
     */
    public int moveLocalRejectionsIntoRepository() {
        owner.checkInitialized();
        Set<String> local = ConstraintSuggestionPreferences.getRejected(owner.getRepositoryDir());
        if (local.isEmpty()) return 0;
        owner.writeTransaction(repository -> {
            for (String signature : local) {
                ConstraintSuggestionPreferences.AcceptedConstraint parsed = ConstraintSuggestionPreferences.parseSignature(signature);
                if (parsed != null && repository.getConstraint(signature) == null)
                    repository.addRejectedConstraint(toConstraintKind(parsed.kind), parsed.a, parsed.b);
            }
            return repository;
        });
        ConstraintSuggestionPreferences.forget(owner.getRepositoryDir());
        return local.size();
    }

    public List<String> checkConstraintViolations(Configuration configuration) {
        owner.checkInitialized();
        checkNotNull(configuration);
        if (!owner.constraintViolationWarningsEnabled) return List.of();
        // inside a transaction, like EccoService.compose() - see ReadWithoutTransactionTest
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
            Repository.Op repository = owner.repositoryDao.load();
            List<ConstraintMiner.Suggestion> acceptedSuggestions = acceptedSuggestions(repository);
            Set<String> selectedFeatures = ConfigurationBridge.tokensOf(configuration);
            List<String> violations = ConstraintViolationChecker.checkViolations(selectedFeatures, acceptedSuggestions);
            owner.transactionStrategy.end();
            return violations;
        } catch (RuntimeException e) {
            owner.rollbackIfTransactionActive();
            throw e;
        }
    }

}
