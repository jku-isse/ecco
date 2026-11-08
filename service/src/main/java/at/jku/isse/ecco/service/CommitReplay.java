package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Commits another repository's commits again, in their order, as committing their content would -
 * what a fork, pull and push take the other side's commits in with (FR-D3b/c) - and copies commits
 * to send. Shared by {@link ForkService} and {@link RemoteSyncService}; moved out of EccoService.
 */
final class CommitReplay {

    private final ServiceState state;

    CommitReplay(ServiceState state) {
        this.state = state;
    }

    /**
     * Commits [commits] of [source] here, in their order, as committing their content would - each
     * keeping its id, date and message (Repository.Op#replay) - in one transaction.
     */
    void replay(Repository.Op source, List<? extends Commit> commits) {
        RepositoryContext context = this.state.context();
        try {
            context.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            Repository.Op repository = context.repositoryDao.load();
            this.replayInto(repository, source, commits);
            context.repositoryDao.store(repository);
            context.transactionStrategy.end();
        } catch (RuntimeException | Error e) {
            context.rollbackIfActive();
            throw e;
        }
    }

    /**
     * Commits [commits] of [source] in [repository] (the service's, loaded in a write transaction), in
     * their order - see {@link #replay} - and takes over the decisions of [source] it has not made
     * (Repository.Op#mergeDecisions); stored by the caller. A commit [repository] already has is left
     * out: two pushes that asked for the commits missing before either was taken in send the same.
     */
    void replayInto(Repository.Op repository, Repository.Op source, List<? extends Commit> commits) {
        // as a commit and a merge do (the source was checked when copied: Repository.Op#copyCommitsInto)
        repository.checkArtifactFormats();
        repository.mergeDecisions(source);
        Set<String> had = new HashSet<>();
        for (Commit commit : repository.getCommits())
            had.add(commit.getId());
        boolean replayedAny = false;
        for (Commit commit : commits) {
            // a merge commit has no configuration and nothing to replay
            if (commit.getConfiguration() == null || !had.add(commit.getId()))
                continue;
            replayedAny = true;
            List<Association.Op> associations = new ArrayList<>();
            for (Association association : commit.getAssociations())
                associations.add((Association.Op) association);
            Commit replayed = repository.replay(commit.getConfiguration(), associations, commit.getUsername());
            replayed.setId(commit.getId());
            replayed.setDate(commit.getDate());
            replayed.setCommitMessage(commit.getCommitMessage());
            // a variant for each configuration committed, as a commit records (CommitService)
            if (repository.getVariants().stream().noneMatch(variant -> variant.getConfiguration().equals(replayed.getConfiguration()))) {
                Variant variant = this.state.currentContext().entityFactory.createVariant("Commit", replayed.getConfiguration(), UUID.randomUUID().toString());
                variant.setDescription(commit.getCommitMessage());
                repository.addVariant(variant);
            }
        }
        if (replayedAny) {
            repository.setRetroactiveConditions();
            repository.invalidateMainTree();
        }
    }

    /** [commits] of [repository], with the associations holding their content, as a repository of their own - to send or replay. */
    Repository.Op copyOfCommits(Repository.Op repository, List<? extends Commit> commits) {
        Repository.Op copy = this.state.currentContext().entityFactory.createRepository();
        repository.copyCommitsInto(copy, commits);
        return copy;
    }
}
