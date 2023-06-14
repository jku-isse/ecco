package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.ElementCreate;
import at.jku.isse.designspace.sdk.core.operations.ElementDelete;
import at.jku.isse.designspace.sdk.core.operations.ElementUpdate;
import at.jku.isse.designspace.sdk.core.operations.Operation;
import at.jku.isse.ecco.adapter.designspace.artifact.OperationArtifact;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DesignSpaceReaderTest {
    @Test
    public void multipleWorkspacesAreNotSupported() {
        DesignSpaceReader reader = new DesignSpaceReader(null);
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(new Workspace[2]));
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(mock(Workspace.class), new Workspace[1]));
    }

    @Test
    public void noWorkspaceGivenRaisesException() {
        DesignSpaceReader reader = new DesignSpaceReader(null);
        assertThrows(NoWorkspaceException.class, () -> reader.read(null));
        assertThrows(NoWorkspaceException.class, () -> reader.read(null, null));
        assertThrows(NoWorkspaceException.class, () -> reader.read(new Workspace[0]));
    }

    @Test
    public void fillsTreeLayerByLayer() {
        Operation[] operations = initTestOperations();
        Workspace workspace = initWorkspaceWithOperations(operations);
        List<Node.Op> operationTree = new ArrayList<>(readWorkspace(workspace));

        assertEquals(1, operationTree.size());
        assertInstanceOf(RootNode.class, operationTree.get(0));
        assertTreeOperations(operations, operationTree.get(0));
    }

    @Test
    public void callsAddedListeners() {
        Operation[] operations = initTestOperations();
        Workspace workspace = initWorkspaceWithOperations(operations);
        DesignSpaceReader reader = new DesignSpaceReader(new MemEntityFactory());
        List<ReadListener> listeners = new ArrayList<>() {{
           add(mock(ReadListener.class));
        }};

        for (ReadListener listener : listeners) {
            reader.addListener(listener);
        }

        reader.read(workspace, null);

        for (ReadListener listener : listeners) {
            verify(listener).fileReadEvent(null, reader);
        }
    }

    @Test
    public void doesNotCallRemovedListeners() {
        Operation[] operations = initTestOperations();
        Workspace workspace = initWorkspaceWithOperations(operations);
        DesignSpaceReader reader = new DesignSpaceReader(new MemEntityFactory());
        List<ReadListener> listeners = new ArrayList<>() {{
            add(mock(ReadListener.class));
        }};

        for (int i = 0; i < listeners.size(); i++) {
            ReadListener listener = listeners.get(i);
            reader.addListener(listener);

            if (i % 2 == 0) {
                reader.removeListener(listener);
            }
        }

        reader.read(workspace, null);

        for (int i = 0; i < listeners.size(); i++) {
            ReadListener listener = listeners.get(i);


            if (i % 2 == 0) {
                verify(listener, never()).fileReadEvent(null, reader);
            } else {
                verify(listener).fileReadEvent(null, reader);
            }
        }
    }

    private void assertTreeOperations(Operation[] operations, Node.Op currentNode) {
        List<Node.Op> children = new ArrayList<>(currentNode.getChildren());
        int i = 0;

        assertEquals(2, children.size());

        while (children.size() > 0) {
            List<Node.Op> newChildren = new ArrayList<>(2*((i+1)*(i+1)));

            while(children.size() > 0) {
                Node.Op node = children.remove(0);

                assertNodeOperationDataEquals(operations[i++], node);

                newChildren.addAll(node.getChildren());
            }

            children.addAll(newChildren);
        }

        assertEquals(i, operations.length);
    }

    private void assertNodeOperationDataEquals(Operation expected, Node.Op node) {
        assertNotNull(node.getArtifact());
        assertNotNull(node.getArtifact().getData());
        assertInstanceOf(OperationArtifact.class, node.getArtifact().getData());

        OperationArtifact leftNodeArtifact = (OperationArtifact) node.getArtifact().getData();

        assertNotNull(leftNodeArtifact.getOperation());
        assertEquals(expected, leftNodeArtifact.getOperation());
    }

    private Set<Node.Op> readWorkspace(Workspace workspace) {
        return new DesignSpaceReader(new MemEntityFactory()).read(workspace, null);
    }

    private Workspace initWorkspaceWithOperations(Operation[] operations) {
        Workspace workspace = mock(Workspace.class);

        workspace.operationsProcessed = new HashMap<>(){{
            for (int i = 0; i < operations.length; i++) {
                put((long)i, operations[i]);
            }
        }};
        return workspace;
    }

    private Operation[] initTestOperations() {
        Operation[] operations = new Operation[100];

        for (int i = 0; i < operations.length; i++) {
            if (i % 3 == 0) {
                operations[i] = mock(ElementCreate.class);
            } else if (i % 3 == 1) {
                operations[i] = mock(ElementUpdate.class);
            } else {
                operations[i] = mock(ElementDelete.class);
            }
        }

        return operations;
    }
}
