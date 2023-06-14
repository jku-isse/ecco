package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.designspace.artifact.EmptyNodeArtifact;
import at.jku.isse.ecco.adapter.designspace.artifact.OperationArtifact;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DesignSpaceReader implements ArtifactReader<Workspace, Set<Node.Op>> {
    private final EntityFactory entityFactory;

    @Inject
    public DesignSpaceReader(EntityFactory entityFactory) {
        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return new DesignSpacePlugin().getPluginId();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        // Since this reader does not interact with files, there are no patterns to look for
        return Map.of();
    }

    @Override
    public Set<Node.Op> read(Workspace base, Workspace[] input) {
        if (input != null && input.length > 0) {
            throw new MultipleWorkspaceException();
        }

        if (base == null) {
            throw new NoWorkspaceException();
        }

        Node.Op rootNode = entityFactory.createRootNode();
        Node.Op currentElementCreationNode = rootNode;
        Node.Op currentElementDeletionNode = rootNode;
        Node.Op currentElementUpdateNode = null;
        Node.Op currentPropertyCreateNode = rootNode;
        Node.Op currentPropertyDeleteNode = rootNode;
        Node.Op currentPropertyUpdateNode = null;
        Node.Op currentPropertyUpdateAddNode = rootNode;
        Node.Op currentPropertyUpdateSetNode = rootNode;
        Node.Op currentPropertyUpdateRemoveNode = rootNode;
        Collection<Operation> operations = base.operationsProcessed.values();

        for (Operation operation :operations) {
            OperationArtifact operationArtifact = new OperationArtifact(operation);
            Node.Op node = entityFactory.createNode(operationArtifact);

            if(operation instanceof ElementCreate) {
                currentElementCreationNode.addChild(node);
                currentElementCreationNode = node;
            } else if (operation instanceof ElementDelete) {
                currentElementDeletionNode.addChild(node);
                currentElementDeletionNode = node;
            } else if (operation instanceof ElementUpdate) {
                if (currentElementUpdateNode == null) {
                    currentElementUpdateNode = entityFactory.createNode(new EmptyNodeArtifact());
                    rootNode.addChild(currentElementUpdateNode);
                }

                if (operation instanceof PropertyCreate) {
                    currentElementUpdateNode.addChild(node);
                    currentElementUpdateNode = node;
                }
            }
        }

        return Set.of(rootNode);
    }

    @Override
    public Set<Node.Op> read(Workspace[] input) {
        if (input != null && input.length > 1) {
            throw new MultipleWorkspaceException();
        }

        if (input == null || input.length == 0) {
            throw new NoWorkspaceException();
        }

        return read(input[0], null);
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
