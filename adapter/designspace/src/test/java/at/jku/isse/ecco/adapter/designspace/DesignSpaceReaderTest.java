package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.adapter.designspace.artifact.OperationArtifact;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

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
    public void wrapsElementCreateOperationsInArtefacts() {
        Operation elementCreateOperation1 = mock(ElementCreate.class);
        Operation elementCreateOperation2 = mock(ElementCreate.class);
        Operation elementCreateOperation3 = mock(ElementCreate.class);
        Operation[] operations = new Operation[]{elementCreateOperation1, elementCreateOperation2, elementCreateOperation3};
        Workspace workspace = mock(Workspace.class);

        workspace.operationsProcessed = new HashMap<>() {{
            long id = 1;

            put(id++, elementCreateOperation1);
            put(id++, elementCreateOperation2);
            put(id, elementCreateOperation3);
        }};

        Set<Node.Op> rootNodeOps = new DesignSpaceReader(
                new MemEntityFactory()
        ).read(workspace, null);
        List<Node.Op> rootNodeOpsList = new ArrayList<>(rootNodeOps);

        assertEquals(1, rootNodeOpsList.size());
        assertInstanceOf(RootNode.class, rootNodeOpsList.get(0));

        List<? extends Node.Op> layer = rootNodeOpsList.get(0).getChildren();

        for (int i = 0; i < 3; i++) {
            assertNotNull(layer);
            assertEquals(1, layer.size());
            assertNotNull(layer.get(0).getArtifact());
            assertNotNull(layer.get(0).getArtifact().getData());
            assertInstanceOf(OperationArtifact.class, layer.get(0).getArtifact().getData());

            OperationArtifact operationArtifact = (OperationArtifact) layer.get(0).getArtifact().getData();

            assertNotNull(operationArtifact.getOperation());
            assertEquals(operations[i], operationArtifact.getOperation());

            layer = layer.get(0).getChildren();
        }
    }

    @Test
    public void buildsATreeOfOperations() {
        Operation elementCreateOperation = mock(ElementCreate.class);
        Operation elementDeleteOperation = mock(ElementDelete.class);

        Operation propertyCreateOperation = mock(PropertyCreate.class);
        Operation propertyDeleteOperation = mock(PropertyDelete.class);

        Operation propertyUpdateAddOperation = mock(PropertyUpdateAdd.class);
        Operation propertyUpdateSetOperation = mock(PropertyUpdateSet.class);
        Operation propertyUpdateRemoveOperation = mock(PropertyUpdateRemove.class);

        Workspace workspace = mock(Workspace.class);

        workspace.operationsProcessed = new HashMap<>() {{
            long id = 1;

            put(id++, elementCreateOperation);
            put(id++, elementDeleteOperation);

            put(id++, propertyCreateOperation);
            put(id++, propertyDeleteOperation);

            put(id++, propertyUpdateAddOperation);
            put(id++, propertyUpdateSetOperation);
            put(id, propertyUpdateRemoveOperation);
        }};

        Set<Node.Op> rootNodeOps = new DesignSpaceReader(
                new MemEntityFactory()
        ).read(workspace, null);
        List<Node.Op> rootNodeOpsList = new ArrayList<>(rootNodeOps);

        assertEquals(3, rootNodeOpsList.size());
    }
}
