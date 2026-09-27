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

    CommitService(EccoService owner) {
        this.owner = owner;
    }

    /**
     * Commits the files in the base directory as the given configuration and returns the resulting commit object.
     */
    Commit commit(String commitMessage, Configuration configuration, String committer) {
        owner.checkInitialized();
        checkNotNull(configuration);

        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();
            repository.addFeatureRevisions(configuration.getFeatureRevisions());
            Set<Node.Op> nodes = this.readFiles();
            ArrayList<Variant> variants = repository.getVariants();

            long extractTime = System.currentTimeMillis();
            repository.checkOrderedArtifacts();
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

            owner.repositoryDao.store(repository);

            long endStrategyTime = System.currentTimeMillis();
            owner.transactionStrategy.end();
            endStrategyTime = System.currentTimeMillis() - endStrategyTime;

            LOGGER.info(Repository.class.getName() + ".extract(): " + extractTime +
                    "ms, .transactionStrategy.end(): " + endStrategyTime + "ms");

            owner.listeners.fireStatusChangedEvent();

            return commit;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();
            throw new EccoException("Error during commit.", e);
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }

    /**
     * Reads the configuration string from the {@link EccoService#CONFIG_FILE_NAME} file in {@code path}, or "" if there is none.
     */
    String getConfigStringFromFile(Path path) {
        Path configFile = path.resolve(EccoService.CONFIG_FILE_NAME);
        try {
            String configurationString = "";
            if (Files.exists(configFile))
                configurationString = new String(Files.readAllBytes(configFile)).trim();
            return configurationString;
        } catch (IOException e) {
            throw new EccoException("Error during commit: '.config' file existed but could not be read.", e);
        }
    }

    Set<Node.Op> readFiles() {
        return owner.reader.read(owner.baseDir, new Path[]{Paths.get("")});
    }
}
