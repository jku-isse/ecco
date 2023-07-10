package at.jku.isse.ecco.adapter.designspace.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;

import java.util.Collection;

public interface WorkspaceRepository {
    Collection<Workspace> findAll();
}
