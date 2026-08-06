package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.Operation;

import java.util.HashMap;

import static org.mockito.Mockito.mock;

public class Workspaces {
    public Workspace initTestWorkspaceWithOperations(Operation[] operations) {
        Workspace workspace = mock(Workspace.class);

        workspace.operationsProcessed = new HashMap<>(){{
            for (int i = 0; i < operations.length; i++) {
                put((long)i, operations[i]);
            }
        }};
        
        return workspace;
    }
}
