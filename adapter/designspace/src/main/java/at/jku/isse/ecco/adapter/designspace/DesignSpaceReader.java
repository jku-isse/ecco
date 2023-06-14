package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.designspace.artifact.OperationArtifact;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.util.*;

public class DesignSpaceReader implements ArtifactReader<Workspace, Set<Node.Op>> {
    private final EntityFactory entityFactory;
    private final List<ReadListener> listeners = new ArrayList<>();

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
        List<Operation> operationList = new ArrayList<>(base.operationsProcessed.values());
        List<Node.Op> childNodes = new ArrayList<>();

        childNodes.add(rootNode);
        childNodes.add(rootNode);

        for (Operation operation : operationList) {
            Node.Op root = childNodes.remove(0);
            Node.Op node = entityFactory.createNode(new OperationArtifact(operation));

            root.addChild(node);

            childNodes.add(node);
            childNodes.add(node);
        }

        listeners.forEach(listener -> listener.fileReadEvent(null, this));
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
        listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        listeners.remove(listener);
    }
}
