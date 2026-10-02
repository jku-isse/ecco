package at.jku.isse.ecco.service;

import at.jku.isse.ecco.featuretrace.RejectedTrace;
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
import org.logicng.formulas.Formula;
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
 * Composes variants and writes them out: checkout (including the .config and .warnings files),
 * checkout of a given node, and re-writing a single checked-out file. Extracted from EccoService,
 * which keeps its public, synchronized API and delegates, like VariantManager, ConstraintService
 * and ConfigurationParser.
 */
class CheckoutService {

    private static final Logger LOGGER = Logger.getLogger(CheckoutService.class.getName());

    private final EccoService owner;

    CheckoutService(EccoService owner) {
        this.owner = owner;
    }

    /**
     * Composes checkout with given configuration.
     *
     * @param configuration Configuration to be composed.
     * @return Checkout with composed artifacts.
     */
    Checkout compose(Configuration configuration) {
        owner.checkInitialized();
        checkNotNull(configuration);
        // read inside a transaction like every other read: outside of one, load() returns whatever
        // database the last transaction left loaded - none right after open() (NPE), or a stale one
        // if another process committed since (see ReadWithoutTransactionTest)
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
            Checkout checkout = this.composeInTransaction(configuration);
            owner.transactionStrategy.end();
            return checkout;
        } catch (RuntimeException e) {
            owner.rollbackIfTransactionActive();
            throw e;
        }
    }
    private Checkout composeInTransaction(Configuration configuration) {
        Repository.Op repository = owner.repositoryDao.load();
        Map<String, String> minimized = owner.minimizedConditionsInCheckout ? CheckoutConditions.valid(repository) : Map.of();
        if (owner.minimizedConditionsInCheckout)
            LOGGER.info("Checking out with minimized conditions for " + minimized.size() + " of " + repository.getAssociations().size() + " associations.");
        Checkout checkout = repository.compose(configuration, minimized);
        if (owner.surplusAbsorptionEnabled && !checkout.getSurplusModules().isEmpty()) {
            try {
                SurplusLatticeAbsorber.suppressAbsorbed(checkout, repository);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Surplus-lattice absorption failed; leaving surplus warnings as-is.", e);
            }
        }
        if (owner.surplusSuppressionEnabled && !checkout.getSurplusModules().isEmpty()) {
            try {
                List<ConstraintMiner.Suggestion> acceptedSuggestions = owner.acceptedSuggestions(repository);
                Formula revisionAwareFeatureModel =
                        FeatureModelFormula.compileRevisionAware(acceptedSuggestions, repository.getFeatures());
                Set<ModuleRevision> desiredModules =
                        new HashSet<>(repository.getOrphanedConfigurationModules(configuration));
                SurplusModuleSuppressor.suppressEntailed(checkout, desiredModules, revisionAwareFeatureModel);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Surplus-module suppression failed; leaving surplus warnings as-is.", e);
            }
        }
        if (owner.constraintViolationWarningsEnabled) {
            try {
                List<ConstraintMiner.Suggestion> acceptedSuggestions = owner.acceptedSuggestions(repository);
                Set<String> selectedFeatures = ConfigurationBridge.tokensOf(configuration);
                checkout.getConstraintWarnings().addAll(
                        ConstraintViolationChecker.checkViolations(selectedFeatures, acceptedSuggestions));
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Constraint-violation check failed; skipping.", e);
            }
        }
        return checkout;
    }
    /**
     * Checks out the implementation of the given configuration into the base directory.
     *
     * @param configuration The configuration to be checked out.
     * @return The checkout object.
     */
    Checkout checkout(Configuration configuration) {
        owner.listeners.setWriteInProgress(true);
        try {
            Checkout checkout = this.compose(configuration);

            Set<Node> nodes = this.selectArtifacts(checkout);
            owner.writer.write(owner.baseDir, nodes);

            // TODO: check if rest of method is affected by code change to compose
            // write config file into base directory
            Path configFile = owner.baseDir.resolve(EccoService.CONFIG_FILE_NAME);
            if (Files.exists(configFile)) {
                throw new EccoException("Configuration file already exists in base directory.");
            } else {
                try {
                    Files.write(configFile, configuration.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    throw new EccoException("Could not create configuration file.", e);
                }
                owner.listeners.fireWriteEvent(configFile, owner.writer);
            }

            // write warnings file into base directory
            Path warningsFile = owner.baseDir.resolve(EccoService.WARNINGS_FILE_NAME);
            if (Files.exists(warningsFile)) {
                throw new EccoException("Warnings file already exists in base directory.");
            } else {
                try {
                    StringBuilder sb = new StringBuilder();
                    List<ModuleRevision> sortedMissing = new ArrayList<>(checkout.getMissing());
                    sortedMissing.sort(ModuleRevisions.RELEVANCE_ORDER);
                    for (ModuleRevision mr : sortedMissing) {
                        sb.append("MISSING: ").append(ModuleRevisions.describe(mr));
                        String location = checkout.getMissingLocations().get(mr);
                        if (location != null && !location.isEmpty()) {
                            sb.append(" (").append(location).append(")");
                        }
                        sb.append(" -- suggested fix: ").append(ModuleRevisions.suggestFix(mr, checkout.getConfiguration()));
                        sb.append(System.lineSeparator());
                    }
                    for (Map.Entry<ModuleRevision, String> mr : checkout.getSurplusModules().entrySet()) {
                        sb.append("SURPLUS: ").append(mr.getKey()).append(" trace id: ")
                                .append(mr.getValue()).append(System.lineSeparator());
                    }
                    for (Node orderNode : checkout.getOrderWarnings()) {
                        sb.append("ORDER: ").append(ArtifactDiagnostics.describePath(orderNode))
                                .append(" (current order: ").append(ArtifactDiagnostics.describeChildren(orderNode)).append(")")
                                .append(" -- suggested fix: ").append(ArtifactDiagnostics.suggestOrderFix(orderNode))
                                .append(System.lineSeparator());
                    }
                    for (Association association : checkout.getUnresolvedAssociations()) {
                        sb.append("UNRESOLVED: ").append(association).append(System.lineSeparator());
                    }
                    for (String constraintWarning : checkout.getConstraintWarnings()) {
                        sb.append("CONSTRAINT: ").append(constraintWarning).append(System.lineSeparator());
                    }
                    for (RejectedTrace rejectedTrace : checkout.getRejectedTraces()) {
                        sb.append("TRACE: ").append(rejectedTrace.describe()).append(System.lineSeparator());
                    }
                    Files.write(warningsFile, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    throw new EccoException("Could not create warnings file.", e);
                }
                owner.listeners.fireWriteEvent(warningsFile, owner.writer);
            }

            return checkout;
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }
    Set<Node> selectArtifacts(Checkout checkout) {
        for (Association selectedAssociation : checkout.getSelectedAssociations()) {
            owner.listeners.fireAssociationSelectedEvent(selectedAssociation);
        }
        // nodes (artifacts) to write to files
        return new HashSet<>(checkout.getNode().getChildren());
    }
    Checkout checkout(Node node) {
        owner.checkInitialized();

        Checkout checkout = new Checkout();
        checkout.setNode(node);

        Set<Node> nodes = new HashSet<>(node.getChildren());
        owner.writer.write(owner.baseDir, nodes);

        return checkout;
    }
    /**
     * Locates the nearest file-level ancestor (inclusive) of {@code node} -- walking up via {@code
     * Node#getParent()} until reaching a node whose artifact data is {@link PluginArtifactData}
     * (the granularity {@code ArtifactWriter}s actually write; intermediate ancestors may be plain
     * {@code DirectoryArtifactData} folder nodes, or structural nodes nested inside a file, e.g. a
     * method body) -- and re-writes just that one file to disk under the current base directory. See
     * {@link DispatchWriter#writeFile(Path, Node)}. Used by the GUI's ORDER-warning reorder dialog to
     * materialize a user-chosen child order (already applied via {@code Node.Op#setChildren}
     * somewhere in this node's subtree) before committing it.
     *
     * @param node Any node from the currently-shown checkout's tree, e.g. the ambiguous ORDER-warning
     *             node itself.
     * @return The path(s) written.
     */
    Path[] writeCheckoutFile(Node node) {
        owner.checkInitialized();
        Node fileNode = node;
        while (fileNode != null && !(fileNode.getArtifact().getData() instanceof PluginArtifactData)) {
            fileNode = fileNode.getParent();
        }
        if (fileNode == null) {
            throw new EccoException("Could not locate an enclosing file node (PluginArtifactData) for the given node.");
        }
        return owner.writer.writeFile(owner.baseDir, fileNode);
    }
}
