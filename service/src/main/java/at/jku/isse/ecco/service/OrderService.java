package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Records the order of an artifact's children in the repository without a commit (Known gap #23):
 * only the parent artifact's partial order graph changes, so no configuration is recorded as a variant
 * and no presence condition changes. A commit would record the whole checkout as a variant instead,
 * and committing only part of it would drop the rest from that configuration.
 */
class OrderService {

    private final EccoService owner;

    OrderService(EccoService owner) {
        this.owner = owner;
    }

    /** See {@link EccoService#recordOrder}. */
    int recordOrder(Node parent, List<? extends Node> orderedChildren) {
        checkNotNull(parent);
        checkNotNull(orderedChildren);
        Map<Node, List<? extends Node>> orders = new LinkedHashMap<>();
        orders.put(parent, orderedChildren);
        return this.recordOrders(orders);
    }

    /**
     * Records every order in one transaction; checks all of them first, so a refused one leaves the
     * repository as it was.
     *
     * @return how many precedences were added
     */
    int recordOrders(Map<Node, List<? extends Node>> orders) {
        owner.checkInitialized();
        Map<Artifact.Op<?>, List<Artifact<?>>> byArtifact = new LinkedHashMap<>();
        for (Map.Entry<Node, List<? extends Node>> order : orders.entrySet()) {
            if (!(order.getKey().getArtifact() instanceof Artifact.Op<?> artifact) || !artifact.isOrdered() || artifact.getPartialOrderGraph() == null)
                throw new EccoException("This artifact has no order to record: " + order.getKey().getArtifact());
            List<Artifact<?>> children = new ArrayList<>();
            for (Node child : order.getValue())
                children.add(child.getArtifact());
            byArtifact.put(artifact, children);
        }
        if (byArtifact.isEmpty())
            return 0;

        int[] added = {0};
        owner.writeTransaction("Error recording the order.", repository -> {
            Map<Artifact.Op<?>, List<Association.Op>> containing = new HashMap<>();
            for (Artifact.Op<?> artifact : byArtifact.keySet()) {
                List<Association.Op> associations = new ArrayList<>();
                for (Association.Op association : repository.getAssociations())
                    if (contains(association.getRootNode(), artifact))
                        associations.add(association);
                if (associations.isEmpty())
                    throw new EccoException("The artifact is not part of this repository: " + artifact);
                containing.put(artifact, associations);
                artifact.getPartialOrderGraph().checkOrder(byArtifact.get(artifact));
            }
            for (Map.Entry<Artifact.Op<?>, List<Artifact<?>>> order : byArtifact.entrySet()) {
                added[0] += order.getKey().getPartialOrderGraph().addOrder(order.getValue());
                // the artifact (with its graph) is written with the associations whose trees hold it
                for (Association.Op association : containing.get(order.getKey()))
                    repository.addAssociation(association);
            }
            repository.invalidateMainTree();
            return repository;
        });
        return added[0];
    }

    /**
     * Records the order of the given files as they are now in the working directory, which holds a
     * checkout (its {@code .config} names the configuration): the configuration is composed again,
     * each file is read with its adapter, and the children of every ordered node are matched to the
     * composed ones by content. Only reordering is accepted - a file with content added or removed
     * is refused.
     *
     * @param files paths relative to the working directory
     * @return how many precedences were added; 0 if the repository already had the files' order
     */
    int recordOrderOfFiles(List<Path> files) {
        owner.checkInitialized();
        checkNotNull(files);
        String configurationString = owner.getConfigStringFromFile(owner.baseDir);
        if (configurationString.isEmpty())
            throw new EccoException("The working directory holds no checkout: " + owner.baseDir.resolve(EccoService.CONFIG_FILE_NAME) + " is missing or empty.");
        Configuration configuration = owner.parseConfigurationString(configurationString);
        Checkout checkout = owner.compose(configuration);

        Map<Node, List<? extends Node>> orders = new LinkedHashMap<>();
        for (Path file : files) {
            Node composed = findFile(checkout.getNode(), file);
            if (composed == null)
                throw new EccoException(file + " is not part of the checkout of " + configurationString + ".");
            Node read = null;
            for (Node root : owner.reader.readSpecificFiles(owner.baseDir, new Path[]{file})) {
                read = findFile(root, file);
                if (read != null) break;
            }
            if (read == null)
                throw new EccoException("Could not read " + file + ".");
            matchOrders(file, composed, read, orders);
        }
        return this.recordOrders(orders);
    }

    /**
     * Pairs {@code read}'s children with {@code composed}'s by equal artifacts, in {@code read}'s
     * order, and collects that order for every ordered node, recursively.
     */
    private static void matchOrders(Path file, Node composed, Node read, Map<Node, List<? extends Node>> orders) {
        List<? extends Node> composedChildren = composed.getChildren();
        Set<Node> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Node> ordered = new ArrayList<>();
        List<Node[]> pairs = new ArrayList<>();
        for (Node readChild : read.getChildren()) {
            Node match = null;
            for (Node composedChild : composedChildren)
                if (!used.contains(composedChild) && composedChild.getArtifact() != null && composedChild.getArtifact().equals(readChild.getArtifact())) {
                    match = composedChild;
                    break;
                }
            if (match == null)
                throw new EccoException(file + ": " + readChild.getArtifact() + " is not in the checkout - order only records a new order, it does not add or change content.");
            used.add(match);
            ordered.add(match);
            pairs.add(new Node[]{match, readChild});
        }
        for (Node composedChild : composedChildren)
            if (!used.contains(composedChild))
                throw new EccoException(file + ": " + composedChild.getArtifact() + " is missing - order only records a new order, it does not remove content.");

        Artifact<?> artifact = composed.getArtifact();
        if (artifact != null && artifact.isOrdered() && artifact.getPartialOrderGraph() != null && ordered.size() > 1)
            orders.put(composed, ordered);
        for (Node[] pair : pairs)
            matchOrders(file, pair[0], pair[1], orders);
    }

    /** The node of {@code file} (a path relative to the working directory) in the tree below {@code node}. */
    private static Node findFile(Node node, Path file) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data && file.normalize().equals(data.getPath().normalize()))
            return node;
        for (Node child : node.getChildren()) {
            Node found = findFile(child, file);
            if (found != null)
                return found;
        }
        return null;
    }

    private static boolean contains(Node node, Artifact<?> artifact) {
        if (node.getArtifact() == artifact)
            return true;
        for (Node child : node.getChildren())
            if (contains(child, artifact))
                return true;
        return false;
    }
}
