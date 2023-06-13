package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DesignSpaceReaderTest {
    @Test
    public void multipleWorkspacesAreNotSupported() {
        DesignSpaceReader reader = new DesignSpaceReader();
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(new Workspace[2]));
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(mock(Workspace.class), new Workspace[1]));
    }

    @Test
    public void noWorkspaceGivenRaisesException() {
        DesignSpaceReader reader = new DesignSpaceReader();
        assertThrows(NoWorkspaceException.class, () -> reader.read(null));
        assertThrows(NoWorkspaceException.class, () -> reader.read(null, null));
        assertThrows(NoWorkspaceException.class, () -> reader.read(new Workspace[0]));
    }

    @Test
    public void buildsATreeOfOperations() {
        Operation elementCreateOperation = mock(ElementCreate.class);
        Operation elementUpdateOperation = mock(ElementUpdate.class);
        Operation elementDeleteOperation = mock(ElementDelete.class);

        Operation propertyCreateOperation = mock(PropertyCreate.class);
        Operation propertyUpdateOperation = mock(PropertyUpdate.class);
        Operation propertyDeleteOperation = mock(PropertyDelete.class);

        Operation propertyUpdateAddOperation = mock(PropertyUpdateAdd.class);
        Operation propertyUpdateSetOperation = mock(PropertyUpdateSet.class);
        Operation propertyUpdateRemoveOperation = mock(PropertyUpdateRemove.class);

        Workspace workspace = mock(Workspace.class);

        workspace.operationsProcessed = new HashMap<>() {{
            long id = 1;

            put(id++, elementCreateOperation);
            put(id++, elementUpdateOperation);
            put(id++, elementDeleteOperation);

            put(id++, propertyCreateOperation);
            put(id++, propertyUpdateOperation);
            put(id++, propertyDeleteOperation);

            put(id++, propertyUpdateAddOperation);
            put(id++, propertyUpdateSetOperation);
            put(id, propertyUpdateRemoveOperation);
        }};

        Set<Node.Op> rootNodeOps = new DesignSpaceReader().read(workspace, null);
        List<Node.Op> rootNodeOpsList = new ArrayList<>(rootNodeOps);

        int index = 0;

        assertEquals(3, rootNodeOpsList.size());

        assertEquals(elementCreateOperation, rootNodeOpsList.get(index++).getArtifact().getData());
        assertEquals(elementUpdateOperation, rootNodeOpsList.get(index++).getArtifact().getData());
        assertEquals(elementDeleteOperation, rootNodeOpsList.get(index++).getArtifact().getData());

        List<? extends Node.Op> elementUpdateNodes = rootNodeOpsList.get(index).getChildren();
        index = 0;

        assertEquals(3, elementUpdateNodes.size());

        assertEquals(propertyCreateOperation, elementUpdateNodes.get(index++).getArtifact().getData());
        assertEquals(propertyUpdateOperation, elementUpdateNodes.get(index++).getArtifact().getData());
        assertEquals(propertyDeleteOperation, elementUpdateNodes.get(index).getArtifact().getData());

        List<? extends Node.Op> propertyUpdateNodes = elementUpdateNodes.get(1).getChildren();
        index = 0;

        assertEquals(propertyUpdateAddOperation, propertyUpdateNodes.get(index++).getArtifact().getData());
        assertEquals(propertyUpdateSetOperation, propertyUpdateNodes.get(index++).getArtifact().getData());
        assertEquals(propertyUpdateRemoveOperation, propertyUpdateNodes.get(index).getArtifact().getData());
    }
}
