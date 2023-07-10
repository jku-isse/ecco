package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.*;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.service.EccoService;

import java.util.*;

public class EccoWorkspaceListener extends WorkspaceListener {
    @Override
    public void workspaceUpdated(Workspace workspace, List<Operation> operations) {
        super.workspaceUpdated(workspace, operations);

        System.out.println(workspace.toString());

        for (Operation operation: operations) {
            System.out.println(operation);
        }
    }

    @Override
    public void elementChanged(Element element, Operation operation) {
        super.elementChanged(element, operation);

        System.out.println(element.toString());
        System.out.println(operation);
    }
}
