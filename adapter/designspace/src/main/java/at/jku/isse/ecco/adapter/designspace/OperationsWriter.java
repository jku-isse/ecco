package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.operations.Operation;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.designspace.artifact.OperationArtifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.*;

public class OperationsWriter implements ArtifactWriter<Set<Node>, HashMap<Long, Operation>> {
    private final List<WriteListener> listeners = new LinkedList<>();

    @Override
    public String getPluginId() {
        return DesignSpacePlugin.class.getName();
    }

    @Override
    public HashMap<Long, Operation>[] write(HashMap<Long, Operation> base, Set<Node> input) {
        HashMap<Long, Operation> operations = new HashMap<>();
        List<Node> nodes = new ArrayList<>(input);

        while (nodes.size() > 0){
            Node node = nodes.remove(0);

            nodes.addAll(node.getChildren());

            if (node instanceof RootNode) {
                // Root node does not contain any data so it must be skipped
                continue;
            }

            if (node.getArtifact() == null) {
                // Skip nodes with invalid artifact
                continue;
            }

            if (node.getArtifact().getData() == null || !(node.getArtifact().getData() instanceof OperationArtifact)) {
                // Skip nodes with invalid data
                continue;
            }

            OperationArtifact artifact = (OperationArtifact) node.getArtifact().getData();
            Operation operation = artifact.getOperation();

            if (operation == null) {
                // Skip nodes with invalid payload
                continue;
            }

            operations.put(operation.id, operation);
        }

        listeners.forEach(listener -> listener.fileWriteEvent(null, this));

        // Linter may complain that a HashMap array without specific types is returned
        // but in Java it's not possible to create arrays with type arguments so the linter just has to live with that
        return new HashMap[]{operations};
    }

    @Override
    public HashMap<Long, Operation>[] write(Set<Node> input) {
        return write(null, input);
    }

    @Override
    public void addListener(WriteListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        listeners.remove(listener);
    }
}
