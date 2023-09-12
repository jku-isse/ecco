package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.*;
import at.jku.isse.designspace.sdk.core.operations.*;

import java.util.*;

public class EccoWorkspaceListener extends WorkspaceListener {
    @Override
    public void workspaceUpdated(Workspace workspace, List<Operation> operations) {
        super.workspaceUpdated(workspace, operations);
    }

    @Override
    public void elementChanged(Element element, Operation operation) {
        super.elementChanged(element, operation);
    }
}
