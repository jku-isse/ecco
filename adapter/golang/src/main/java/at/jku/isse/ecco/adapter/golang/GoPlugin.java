package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class GoPlugin extends ArtifactPlugin {
    public static final String DESCRIPTION = "Adds support for Golang source files";

    private final GoModule module = new GoModule();

    @Override
    public String getPluginId() {
        return GoPlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return GoPlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
}
