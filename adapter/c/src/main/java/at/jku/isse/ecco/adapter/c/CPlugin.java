package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class CPlugin extends ArtifactPlugin {

    private CModule module = new CModule();

    @Override
    public String getPluginId() {
        return CPlugin.class.getName();
    }

    @Override
    public Module getModule() {
        return this.module;
    }

    @Override
    public String getName() {
        return "CArtifactPlugin";
    }

    @Override
    public String getDescription() {
        return "C Artifact Plugin";
    }
}
