package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.ElementCreate;
import at.jku.isse.designspace.sdk.core.operations.ElementDelete;
import at.jku.isse.designspace.sdk.core.operations.ElementUpdate;
import at.jku.isse.designspace.sdk.core.operations.Operation;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

public class OperationsWriterTest {
    @Test
    public void createsSetFromNodes() {
        Operation[] operations = new Operations().initTestOperations();
        Workspace workspace = new Workspaces().initTestWorkspaceWithOperations(operations);
        Set<Node> operationNodes =
                new WorkspaceReader(new MemEntityFactory())
                        .read(workspace, null)
                        .stream()
                        .map(Node::getNode)
                        .collect(Collectors.toSet());
        HashMap<Long, Operation>[] operationMaps = new OperationsWriter().write(null, operationNodes);

        assertEquals(1, operationMaps.length);

        HashMap<Long, Operation> operationMap = operationMaps[0];

        for (int i = 0; i < operations.length; i++) {
            Operation operation = operationMap.get((long)i);

            assertEquals(i, operation.id);
            if (i % 3 == 0) {
                assertInstanceOf(ElementCreate.class, operation);
            } else if (i % 3 == 1) {
                assertInstanceOf(ElementUpdate.class, operation);
            } else {
                assertInstanceOf(ElementDelete.class, operation);
            }
        }
    }

    @Test
    public void callsListeners() {
        Operation[] operations = new Operations().initTestOperations();
        Workspace workspace = new Workspaces().initTestWorkspaceWithOperations(operations);
        Set<Node> operationNodes =
                new WorkspaceReader(new MemEntityFactory())
                        .read(workspace, null)
                        .stream()
                        .map(Node::getNode)
                        .collect(Collectors.toSet());
        OperationsWriter writer = new OperationsWriter();
        List<WriteListener> listeners = new ArrayList<>(){{
            for (int i = 0; i < 100; i++) {
                add(mock(WriteListener.class));
                writer.addListener(get(i));
            }
        }};

        writer.write(null, operationNodes);

        for (WriteListener listener : listeners) {
            verify(listener).fileWriteEvent(null, writer);
        }
    }

    @Test
    public void doesNotCallRemovedListeners() {
        Operation[] operations = new Operations().initTestOperations();
        Workspace workspace = new Workspaces().initTestWorkspaceWithOperations(operations);
        Set<Node> operationNodes =
                new WorkspaceReader(new MemEntityFactory())
                        .read(workspace, null)
                        .stream()
                        .map(Node::getNode)
                        .collect(Collectors.toSet());
        OperationsWriter writer = new OperationsWriter();
        List<WriteListener> listeners = new ArrayList<>(){{
            for (int i = 0; i < 100; i++) {
                add(mock(WriteListener.class));
                writer.addListener(get(i));

                if (i % 2 == 0) {
                    writer.removeListener(get(i));
                }
            }
        }};

        writer.write(null, operationNodes);

        for (int i = 0; i < listeners.size(); i++) {
            WriteListener listener = listeners.get(i);
            if (i % 2 == 0) {
                verify(listener, never()).fileWriteEvent(null, writer);
            } else {
                verify(listener).fileWriteEvent(null, writer);
            }
        }
    }
}
