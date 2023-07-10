package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.DesignSpace;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.designspace.designspace.WorkspaceFacade;
import at.jku.isse.ecco.adapter.designspace.designspace.WorkspaceRepository;
import at.jku.isse.ecco.dao.EntityFactory;
import com.google.inject.Inject;
import com.google.inject.Module;

public class DesignSpacePlugin extends ArtifactPlugin {
    private static final String DESCRIPTION = "Adds support for the DesignSpace model";

    private final DesignSpaceModule module = new DesignSpaceModule();
    private final WorkspaceRepository workspaceRepository;

    public DesignSpacePlugin(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public DesignSpacePlugin() {
        this.workspaceRepository = new WorkspaceFacade();
    }

    @Override
    public void init() {
        workspaceRepository
                .findAll()
                .forEach(workspace -> {
                    workspace.addListener(new EccoWorkspaceListener());
                });
    }

    @Override
    public String getPluginId() {
        return DesignSpacePlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return DesignSpacePlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
}
