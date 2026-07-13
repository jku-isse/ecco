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
import java.util.function.Function;

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
        safeTransaction(repository -> {
            repository.addConstraint(toConstraintKind(kind), featureA, featureB);
            return repository;
        });
    }

    public void unacceptConstraint(Constraint.Kind kind, String featureA, String featureB) {
        owner.checkInitialized();
        checkNotNull(kind);
        checkNotNull(featureA);
        safeTransaction(repository -> {
            String id = kind.name() + "|" + featureA + "|" + (featureB == null ? "" : featureB);
            Constraint existing = repository.getConstraint(id);
            if (existing != null) repository.removeConstraint(existing);
            return repository;
        });
    }

    public List<String> checkConstraintViolations(Configuration configuration) {
        owner.checkInitialized();
        checkNotNull(configuration);
        if (!owner.constraintViolationWarningsEnabled) return List.of();
        Repository.Op repository = owner.repositoryDao.load();
        List<ConstraintMiner.Suggestion> acceptedSuggestions = acceptedSuggestions(repository);
        Set<String> selectedFeatures = ConfigurationBridge.tokensOf(configuration);
        return ConstraintViolationChecker.checkViolations(selectedFeatures, acceptedSuggestions);
    }

    private void safeTransaction(Function<Repository.Op, Repository.Op> transaction) {
        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            Repository.Op repository = owner.repositoryDao.load();

            repository = transaction.apply(repository);

            owner.repositoryDao.store(repository);
            owner.transactionStrategy.end();

            owner.listeners.fireStatusChangedEvent();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during repository write transaction.", e);
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }

}
