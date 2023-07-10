package at.jku.isse.ecco.adapter.designspace.designspace;

import at.jku.isse.designspace.sdk.core.DesignSpace;
import at.jku.isse.designspace.sdk.core.model.Workspace;

import java.util.Collection;

public class WorkspaceFacade implements WorkspaceRepository {

    @Override
    public Collection<Workspace> findAll() {
        return DesignSpace.allWorkspaces();
    }
}
