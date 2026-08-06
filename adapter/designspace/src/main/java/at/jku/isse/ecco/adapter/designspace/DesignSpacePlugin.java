package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class DesignSpacePlugin extends ArtifactPlugin {
    private static final String DESCRIPTION = "Adds support for the DesignSpace model";

    private final Module module = new DesignSpaceModule();

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
