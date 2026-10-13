package at.jku.isse.ecco.service;

import at.jku.isse.ecco.*;
import at.jku.isse.ecco.adapter.*;
import at.jku.isse.ecco.adapter.dispatch.*;
import at.jku.isse.ecco.artifact.*;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.mining.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.repository.*;
import at.jku.isse.ecco.service.listener.*;
import at.jku.isse.ecco.storage.*;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.*;
import com.google.inject.*;
import com.google.inject.name.*;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;
import static com.google.common.base.Preconditions.*;

/**
 * Commits the files in the base directory: reads them through the dispatch reader, extracts them
 * into the repository under the given configuration and records the configuration as a variant.
 * Extracted from EccoService, which keeps its public, synchronized API and delegates, like
 * CheckoutService, ConfigurationParser, VariantManager and ConstraintService.
 */
class CommitService {

    private static final Logger LOGGER = Logger.getLogger(CommitService.class.getName());

    private final EccoService owner;

    // set from another thread (the GUI's Cancel) while commit() runs; checked only before anything is
    // stored, so a cancelled commit is rolled back like a failed one and a commit that is already
    // being written always completes
    private volatile boolean cancelRequested;
    private volatile boolean committing;

    CommitService(EccoService owner) {
        this.owner = owner;
    }

    /**
     * Commits the files in the base directory as the given configuration and returns the resulting commit object.
     */
    Commit commit(String commitMessage, Configuration configuration, String committer) {
        owner.checkInitialized();
        checkNotNull(configuration);
        // presence conditions are built from the features of the commits an artifact occurs in: what
        // is committed without any feature gets no condition that can ever hold, so no checkout
        // would contain it - it was accepted and silently lost
        if (configuration.getFeatureRevisions().length == 0)
            throw new EccoException("A commit needs at least one feature: what is committed without any cannot be checked out again."
                    + " For content every variant has, use a feature of its own in every configuration (e.g. BASE).");

        this.cancelRequested = false;
        this.committing = true;
        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();
            repository.addFeatureRevisions(configuration.getFeatureRevisions());
            owner.adoptRepositorySettings(repository);
            Set<Node.Op> nodes = this.readFiles();
            this.checkCancelled();
            ArrayList<Variant> variants = repository.getVariants();

            long extractTime = System.currentTimeMillis();
            repository.checkArtifactFormats();
            this.checkCancelled();
            Commit commit = repository.extract(configuration, nodes, committer);
            repository.setRetroactiveConditions();
            // invalidate (don't eagerly rebuild) rather than call buildMainTree() here: within a
            // single long-lived session, loadDatabase()'s invalidateMainTree() never runs again
            // after the first load (REUSE_DB_ACROSS_TRANSACTIONS short-circuits it), so this is the
            // only thing that keeps a later compose()/getMainTree() call (Repository.java's
            // compose(Configuration) reads it directly) from silently reusing a tree that predates
            // this commit. getMainTree() rebuilds lazily on next actual use.
            repository.invalidateMainTree();
            extractTime = System.currentTimeMillis() - extractTime;

            //storing new variant
            boolean hasConfiguration = false;
            for (Variant v : variants) {
                if (v.getConfiguration().equals(configuration)) {
                    hasConfiguration = true;
                }
            }
            if (!hasConfiguration) {
                Variant memVariant = owner.entityFactory.createVariant("Commit", configuration, UUID.randomUUID().toString());
                memVariant.setDescription(commitMessage);
                repository.addVariant(memVariant);
            }
            commit.setCommitMessage(commitMessage);

            this.checkCancelled();
            owner.repositoryDao.store(repository);

            long endStrategyTime = System.currentTimeMillis();
            owner.transactionStrategy.end();
            endStrategyTime = System.currentTimeMillis() - endStrategyTime;

            LOGGER.info(Repository.class.getName() + ".extract(): " + extractTime +
                    "ms, .transactionStrategy.end(): " + endStrategyTime + "ms");

            owner.listeners.fireStatusChangedEvent();

            return commit;
        } catch (CommitCancelledException e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();
            // a reader may wrap the cancellation thrown from its read event
            for (Throwable cause = e; cause != null; cause = cause.getCause())
                if (cause instanceof CommitCancelledException cancelled)
                    throw cancelled;
            throw new EccoException("Error during commit.", e);
        } catch (Error e) {
            // out of memory, typically: an open transaction made every later operation fail, and
            // close() too - reporting "Not all transactions have been ended" instead of this
            owner.rollbackIfTransactionActive();
            throw e;
        } finally {
            this.committing = false;
            this.cancelRequested = false;
            owner.listeners.setWriteInProgress(false);
        }
    }

    /**
     * Asks the commit in progress to stop. It stops at the next file read or before it extracts or
     * stores, and throws {@link CommitCancelledException}; once it stores, it completes. Without a
     * commit in progress this does nothing.
     */
    void cancel() {
        if (this.committing)
            this.cancelRequested = true;
    }

    /**
     * Throws {@link CommitCancelledException} if the commit in progress was asked to stop; called
     * before each step that stores nothing yet, and for each file read.
     */
    void checkCancelled() {
        if (this.committing && this.cancelRequested)
            throw new CommitCancelledException();
    }

    /**
     * Reads the configuration string from the {@link EccoService#CONFIG_FILE_NAME} file in {@code path}, or "" if there is none.
     * Line breaks in the file (e.g. one feature per line) are collapsed into a single "a.1, b.1" line, so
     * the string reads well wherever it is shown or used as a default commit message.
     */
    String getConfigStringFromFile(Path path) {
        Path configFile = path.resolve(EccoService.CONFIG_FILE_NAME);
        try {
            String configurationString = "";
            if (Files.exists(configFile))
                configurationString = new String(Files.readAllBytes(configFile)).trim()
                        .replaceAll("\\s*,\\s*", ", ")
                        .replaceAll("\\s+", " ");
            return configurationString;
        } catch (IOException e) {
            throw new EccoException("Error during commit: '.config' file existed but could not be read.", e);
        }
    }

    Set<Node.Op> readFiles() {
        return owner.reader.read(owner.baseDir, new Path[]{Paths.get("")});
    }
}
